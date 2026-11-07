package dev.sriramcodessw.versesync.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.data.TransferPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

private const val TAG = "BluetoothService"

data class BluetoothDeviceDomain(val name: String, val address: String)

@SuppressLint("MissingPermission")
class BluetoothService(
    private val context: Context, 
    private val bluetoothAdapter: BluetoothAdapter,
    private val mainDispatcher: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    sealed class BluetoothState {
        object Idle : BluetoothState()
        data class Discovering(val devices: List<BluetoothDeviceDomain>) : BluetoothState()
        object ListeningForConnection : BluetoothState()
        data class Connecting(val device: BluetoothDeviceDomain) : BluetoothState()
        data class Connected(val device: BluetoothDeviceDomain) : BluetoothState()
        data class Transferring(val progress: Int) : BluetoothState()
        data class SongsReceived(val songs: List<Song>) : BluetoothState()
        data class Error(val message: String) : BluetoothState()
    }

    private val _state = MutableStateFlow<BluetoothState>(BluetoothState.Idle)
    val state = _state.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val transferPrefs = TransferPreferences(context)
    private val serverThreads = mutableListOf<Thread>()
    private var clientSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var dataTransferService: DataTransferService? = null

    private val foundDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                @Suppress("DEPRECATION")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                device?.let {
                    val currentState = _state.value
                    if (currentState is BluetoothState.Discovering) {
                        val newDevice = BluetoothDeviceDomain(it.name ?: "Unknown", it.address)
                        if (!currentState.devices.contains(newDevice)) {
                            mainDispatcher.launch { _state.value = BluetoothState.Discovering(currentState.devices + newDevice) }
                        }
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        ContextCompat.registerReceiver(context, foundDeviceReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        bluetoothAdapter.startDiscovery()
        _state.value = BluetoothState.Discovering(emptyList())
    }

    fun stopDiscovery() {
        try {
            context.unregisterReceiver(foundDeviceReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Receiver error", e)
        }
        bluetoothAdapter.cancelDiscovery()
        _state.value = BluetoothState.Idle
    }

    fun startServer() {
        _state.value = BluetoothState.ListeningForConnection
        for (uuid in BluetoothConstants.SUPPORTED_UUIDS) {
            val thread = Thread {
                try {
                    val localServerSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        "${BluetoothConstants.SERVICE_NAME}-$uuid",
                        uuid
                    )
                    serverSocket = localServerSocket
                    val socket = localServerSocket.accept()
                    clientSocket = socket
                    mainDispatcher.launch { 
                        _state.value = BluetoothState.Connected(
                            BluetoothDeviceDomain(socket.remoteDevice.name ?: "Unknown", socket.remoteDevice.address)
                        ) 
                    }
                    dataTransferService = DataTransferService(socket)
                    listenForIncomingSongs()
                } catch (e: IOException) {
                    Log.e(TAG, "Server error", e)
                }
            }
            serverThreads.add(thread)
            thread.start()
        }
    }

    fun connectToDevice(deviceAddress: String) {
        _state.value = BluetoothState.Connecting(BluetoothDeviceDomain("Unknown", deviceAddress))
        serviceScope.launch {
            val remoteDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
            for (uuid in BluetoothConstants.SUPPORTED_UUIDS) {
                try {
                    val socket = remoteDevice.createInsecureRfcommSocketToServiceRecord(uuid)
                    bluetoothAdapter.cancelDiscovery()
                    socket.connect()
                    clientSocket = socket
                    mainDispatcher.launch { 
                        _state.value = BluetoothState.Connected(
                            BluetoothDeviceDomain(remoteDevice.name ?: "Unknown", remoteDevice.address)
                        ) 
                    }
                    dataTransferService = DataTransferService(socket)
                    listenForIncomingSongs()
                    return@launch
                } catch (e: IOException) {
                    Log.w(TAG, "Retry connection...")
                }
            }
            mainDispatcher.launch { _state.value = BluetoothState.Error("Connection failed.") }
        }
    }

    fun sendSongs(songs: List<Song>, transferId: String) {
        serviceScope.launch {
            mainDispatcher.launch { _state.value = BluetoothState.Transferring(0) }
            val success = dataTransferService?.sendSongs(songs, transferId) { progress ->
                mainDispatcher.launch { _state.value = BluetoothState.Transferring(progress) }
            } ?: false
            mainDispatcher.launch {
                if (success) _state.value = BluetoothState.Idle
                else _state.value = BluetoothState.Error("Transfer failed.")
            }
        }
    }

    private fun listenForIncomingSongs() {
        serviceScope.launch {
            val receivedSongs = dataTransferService?.receiveSongs(
                getOffsetForId = { id ->
                    runBlocking {
                        val progress = transferPrefs.transferProgress.first()
                        if (progress?.transferId == id) progress.bytesReceived else 0
                    }
                },
                onProgress = { id, progress ->
                    serviceScope.launch { transferPrefs.updateProgress(id, progress) }
                    mainDispatcher.launch { _state.value = BluetoothState.Transferring(-1) }
                }
            )
            mainDispatcher.launch {
                if (receivedSongs != null) {
                    transferPrefs.clearProgress()
                    _state.value = BluetoothState.SongsReceived(receivedSongs)
                } else {
                    _state.value = BluetoothState.Error("Reception failed.")
                }
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            clientSocket?.close()
            serverThreads.forEach { it.interrupt() }
            serverThreads.clear()
            _state.value = BluetoothState.Idle
        } catch (e: IOException) {
            Log.e(TAG, "Stop server error", e)
        }
    }

    fun release() {
        stopDiscovery()
        stopServer()
    }
}
