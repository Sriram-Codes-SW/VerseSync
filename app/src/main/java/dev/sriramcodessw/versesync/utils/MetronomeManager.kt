package dev.sriramcodessw.versesync.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.sriramcodessw.versesync.R
import kotlinx.coroutines.*

class MetronomeManager(context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Using the same tap sound for the metronome tick
    private val tickSoundId: Int = soundPool.load(context, dev.sriramcodessw.versesync.R.raw.tap_sound, 1)
    
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start(bpm: Int) {
        stop()
        val intervalMs = (60_000 / bpm).toLong()
        job = scope.launch {
            while (isActive) {
                soundPool.play(tickSoundId, 1f, 1f, 1, 0, 1f)
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun release() {
        stop()
        soundPool.release()
    }
}
