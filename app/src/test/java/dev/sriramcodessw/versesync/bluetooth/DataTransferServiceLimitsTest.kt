package dev.sriramcodessw.versesync.bluetooth

import android.bluetooth.BluetoothSocket
import dev.sriramcodessw.versesync.data.Song
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class DataTransferServiceLimitsTest {

    private fun socketWith(input: ByteArray, output: ByteArrayOutputStream): BluetoothSocket {
        val socket: BluetoothSocket = mock()
        whenever(socket.inputStream).thenReturn(ByteArrayInputStream(input))
        whenever(socket.outputStream).thenReturn(output)
        return socket
    }

    private fun header(size: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply { writeUTF("t"); writeInt(size); flush() }
        return bytes.toByteArray()
    }

    @Test
    fun receiveSongs_rejectsOversizedTransferWithoutAllocating() {
        val out = ByteArrayOutputStream()
        val service = DataTransferService(socketWith(header(Int.MAX_VALUE), out))

        val result = service.receiveSongs { _, _ -> }

        assertNull(result)
        assertEquals(-1, DataInputStream(ByteArrayInputStream(out.toByteArray())).readInt())
    }

    @Test
    fun receiveSongs_rejectsNegativeSize() {
        val out = ByteArrayOutputStream()
        val service = DataTransferService(socketWith(header(-5), out))

        assertNull(service.receiveSongs { _, _ -> })
        assertEquals(-1, DataInputStream(ByteArrayInputStream(out.toByteArray())).readInt())
    }

    @Test
    fun receiveSongs_alwaysRepliesWithOffsetZero() {
        val json = "[]".toByteArray()
        val input = ByteArrayOutputStream().apply {
            write(header(json.size)); write(json)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        val service = DataTransferService(socketWith(input, out))

        val result = service.receiveSongs { _, _ -> }

        assertEquals(emptyList<Song>(), result)
        assertEquals(0, DataInputStream(ByteArrayInputStream(out.toByteArray())).readInt())
    }

    @Test
    fun sendSongs_abortsWhenReceiverRefuses() {
        val refusal = ByteArrayOutputStream().apply { DataOutputStream(this).writeInt(-1) }.toByteArray()
        val service = DataTransferService(socketWith(refusal, ByteArrayOutputStream()))

        val ok = service.sendSongs(listOf(Song(1, "t", "a", "l")), "id") { }

        assertFalse(ok)
    }
}

class DataTransferPayloadSizeTest {

    @Test
    fun smallSelection_isNotTooLarge() {
        val songs = List(10) { Song(it, "T$it", "A", "lyrics $it") }

        assertFalse(DataTransferService.isTooLarge(DataTransferService.encode(songs).size))
    }

    @Test
    fun selectionOverTheLimit_isReportedBeforeSending() {
        // About 9 MiB of lyrics, the size a few hundred fingerprinted songs reach.
        val songs = listOf(Song(1, "Big", "A", "x".repeat(9 * 1024 * 1024)))

        assertTrue(DataTransferService.isTooLarge(DataTransferService.encode(songs).size))
    }

    @Test
    fun limitIsInclusive() {
        assertFalse(DataTransferService.isTooLarge(MAX_TRANSFER_BYTES))
        assertTrue(DataTransferService.isTooLarge(MAX_TRANSFER_BYTES + 1))
    }
}
