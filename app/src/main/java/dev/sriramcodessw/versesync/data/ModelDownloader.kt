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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Resolves [relativePath] under [targetDir] and returns null if the result would land
 * outside it (a "zip slip" entry such as `../../x`). Entries from a downloaded archive
 * are untrusted, so every extraction path must go through this.
 */
internal fun resolveInside(targetDir: File, relativePath: String): File? {
    val root = targetDir.canonicalFile
    val dest = File(root, relativePath).canonicalFile
    return if (dest.path == root.path || dest.path.startsWith(root.path + File.separator)) dest else null
}

class ModelDownloader(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val activeDownloads = mutableMapOf<Long, String>()
    val downloadStatus = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun downloadModel(languageCode: String, url: String) {
        try {
            val request = DownloadManager.Request(url.toUri())
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
    private fun updateStatus(lang: String, progress: Int) {
        val current = downloadStatus.value.toMutableMap()
        current[lang] = progress
        downloadStatus.value = current
    }

    val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
        if (!targetDir.exists()) targetDir.mkdirs()

        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                // Most Vosk zips have a single root folder. We want to skip it to "flatten" the structure.
                var rootFolderName: String? = null

                while (entry != null) {
                    val name = entry.name
                    if (rootFolderName == null && entry.isDirectory) {
                        rootFolderName = name
                    }

                    val relativePath = if (rootFolderName != null && name.startsWith(rootFolderName)) {
                        name.substring(rootFolderName.length)
                    } else name

                    if (relativePath.isNotEmpty()) {
                        val newFile = resolveInside(targetDir, relativePath)
                            ?: throw SecurityException("Blocked archive entry outside model directory: $name")
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            updateStatus(languageCode, 100)
            Log.d("ModelDownloader", "Successfully unpacked and flattened $languageCode")
        } catch (e: Exception) {
            Log.e("ModelDownloader", "Failed to unpack $languageCode", e)
            // Do not leave a half-extracted model that would later fail to load.
            targetDir.deleteRecursively()
            zipFile.delete()
            updateStatus(languageCode, -1)
        }
    }
}
