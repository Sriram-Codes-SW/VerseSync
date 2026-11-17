package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Songs, folders and setlists.
 *
 * ### Changing the schema
 * Bumping [version] without a matching entry in [MIGRATIONS] makes Room throw an
 * `IllegalStateException` when the database is opened, which is a crash at launch for every
 * existing user. That is deliberate: the alternative was silently deleting their library.
 *
 * When changing an entity:
 * 1. Raise `version` and build once, which writes the new schema file to `app/schemas`.
 * 2. Add a `Migration(old, new)` to `Migrations.kt` and list it in [MIGRATIONS], with the SQL copied
 *    from the new schema file.
 * 3. Run `VerseDatabaseMigrationTest` on a device or emulator. It opens a database created from the
 *    previous schema file and fails when the migration is missing or wrong, and
 *    `runMigrationsAndValidate` checks the migrated database against the new schema file.
 */
@Database(
    entities = [Song::class, Folder::class, Setlist::class, SetlistEntry::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VerseDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun setlistDao(): SetlistDao

    companion object {
        const val NAME = "verse_database"

        /** Every migration between released versions. Version 3 was the first release. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)

        @Volatile
        @Suppress("ktlint:standard:property-naming") // conventional name for the singleton holder
        private var INSTANCE: VerseDatabase? = null

        fun getInstance(context: Context): VerseDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            VerseDatabase::class.java,
                            NAME,
                        ).addMigrations(*MIGRATIONS)
                        // Never wipe user songs on upgrade. Only a downgrade, which Room cannot
                        // migrate, may start empty.
                        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
