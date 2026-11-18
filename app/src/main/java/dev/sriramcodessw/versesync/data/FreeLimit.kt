package dev.sriramcodessw.versesync.data

/**
 * What a free person may add to the library (ADR-20, decision 3). Only songs that are in no setlist count: songs in a
 * setlist are concert songs, and concert use is free for any size of show. Pro has no limit. Nothing is ever removed or
 * hidden because of this; it only decides whether a NEW song can be added from the Songs tab.
 */
object FreeLimit {
    /** How many songs outside every setlist a free person may have. */
    const val MAX_LOOSE_SONGS = 10

    fun canAddLooseSong(
        isPro: Boolean,
        looseSongs: Int,
    ): Boolean = isPro || looseSongs < MAX_LOOSE_SONGS
}
