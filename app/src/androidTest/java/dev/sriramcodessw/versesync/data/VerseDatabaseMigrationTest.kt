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
 * It creates a database from the exported schema of an earlier version, puts a song in it, and
 * opens it with the database class as it is now. If the schema changed and no migration was added,
 * opening fails and so does this test, instead of the app crashing for users after an update.
 * See [VerseDatabase] for how to add a migration.
 */
@RunWith(AndroidJUnit4::class)
class VerseDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VerseDatabase::class.java
    )

    private val databaseName = "migration-test"

    @Test
    fun songsSurviveOpeningTheDatabaseOfTheFirstReleasedVersion() = runBlocking {
        val firstReleasedVersion = 3

        helper.createDatabase(databaseName, firstReleasedVersion).use { db ->
            db.execSQL(
                "INSERT INTO songs (id, title, artist, lyrics, language, folderId, dateAdded, lrcData, fingerprint) " +
                    "VALUES (1, 'Kept', 'Artist', 'Words', 'en-us', NULL, 1, NULL, NULL)"
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, VerseDatabase::class.java, databaseName)
            .addMigrations(*VerseDatabase.MIGRATIONS)
            .build()
        try {
            val songs = database.songDao().getAllSongs().first()
            assertEquals(listOf("Kept"), songs.map { it.title })
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
