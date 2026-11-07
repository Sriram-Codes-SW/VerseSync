package dev.sriramcodessw.versesync.bluetooth

import android.bluetooth.BluetoothSocket
import dev.sriramcodessw.versesync.data.Song
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataTransferServiceTest {

    private lateinit var socketMock: BluetoothSocket
    private val outputStream = ByteArrayOutputStream()

    @BeforeEach
    fun setup() {
        socketMock = mock()
        whenever(socketMock.outputStream).thenReturn(outputStream)
    }

    @Test
    fun sendSongs_shouldWriteSerializedSongsToOutputStream() {
        val songsToSend = listOf(Song(id = 1, title = "Test Song", artist = "Test Artist", lyrics = "Test Lyrics"))
        val service = DataTransferService(socketMock)

        val success = service.sendSongs(songsToSend) { /* progress */ }

        assertTrue(success)
    }

    @Test
    fun receiveSongs_shouldDeserializeSongsFromInputStream() {
        val songsToReceive = listOf(Song(id = 1, title = "Test Song", artist = "Test Artist", lyrics = "Test Lyrics"))
        val json = """[{"id":1,"title":"Test Song","artist":"Test Artist","lyrics":"Test Lyrics","lrcData":null,"fingerprint":null}]"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        whenever(socketMock.inputStream).thenReturn(inputStream)
        val service = DataTransferService(socketMock)

        val receivedSongs = service.receiveSongs()

        assertEquals(songsToReceive, receivedSongs)
    }
}
