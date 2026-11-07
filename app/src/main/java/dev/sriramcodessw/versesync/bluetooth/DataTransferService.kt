package dev.sriramcodessw.versesync.bluetooth

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.sriramcodessw.versesync.data.Song
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

private const val TAG = "DataTransferService"

/** Upper bound for one transfer. The size comes from the remote device, so it is not trusted. */
const val MAX_TRANSFER_BYTES = 8 * 1024 * 1024

class DataTransferService(private val socket: BluetoothSocket) {

    private val dataInputStream = DataInputStream(socket.inputStream)
    private val dataOutputStream = DataOutputStream(socket.outputStream)

    /**
     * Protocol:
     * 1. Sender sends TRANSFER_ID (UTF) and TOTAL_SIZE (Int)
     * 2. Receiver validates the size and replies with an OFFSET (Int), or -1 to refuse
     * 3. Sender sends data from that offset
     *
     * The receiver does not persist partial data, so it always replies with offset 0.
     */

    fun sendSongs(songs: List<Song>, transferId: String, onProgress: (Int) -> Unit): Boolean {
        return try {
            val json = Gson().toJson(songs)
            val data = json.toByteArray(StandardCharsets.UTF_8)
            val totalSize = data.size

            // Step 1: Send Header
            dataOutputStream.writeUTF(transferId)
            dataOutputStream.writeInt(totalSize)
            dataOutputStream.flush()

            // Step 2: Read Offset from Receiver (-1 means the receiver refused the transfer)
            val offset = dataInputStream.readInt()
            if (offset < 0 || offset > totalSize) {
                Log.w(TAG, "Receiver refused transfer $transferId (offset $offset)")
                return false
            }
            Log.d(TAG, "Resuming transfer $transferId from offset $offset")

            // Step 3: Send Data from Offset
            var bytesWritten = offset
            val bufferSize = 1024

            while (bytesWritten < totalSize) {
                val length = if (bytesWritten + bufferSize > totalSize) totalSize - bytesWritten else bufferSize
                dataOutputStream.write(data, bytesWritten, length)
                bytesWritten += length
                onProgress((bytesWritten * 100) / totalSize)
            }
            dataOutputStream.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in send", e)
            false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun receiveSongs(getOffsetForId: (String) -> Int, onProgress: (String, Int) -> Unit): List<Song>? {
        return try {
            // Step 1: Read Header
            val transferId = dataInputStream.readUTF()
            val totalSize = dataInputStream.readInt()

            // Never allocate based on an unchecked remote value.
            if (totalSize < 0 || totalSize > MAX_TRANSFER_BYTES) {
                Log.w(TAG, "Rejecting transfer with invalid size $totalSize")
                dataOutputStream.writeInt(-1)
                dataOutputStream.flush()
                return null
            }

            // Step 2: Always restart from 0. Resuming from a stored offset would leave the
            // start of the buffer zero-filled, because partial data is not kept.
            val offset = 0
            dataOutputStream.writeInt(offset)
            dataOutputStream.flush()

            // Step 3: Read data
            val fullData = ByteArray(totalSize)
            var bytesRead = offset
            val buffer = ByteArray(1024)

            while (bytesRead < totalSize) {
                val read = dataInputStream.read(buffer, 0, Math.min(buffer.size, totalSize - bytesRead))
                if (read == -1) break
                System.arraycopy(buffer, 0, fullData, bytesRead, read)
                bytesRead += read
                onProgress(transferId, bytesRead)
            }

            if (bytesRead == totalSize) {
                val json = String(fullData, StandardCharsets.UTF_8)
                val songListType = object : TypeToken<List<Song>>() {}.type
                Gson().fromJson(json, songListType)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in receive", e)
            null
        }
    }
}
