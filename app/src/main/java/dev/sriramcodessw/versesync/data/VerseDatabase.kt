package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * The song library.
 *
 * ### Changing the schema
 * Bumping [version] without a matching entry in [MIGRATIONS] makes Room throw an
 * `IllegalStateException` when the database is opened, which is a crash at launch for every
 * existing user. That is deliberate: the alternative was silently deleting their library.
 *
 * When changing an entity:
 * 1. Raise `version` and build once, which writes the new schema file to `app/schemas`.
 * 2. Add a `Migration(old, new)` to [MIGRATIONS].
 * 3. Run `VerseDatabaseMigrationTest` on a device or emulator. It opens a database created from the
 *    previous schema file and fails when the migration is missing or wrong.
 */
@Database(entities = [Song::class, Folder::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class VerseDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        const val NAME = "verse_database"

        /** Every migration between released versions. Empty while version 3 is the first release. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile
        private var INSTANCE: VerseDatabase? = null

        fun getInstance(context: Context): VerseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VerseDatabase::class.java,
                    NAME
                )
                    .addMigrations(*MIGRATIONS)
                    // Never wipe user songs on upgrade. Only a downgrade, which Room cannot
                    // migrate, may start empty.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
