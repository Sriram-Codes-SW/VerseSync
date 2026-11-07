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

    private fun updateStatus(lang: String, progress: Int) {
        val current = downloadStatus.value.toMutableMap()
        current[lang] = progress
        downloadStatus.value = current
    }

    val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val descIndex = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
                if (statusIndex != -1 && descIndex != -1) {
                    val status = cursor.getInt(statusIndex)
                    val description = cursor.getString(descIndex)
                    if (description?.startsWith("VerseSyncModel:") == true) {
                        val lang = description.substringAfter("VerseSyncModel:")
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            unpackModel(lang)
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            updateStatus(lang, -1)
                        }
                    }
                }
                cursor.close()
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
                        val newFile = File(targetDir, relativePath)
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
            updateStatus(languageCode, -1)
        }
    }
}
