package dev.sriramcodessw.versesync.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.sriramcodessw.versesync.data.Song
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BluetoothViewModel(
    application: Application,
    bluetoothAdapter: BluetoothAdapter,
) : AndroidViewModel(application) {
    private val bluetoothService = BluetoothService(application, bluetoothAdapter)

    val state: StateFlow<BluetoothService.BluetoothState> = bluetoothService.state

    fun startDiscovery() {
        bluetoothService.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothService.stopDiscovery()
    }

    fun startServer() {
        bluetoothService.startServer()
    }

    fun connectToDevice(deviceAddress: String) {
        bluetoothService.connectToDevice(deviceAddress)
    }

    fun sendSongs(songs: List<Song>) {
        // Generate a unique ID for this transfer session to support resumption
        val transferId = UUID.randomUUID().toString()
        bluetoothService.sendSongs(songs, transferId)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService.release()
    }
}

class BluetoothViewModelFactory(
    private val application: Application,
    private val bluetoothAdapter: BluetoothAdapter,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BluetoothViewModel(application, bluetoothAdapter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
