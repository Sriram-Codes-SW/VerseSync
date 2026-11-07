package dev.sriramcodessw.versesync

import android.app.Application
import dev.sriramcodessw.versesync.data.VerseDatabase

class VerseSyncApp : Application() {
    val database: VerseDatabase by lazy { VerseDatabase.getInstance(this) }
}
