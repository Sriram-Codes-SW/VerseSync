package dev.sriramcodessw.versesync.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.sriramcodessw.versesync.R

class SoundManager(
    context: Context,
) {
    private val soundPool: SoundPool
    private var tapSoundId: Int = 0

    init {
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

        soundPool =
            SoundPool
                .Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()

        tapSoundId = soundPool.load(context, R.raw.tap_sound, 1)
    }

    fun playTapSound() {
        soundPool.play(tapSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }
}
