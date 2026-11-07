package dev.sriramcodessw.versesync.bluetooth

import android.bluetooth.BluetoothSocket
import com.google.gson.Gson
import dev.sriramcodessw.versesync.data.Song
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
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

        // Prepare receiver sending back offset 0
        val offsetOut = ByteArrayOutputStream()
        DataOutputStream(offsetOut).writeInt(0)
        val inStream = ByteArrayInputStream(offsetOut.toByteArray())
        whenever(socketMock.inputStream).thenReturn(inStream)

        val service = DataTransferService(socketMock)
        val success = service.sendSongs(songsToSend, "transfer-123") { /* progress */ }

        assertTrue(success)
    }

    @Test
    fun receiveSongs_shouldDeserializeSongsFromInputStream() {
        val songsToReceive = listOf(Song(id = 1, title = "Test Song", artist = "Test Artist", lyrics = "Test Lyrics"))
        val json = Gson().toJson(songsToReceive)
        val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)

        val transferId = "transfer-123"
        val headerAndData = ByteArrayOutputStream()
        val dataOut = DataOutputStream(headerAndData)
        dataOut.writeUTF(transferId)
        dataOut.writeInt(jsonBytes.size)
        dataOut.write(jsonBytes)
        dataOut.flush()

        val inputStream = ByteArrayInputStream(headerAndData.toByteArray())
        whenever(socketMock.inputStream).thenReturn(inputStream)

        val service = DataTransferService(socketMock)
        val receivedSongs = service.receiveSongs(
            getOffsetForId = { 0 },
            onProgress = { _, _ -> }
        )

        assertEquals(songsToReceive, receivedSongs)
    }
}
