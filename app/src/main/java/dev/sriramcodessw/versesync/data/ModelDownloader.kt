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

class ModelDownloader(
    private val context: Context,
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = mutableMapOf<Long, String>()
    val downloadStatus = MutableStateFlow<Map<String, Int>>(emptyMap())

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

    @Synchronized
    private fun updateStatus(
        lang: String,
        progress: Int,
    ) {
        val current = downloadStatus.value.toMutableMap()
        current[lang] = progress
        downloadStatus.value = current
    }

    val onDownloadComplete =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                // Unpacking a model can take seconds. Doing it on the main thread risks an ANR,
                // so keep the broadcast alive and finish it from a worker thread.
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
                unpackModel(lang)
            } else if (status == DownloadManager.STATUS_FAILED) {
                updateStatus(lang, -1)
            }
        }
    }

    private fun unpackModel(languageCode: String) {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val zipFile = File(externalDir, "model-$languageCode.zip")
        val targetDir = File(context.filesDir, "models/model-$languageCode")

        try {
            installModelArchive(zipFile, targetDir)
            zipFile.delete()
            updateStatus(languageCode, 100)
            Log.d("ModelDownloader", "Successfully unpacked and flattened $languageCode")
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Failed to unpack $languageCode", e)
            // A model that was already installed is left as it was.
            zipFile.delete()
            updateStatus(languageCode, -1)
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
