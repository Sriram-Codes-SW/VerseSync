package dev.sriramcodessw.versesync.data

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

private const val TAG = "ModelUnpackWorker"

/**
 * Download and install progress per language code, shared by the whole process: 0 while a model is
 * downloading or being unpacked, 100 when it is installed, and -1 when it failed.
 *
 * The unpacking runs in [ModelUnpackWorker], which can outlive the screen and the view model that
 * started the download, so the status cannot belong to a single [ModelDownloader].
 */
object ModelDownloadStatus {
    private val state = MutableStateFlow<Map<String, Int>>(emptyMap())
    val flow = state.asStateFlow()

    fun update(
        language: String,
        progress: Int,
    ) {
        state.update { it + (language to progress) }
    }
}

/** How [unpackDownloadedModel] ended. */
enum class UnpackOutcome {
    /** The archive was unpacked and the model is now installed. */
    INSTALLED,

    /** There was no archive, but the model was already installed, so nothing was left to do. */
    ALREADY_INSTALLED,

    /** The model could not be installed. A model that was already installed is untouched. */
    FAILED,
}

/**
 * Installs the model from the downloaded archive [zipFile] into [targetDir], then deletes the
 * archive. Running it again after it has finished is harmless: with no archive left and the model
 * in place, it reports [UnpackOutcome.ALREADY_INSTALLED]. That matters because WorkManager can run
 * a job twice, for example when the process is killed just before it reports success.
 */
internal fun unpackDownloadedModel(
    zipFile: File,
    targetDir: File,
): UnpackOutcome {
    if (!zipFile.exists()) {
        return if (targetDir.isDirectory) UnpackOutcome.ALREADY_INSTALLED else UnpackOutcome.FAILED
    }
    return try {
        installModelArchive(zipFile, targetDir)
        zipFile.delete()
        UnpackOutcome.INSTALLED
    } catch (e: Exception) {
        Log.e(TAG, "Failed to unpack ${targetDir.name}", e)
        // A model that was already installed is left as it was.
        zipFile.delete()
        UnpackOutcome.FAILED
    }
}

/**
 * Unpacks a downloaded speech model.
 *
 * This used to run on a thread kept alive by a broadcast receiver, which Android can cut off after
 * a short time. A model of about 300 MB, such as Swedish, can take longer than that on a slow
 * device. A WorkManager job survives the app being closed or the process being killed: it is stored
 * and started again.
 */
class ModelUnpackWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val language = inputData.getString(KEY_LANGUAGE) ?: return Result.failure()
        val externalDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val zipFile = File(externalDir, "model-$language.zip")
        val targetDir = File(applicationContext.filesDir, "models/model-$language")

        return when (unpackDownloadedModel(zipFile, targetDir)) {
            UnpackOutcome.INSTALLED, UnpackOutcome.ALREADY_INSTALLED -> {
                // Recorded here as well as by the view model, so the language is installed even if
                // the app was closed while the model was unpacking.
                LanguagePreferences(applicationContext).addLanguage(language)
                ModelDownloadStatus.update(language, 100)
                Log.d(TAG, "Model $language is installed")
                Result.success()
            }

            UnpackOutcome.FAILED -> {
                ModelDownloadStatus.update(language, -1)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_LANGUAGE = "language"

        fun workName(language: String) = "unpack-model-$language"

        /** Starts unpacking [language]. A job already queued or running for it is kept. */
        fun enqueue(
            context: Context,
            language: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<ModelUnpackWorker>()
                    .setInputData(Data.Builder().putString(KEY_LANGUAGE, language).build())
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(workName(language), ExistingWorkPolicy.KEEP, request)
        }
    }
}
