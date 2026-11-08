package dev.sriramcodessw.versesync.bluetooth

import java.util.UUID

object BluetoothConstants {
    const val SERVICE_NAME = "VerseSync"

    // The first UUID in this list is ALWAYS the one we try to use first.
    // When you release a new version, add the NEW UUID to the TOP of this list.
    // DO NOT remove the old ones immediately.

    val LATEST_UUID: UUID = UUID.fromString("f5b5f8de-5347-4933-8260-255e51c8851c") // New in v2.0
    val LEGACY_UUID_V1: UUID = UUID.fromString("8e35a790-2653-47cb-8c99-e685a73a62f0") // Original in v1.0

    val SUPPORTED_UUIDS: List<UUID> =
        listOf(
            LATEST_UUID,
            LEGACY_UUID_V1,
            // Add future UUIDs here, at the top.
        )
}
