package dev.sriramcodessw.versesync.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Resolves [relativePath] under [targetDir] and returns null if the result would land
 * outside it (a "zip slip" entry such as `../../x`). Entries from a downloaded archive
 * are untrusted, so every extraction path must go through this.
 */
internal fun resolveInside(
    targetDir: File,
    relativePath: String,
): File? {
    val root = targetDir.canonicalFile
    val dest = File(root, relativePath).canonicalFile
    return if (dest.path == root.path || dest.path.startsWith(root.path + File.separator)) dest else null
}

private const val MODEL_DESCRIPTION_PREFIX = "VerseSyncModel:"
private val LANGUAGE_CODE = Regex("[A-Za-z0-9_-]{1,16}")

/**
 * The languages whose downloaded archive still has to be unpacked, from the descriptions of the downloads the download
 * manager reports as finished. A finished download whose archive has been unpacked has no archive left, because unpacking
 * deletes it, so an archive that [archiveExists] is the work that is waiting. A description that is not ours, or whose code
 * is not a plain language code, is ignored. Used when the app starts (issue #36).
 */
internal fun languagesWaitingToUnpack(
    finishedDescriptions: List<String?>,
    archiveExists: (String) -> Boolean,
): List<String> =
    finishedDescriptions
        .mapNotNull { it?.takeIf { d -> d.startsWith(MODEL_DESCRIPTION_PREFIX) }?.substringAfter(MODEL_DESCRIPTION_PREFIX) }
        .filter { LANGUAGE_CODE.matches(it) }
        .distinct()
        .filter(archiveExists)

class ModelDownloader(
    private val context: Context,
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = mutableMapOf<Long, String>()
    val downloadStatus = ModelDownloadStatus.flow

    fun downloadModel(
        languageCode: String,
        url: String,
    ) {
        try {
            val request =
                DownloadManager
                    .Request(url.toUri())
                    .setTitle("Downloading $languageCode Model")
                    .setDescription("VerseSyncModel:$languageCode")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "model-$languageCode.zip")

            val downloadId = downloadManager.enqueue(request)
            activeDownloads[downloadId] = languageCode
            updateStatus(languageCode, 0)
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Failed to enqueue download", e)
            updateStatus(languageCode, -1)
        }
    }

    private fun updateStatus(
        lang: String,
        progress: Int,
    ) = ModelDownloadStatus.update(lang, progress)

    /**
     * Unpacks models whose download finished while the app was not running: the completion broadcast is received only while
     * the app is open, so without this the archive would stay in the downloads folder and the language would never show as
     * installed (issue #36). Asks the download manager for finished downloads of ours and queues the unpack job for each
     * archive that is still there. The job is unique per language and safe to run twice. Call it off the main thread.
     */
    fun resumeFinishedDownloads() {
        try {
            val descriptions = mutableListOf<String?>()
            downloadManager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL))?.use { cursor ->
                val descIndex = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
                if (descIndex == -1) return
                while (cursor.moveToNext()) descriptions += cursor.getString(descIndex)
            }
            val downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            for (lang in languagesWaitingToUnpack(descriptions) { File(downloads, "model-$it.zip").exists() }) {
                Log.d("ModelDownloader", "Resuming the unpacking of $lang, downloaded while the app was closed")
                updateStatus(lang, 0)
                ModelUnpackWorker.enqueue(context, lang)
            }
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Could not look for finished downloads", e)
        }
    }

    val onDownloadComplete =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                // Looking up the download is a quick system call, but not one for the main thread.
                // The slow part, unpacking the model, is handed to WorkManager (see ModelUnpackWorker),
                // which is not cut off like a broadcast receiver is.
                val pending = goAsync()
                Thread {
                    try {
                        handleDownloadComplete(id)
                    } catch (e: Exception) {
                        Log.e("ModelDownloader", "Download completion handling failed", e)
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
        }

    private fun handleDownloadComplete(id: Long) {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id)) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val descIndex = it.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
            if (statusIndex == -1 || descIndex == -1) return
            val status = it.getInt(statusIndex)
            val description = it.getString(descIndex)
            if (description?.startsWith("VerseSyncModel:") != true) return
            val lang = description.substringAfter("VerseSyncModel:")
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                ModelUnpackWorker.enqueue(context, lang)
            } else if (status == DownloadManager.STATUS_FAILED) {
                updateStatus(lang, -1)
            }
        }
    }
}

/**
 * Unpacks a Vosk model archive into [targetDir].
 *
 * The archive is first extracted into a staging folder next to the target, and only replaces the
 * target once every entry has been written. If anything fails, the staging folder is removed and
 * a model that was already in [targetDir] is untouched. Before, extraction went straight into the
 * target, so a failed re-download destroyed a model that worked.
 *
 * @throws SecurityException if an entry would be written outside the model folder
 */
internal fun installModelArchive(
    zipFile: File,
    targetDir: File,
) {
    val parent = targetDir.parentFile ?: throw IOException("Model folder has no parent: $targetDir")
    val stagingDir = File(parent, targetDir.name + ".tmp")
    val backupDir = File(parent, targetDir.name + ".old")

    stagingDir.deleteRecursively()
    backupDir.deleteRecursively()
    if (!stagingDir.mkdirs()) throw IOException("Cannot create $stagingDir")

    try {
        var filesWritten = 0
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            // Most Vosk zips have a single root folder. We want to skip it to "flatten" the structure.
            var rootFolderName: String? = null

            while (entry != null) {
                val name = entry.name
                if (rootFolderName == null && entry.isDirectory) {
                    rootFolderName = name
                }

                val relativePath =
                    if (rootFolderName != null && name.startsWith(rootFolderName)) {
                        name.substring(rootFolderName.length)
                    } else {
                        name
                    }

                if (relativePath.isNotEmpty()) {
                    val newFile =
                        resolveInside(stagingDir, relativePath)
                            ?: throw SecurityException("Blocked archive entry outside model directory: $name")
                    if (entry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        newFile.parentFile?.mkdirs()
                        FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                        filesWritten++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        // ZipInputStream reads a file that is not a zip as an archive with no entries and raises no
        // error. Without this check an empty folder would replace a working model.
        if (filesWritten == 0) throw IOException("The archive contained no files")

        // Swap in the new model, keeping the old one until the swap has worked.
        if (targetDir.exists() && !targetDir.renameTo(backupDir)) {
            throw IOException("Cannot move the existing model out of the way")
        }
        if (!stagingDir.renameTo(targetDir)) {
            backupDir.renameTo(targetDir)
            throw IOException("Cannot move the new model into place")
        }
        backupDir.deleteRecursively()
    } catch (e: Exception) {
        stagingDir.deleteRecursively()
        throw e
    }
}
