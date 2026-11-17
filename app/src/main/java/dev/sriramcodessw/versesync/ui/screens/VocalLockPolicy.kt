package dev.sriramcodessw.versesync.ui.screens

/** How many times the vocal lock can be given up on before the device lock is required. */
object VocalLockPolicy {
    const val MAX_ATTEMPTS = 2

    /** True once [failedAttempts] attempts have been given up, and the device lock must take over. */
    fun fallsBackToDeviceLock(failedAttempts: Int): Boolean = failedAttempts >= MAX_ATTEMPTS
}
