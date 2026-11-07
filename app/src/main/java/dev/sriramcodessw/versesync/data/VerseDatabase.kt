package dev.sriramcodessw.versesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Song::class, Folder::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class VerseDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: VerseDatabase? = null

        fun getInstance(context: Context): VerseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VerseDatabase::class.java,
                    "verse_database"
                )
                    // Never wipe user songs on upgrade. Add a Migration when bumping the version.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
