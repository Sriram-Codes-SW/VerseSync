package dev.sriramcodessw.versesync.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Setlist storage (ADR-20, TC-U-52): order, repeats, cascades and the replace caution, on a real database. */
@RunWith(AndroidJUnit4::class)
class SetlistDaoInstrumentedTest {
    private lateinit var database: VerseDatabase
    private lateinit var songs: SongDao
    private lateinit var setlists: SetlistDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VerseDatabase::class.java).build()
        songs = database.songDao()
        setlists = database.setlistDao()
    }

    @After
    fun close() = database.close()

    private suspend fun addSong(title: String): Int {
        songs.insert(Song(title = title, artist = "A", lyrics = "words of $title"))
        return songs
            .getAllSongs()
            .first()
            .first { it.title == title }
            .id
    }

    private suspend fun newSetlist(name: String = "Show"): Int = setlists.insertSetlist(Setlist(name = name)).toInt()

    private suspend fun titles(setlistId: Int): List<String> = setlists.getEntriesWithSongs(setlistId).first().map { it.song.title }

    @Test
    fun songsComeBackInTheOrderTheyWereAdded() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val c = addSong("C")
            val list = newSetlist()

            setlists.appendSong(list, b)
            setlists.appendSong(list, c)
            setlists.appendSong(list, a)

            assertEquals(listOf("B", "C", "A"), titles(list))
            assertEquals(listOf(0, 1, 2), setlists.getEntries(list).map { it.position })
        }

    @Test
    fun aSongCanAppearTwiceInOneSetlist() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val list = newSetlist()

            val first = setlists.appendSong(list, a)
            setlists.appendSong(list, b)
            val second = setlists.appendSong(list, a)

            assertEquals(listOf("A", "B", "A"), titles(list))
            assertTrue(first != second)
        }

    @Test
    fun removingAnEntryLeavesTheOthersInOrder() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            val entries = ids.map { setlists.appendSong(list, it) }

            setlists.removeEntry(entries[1])

            assertEquals(listOf("A", "C"), titles(list))
            // A gap is allowed. A song added after it still goes last.
            setlists.appendSong(list, ids[1])
            assertEquals(listOf("A", "C", "B"), titles(list))
        }

    @Test
    fun reorderNumbersEntriesFromZeroInTheGivenOrder() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            val entries = ids.map { setlists.appendSong(list, it) }

            setlists.reorder(list, listOf(entries[2], entries[0], entries[1]))

            assertEquals(listOf("C", "A", "B"), titles(list))
            assertEquals(listOf(0, 1, 2), setlists.getEntries(list).map { it.position })
        }

    @Test
    fun reorderRefusesAListThatIsNotExactlyTheSetlistAndChangesNothing() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            val entries = ids.map { setlists.appendSong(list, it) }
            val other = newSetlist("Other")
            val foreign = setlists.appendSong(other, ids[0])

            val bad =
                listOf(
                    listOf(entries[0], entries[1]),
                    listOf(entries[0], entries[1], entries[1]),
                    listOf(entries[0], entries[1], foreign),
                    listOf(entries[0], entries[1], entries[2], foreign),
                )
            for (order in bad) {
                var refused = false
                try {
                    setlists.reorder(list, order.reversed())
                } catch (_: IllegalArgumentException) {
                    refused = true
                }
                assertTrue("refused $order", refused)
            }

            assertEquals(listOf("A", "B", "C"), titles(list))
        }

    @Test
    fun deletingASongRemovesItsEntriesFromEverySetlist() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val one = newSetlist("One")
            val two = newSetlist("Two")
            setlists.appendSong(one, a)
            setlists.appendSong(one, b)
            setlists.appendSong(one, a)
            setlists.appendSong(two, a)

            songs.delete(songs.getAllSongs().first().first { it.id == a })

            assertEquals(listOf("B"), titles(one))
            assertEquals(emptyList<String>(), titles(two))
            assertEquals(listOf("One", "Two"), setlists.getSetlists().first().map { it.name })
        }

    @Test
    fun deletingASetlistRemovesItsEntriesAndKeepsTheSongs() =
        runBlocking {
            val a = addSong("A")
            val one = newSetlist("One")
            val two = newSetlist("Two")
            setlists.appendSong(one, a)
            setlists.appendSong(two, a)

            setlists.deleteSetlist(one)

            assertEquals(emptyList<SetlistEntry>(), setlists.getEntries(one))
            assertEquals(listOf("A"), titles(two))
            assertEquals(listOf("A"), songs.getAllSongs().first().map { it.title })
        }

    @Test
    fun editingASongKeepsItsPlaceInASetlist() =
        runBlocking {
            val a = addSong("A")
            val list = newSetlist()
            setlists.appendSong(list, a)

            songs.update(
                songs
                    .getAllSongs()
                    .first()
                    .first { it.id == a }
                    .copy(lyrics = "new words"),
            )

            assertEquals(listOf("A"), titles(list))
            assertEquals(
                "new words",
                setlists
                    .getEntriesWithSongs(list)
                    .first()
                    .single()
                    .song.lyrics,
            )
        }

    @Test
    fun insertingASongWithIdZeroNeverReplacesAnExistingOne() =
        runBlocking {
            // SongDao.insert uses REPLACE. Id 0 means a new row, so existing entries must survive it.
            val a = addSong("A")
            val list = newSetlist()
            setlists.appendSong(list, a)

            addSong("Another")
            addSong("Third")

            assertEquals(listOf("A"), titles(list))
        }

    @Test
    fun setlistsAreListedByNameIgnoringCase() =
        runBlocking {
            newSetlist("zebra")
            newSetlist("Apple")
            newSetlist("mango")

            assertEquals(listOf("Apple", "mango", "zebra"), setlists.getSetlists().first().map { it.name })
        }

    @Test
    fun reorderOfAnEmptySetlistWithAnEmptyListIsAllowed() =
        runBlocking {
            val list = newSetlist()

            setlists.reorder(list, emptyList())

            assertEquals(emptyList<SetlistEntry>(), setlists.getEntries(list))
        }

    @Test
    fun appendingASongThatDoesNotExistIsRefused() =
        runBlocking {
            val list = newSetlist()

            var refused = false
            try {
                setlists.appendSong(list, 9999)
            } catch (_: SQLiteConstraintException) {
                refused = true
            }

            assertTrue(refused)
            assertEquals(emptyList<SetlistEntry>(), setlists.getEntries(list))
        }

    @Test
    fun deletingSeveralSongsAtOnceRemovesTheirEntries() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            ids.forEach { setlists.appendSong(list, it) }

            songs.deleteSongs(songs.getAllSongs().first().filter { it.id != ids[1] })

            assertEquals(listOf("B"), titles(list))
        }

    @Test
    fun deletingTheSameSongTwiceDoesNothingTheSecondTime() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val list = newSetlist()
            setlists.appendSong(list, a)
            setlists.appendSong(list, b)
            val song = songs.getAllSongs().first().first { it.id == a }

            songs.delete(song)
            songs.delete(song)

            assertEquals(listOf("B"), titles(list))
        }

    @Test
    fun renamingASetlistKeepsItsEntries() =
        runBlocking {
            val a = addSong("A")
            val list = newSetlist("Old")
            setlists.appendSong(list, a)

            setlists.updateSetlist(Setlist(id = list, name = "New"))

            assertEquals(listOf("New"), setlists.getSetlists().first().map { it.name })
            assertEquals(listOf("A"), titles(list))
        }

    @Test
    fun knownHazardInsertingASongWithAnExistingIdReplacesItAndRemovesItsEntries() =
        runBlocking {
            // This pins a hazard, it does not endorse it. SongDao.insert uses REPLACE, which deletes the
            // old row first, and the foreign key then deletes its setlist entries. Nothing in the app
            // inserts with an existing id (all inserts use id 0, edits use update). If that changes, use
            // update, or this setlist is emptied without a word.
            val a = addSong("A")
            val list = newSetlist()
            setlists.appendSong(list, a)

            songs.insert(Song(id = a, title = "A again", artist = "A", lyrics = "words"))

            assertEquals(emptyList<String>(), titles(list))
        }

    @Test
    fun insertAtPutsASongBackWhereItWasAndNeverLeavesItAtTheEnd() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            ids.forEach { setlists.appendSong(list, it) }

            val first = setlists.insertAt(list, ids[2], 0)
            val middle = setlists.insertAt(list, ids[0], 2)
            val past = setlists.insertAt(list, ids[1], 99)

            assertEquals(listOf("C", "A", "A", "B", "C", "B"), titles(list))
            assertEquals(3, setOf(first, middle, past).size)
            assertEquals(listOf(0, 1, 2, 3, 4, 5), setlists.getEntries(list).map { it.position })
        }

    @Test
    fun insertAtOnAnEmptySetlistAndWithANegativeIndexIsFirst() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val list = newSetlist()

            setlists.insertAt(list, a, 5)
            setlists.insertAt(list, b, -3)

            assertEquals(listOf("B", "A"), titles(list))
        }

    @Test
    fun moveEntryMovesOnePlaceAndStopsAtTheEnds() =
        runBlocking {
            val ids = listOf("A", "B", "C").map { addSong(it) }
            val list = newSetlist()
            val entries = ids.map { setlists.appendSong(list, it) }

            assertTrue(setlists.moveEntry(list, entries[2], -1))
            assertEquals(listOf("A", "C", "B"), titles(list))
            assertTrue(setlists.moveEntry(list, entries[0], 1))
            assertEquals(listOf("C", "A", "B"), titles(list))

            assertFalse(setlists.moveEntry(list, entries[1], 1))
            assertFalse(setlists.moveEntry(list, entries[2], -1))
            assertEquals(listOf("C", "A", "B"), titles(list))
        }

    @Test
    fun moveEntryIgnoresAnEntryOfAnotherSetlistAndKeepsRepeatedSongsApart() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val one = newSetlist("One")
            val two = newSetlist("Two")
            val first = setlists.appendSong(one, a)
            setlists.appendSong(one, b)
            val repeat = setlists.appendSong(one, a)
            val foreign = setlists.appendSong(two, a)

            assertFalse(setlists.moveEntry(one, foreign, -1))
            assertTrue(setlists.moveEntry(one, repeat, -2))

            assertEquals(listOf("A", "A", "B"), titles(one))
            assertEquals(repeat, setlists.getEntries(one)[0].id)
            assertEquals(first, setlists.getEntries(one)[1].id)
            assertEquals(listOf("A"), titles(two))
        }

    private suspend fun loose(): Int = songs.countLooseSongs().first()

    @Test
    fun withNoSetlists_everySongIsLoose() =
        runBlocking {
            addSong("A")
            addSong("B")
            assertEquals(2, loose())
        }

    @Test
    fun aSongInASetlistIsNotLoose_evenInTwoSetlistsOrTwiceInOne() =
        runBlocking {
            val a = addSong("A")
            addSong("B")
            val one = newSetlist("One")
            val two = newSetlist("Two")
            setlists.appendSong(one, a)
            setlists.appendSong(one, a)
            setlists.appendSong(two, a)

            assertEquals(1, loose()) // only B
        }

    @Test
    fun removingTheEntry_orDeletingTheSetlist_makesTheSongLooseAgain() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            val one = newSetlist("One")
            val two = newSetlist("Two")
            val entryA = setlists.appendSong(one, a)
            setlists.appendSong(two, b)
            assertEquals(0, loose())

            setlists.removeEntry(entryA)
            assertEquals(1, loose())

            setlists.deleteSetlist(two)
            assertEquals(2, loose())
            assertEquals(2, songs.getAllSongs().first().size) // nothing was deleted or hidden
        }

    @Test
    fun deletingASong_keepsTheLooseCountRight() =
        runBlocking {
            val a = addSong("A")
            val b = addSong("B")
            addSong("C")
            setlists.appendSong(newSetlist(), a)
            assertEquals(2, loose())

            songs.delete(songs.getAllSongs().first().first { it.id == a })
            songs.delete(songs.getAllSongs().first().first { it.id == b })

            assertEquals(1, loose())
        }

    @Test
    fun aNewSongIsAddedToTheLibraryAndTheEndOfTheSetlistTogether_andIsNotLoose() =
        runBlocking {
            val a = addSong("A")
            val list = newSetlist()
            setlists.appendSong(list, a)

            val entryId = setlists.addNewSongToSetlist(list, Song(id = 77, title = "New", artist = "N", lyrics = "new words"))

            assertEquals(listOf("A", "New"), titles(list))
            assertEquals(entryId, setlists.getEntries(list).last().id)
            assertEquals(0, loose()) // both songs are in the setlist
            // The id it came with is ignored: no other song was touched or replaced.
            assertEquals(
                listOf("A", "New"),
                songs
                    .getAllSongs()
                    .first()
                    .map { it.title }
                    .sorted(),
            )
        }

    @Test
    fun ifTheSetlistDoesNotExist_neitherTheSongNorTheEntryIsWritten() =
        runBlocking {
            addSong("A")
            val before = songs.getAllSongs().first().size

            try {
                setlists.addNewSongToSetlist(9999, Song(title = "Lost", artist = "N", lyrics = "words"))
                org.junit.Assert.fail("a missing setlist must fail")
            } catch (expected: SQLiteConstraintException) {
                // the foreign key refused the entry, so the whole transaction must be undone
            }

            assertEquals(before, songs.getAllSongs().first().size)
            assertEquals(before, loose()) // no stray song outside every setlist
        }
}
