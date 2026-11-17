package dev.sriramcodessw.versesync.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 3 to 4 adds the setlist tables (ADR-20). Songs, folders and every existing row are left
 * alone.
 *
 * The SQL is copied from the exported schema `app/schemas/.../4.json`, with `${TABLE_NAME}` replaced
 * by the table name, so that Room's check of the migrated database against that schema passes.
 * Change it only together with a new schema version.
 */
val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `setlists` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `setlist_entries` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `setlistId` INTEGER NOT NULL, " +
                    "`songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`setlistId`) REFERENCES `setlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_setlist_entries_setlistId_position` " +
                    "ON `setlist_entries` (`setlistId`, `position`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_setlist_entries_songId` ON `setlist_entries` (`songId`)",
            )
        }
    }
