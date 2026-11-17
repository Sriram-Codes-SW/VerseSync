package dev.sriramcodessw.versesync.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the user's library across app updates.
 *
 * It creates a database from the exported schema of an earlier version, puts rows in it, and opens
 * it with the database class as it is now. If the schema changed and no migration was added,
 * opening fails and so does this test, instead of the app crashing for users after an update.
 * See [VerseDatabase] for how to add a migration.
 */
@RunWith(AndroidJUnit4::class)
class VerseDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            VerseDatabase::class.java,
        )

    private val databaseName = "migration-test"

    private fun createVersion3WithOneSongInAFolder() {
        helper.createDatabase(databaseName, 3).use { db ->
            db.execSQL("INSERT INTO folders (id, name) VALUES (1, 'Kept folder')")
            db.execSQL(
                "INSERT INTO songs (id, title, artist, lyrics, language, folderId, dateAdded, lrcData, fingerprint) " +
                    "VALUES (1, 'Kept', 'Artist', 'Words', 'en-us', 1, 1, NULL, NULL)",
            )
        }
    }

    @Test
    fun songsSurviveOpeningTheDatabaseOfTheFirstReleasedVersion() =
        runBlocking {
            createVersion3WithOneSongInAFolder()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database =
                Room
                    .databaseBuilder(context, VerseDatabase::class.java, databaseName)
                    .addMigrations(*VerseDatabase.MIGRATIONS)
                    .build()
            try {
                val songs = database.songDao().getAllSongs().first()
                assertEquals(listOf("Kept"), songs.map { it.title })
                assertEquals(listOf<Int?>(1), songs.map { it.folderId })
                assertEquals(
                    listOf("Kept folder"),
                    database
                        .songDao()
                        .getAllFolders()
                        .first()
                        .map { it.name },
                )
                assertEquals(emptyList<Setlist>(), database.setlistDao().getSetlists().first())
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }

    @Test
    fun migrationFrom3To4MatchesTheExportedSchemaAndKeepsEveryRow() {
        createVersion3WithOneSongInAFolder()

        // Room compares the migrated database with 4.json and fails on any difference in a table,
        // a foreign key or an index.
        helper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT title, folderId FROM songs").use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals("Kept", it.getString(0))
                assertEquals(1, it.getInt(1))
            }
            db.query("SELECT name FROM folders").use { assertEquals(1, it.count) }
            db.query("SELECT COUNT(*) FROM setlists").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM setlist_entries").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }
}
