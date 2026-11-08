package dev.sriramcodessw.versesync.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.sriramcodessw.versesync.bluetooth.BluetoothDeviceDomain
import dev.sriramcodessw.versesync.bluetooth.BluetoothService
import dev.sriramcodessw.versesync.bluetooth.BluetoothViewModel
import dev.sriramcodessw.versesync.data.Song
import dev.sriramcodessw.versesync.ui.viewmodels.ShareViewModel
import dev.sriramcodessw.versesync.ui.viewmodels.VerseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(
    onBack: () -> Unit,
    bluetoothViewModel: BluetoothViewModel,
    shareViewModel: ShareViewModel,
    verseViewModel: VerseViewModel,
) {
    val songs by verseViewModel.songs.collectAsState()
    val bluetoothState by bluetoothViewModel.state.collectAsState()

    var isReceiver by remember { mutableStateOf(false) }

    // Android 12+ requires BLUETOOTH_* permissions at runtime; Android 11 and below need
    // location for discovery. Without this, scanning or listening throws SecurityException.
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            if (grants.isNotEmpty() && grants.values.all { it }) {
                pendingAction?.invoke()
            } else {
                Toast.makeText(context, "Bluetooth permission is required to share songs", Toast.LENGTH_SHORT).show()
            }
            pendingAction = null
        }

    fun withBluetoothPermissions(action: () -> Unit) {
        val required =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        val missing =
            required.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(bluetoothState) {
        val received = (bluetoothState as? BluetoothService.BluetoothState.SongsReceived)?.songs
        if (received != null) {
            shareViewModel.incomingSongs.clear()
            shareViewModel.incomingSongs.addAll(received)
            isReceiver = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isReceiver) "Receive Songs" else "Share Songs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isReceiver && bluetoothState !is BluetoothService.BluetoothState.Discovering && bluetoothState !is BluetoothService.BluetoothState.Connecting) {
                        Button(onClick = { withBluetoothPermissions { bluetoothViewModel.startDiscovery() } }) {
                            Text("Scan")
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = bluetoothState) {
            is BluetoothService.BluetoothState.Transferring -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Transferring... ${state.progress}%")
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { state.progress / 100f })
                    }
                }
            }

            is BluetoothService.BluetoothState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }

            else -> {
                if (isReceiver) {
                    ReceiverContent(
                        modifier = Modifier.padding(paddingValues),
                        shareViewModel = shareViewModel,
                        onBack = onBack,
                    )
                } else {
                    SenderContent(
                        modifier = Modifier.padding(paddingValues),
                        songs = songs,
                        bluetoothState = state,
                        onDeviceClick = { deviceAddress -> bluetoothViewModel.connectToDevice(deviceAddress) },
                        onStartSending = { songsToShare -> bluetoothViewModel.sendSongs(songsToShare) },
                        onStartServer = { withBluetoothPermissions { bluetoothViewModel.startServer() } },
                    )
                }
            }
        }
    }
}

@Composable
fun SenderContent(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    bluetoothState: BluetoothService.BluetoothState,
    onDeviceClick: (String) -> Unit,
    onStartSending: (List<Song>) -> Unit,
    onStartServer: () -> Unit,
) {
    val songsToShare = remember { mutableStateListOf<Song>() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                SongListPanel(Modifier.weight(1f), "Your Songs", songs) { song -> if (!songsToShare.contains(song)) songsToShare.add(song) }
                SongListPanel(Modifier.weight(1f), "Songs to Share", songsToShare) { song -> songsToShare.remove(song) }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SongListPanel(Modifier.weight(1f), "Your Songs", songs) { song -> if (!songsToShare.contains(song)) songsToShare.add(song) }
                SongListPanel(Modifier.weight(1f), "Songs to Share", songsToShare) { song -> songsToShare.remove(song) }
            }
        }

        when (val state = bluetoothState) {
            is BluetoothService.BluetoothState.Discovering -> {
                DiscoveredDevicesDialog(devices = state.devices, onDeviceClick = onDeviceClick)
            }

            is BluetoothService.BluetoothState.Connecting -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is BluetoothService.BluetoothState.Connected -> {
                Button(
                    onClick = { onStartSending(songsToShare.toList()) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    enabled = songsToShare.isNotEmpty(),
                ) {
                    Text("Send Selected Songs")
                }
            }

            else -> {
                Button(onClick = onStartServer, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text("Become Discoverable")
                }
            }
        }
    }
}

@Composable
fun DiscoveredDevicesDialog(
    devices: List<BluetoothDeviceDomain>,
    onDeviceClick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Found Devices") },
        text = {
            LazyColumn {
                items(devices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable { onDeviceClick(device.address) },
                    )
                }
            }
        },
        confirmButton = { },
    )
}

@Composable
fun ReceiverContent(
    modifier: Modifier = Modifier,
    shareViewModel: ShareViewModel,
    onBack: () -> Unit,
) {
    val incomingSongs = shareViewModel.incomingSongs
    val selectedSongs = shareViewModel.selectedSongs
    var songToView by remember { mutableStateOf<Song?>(null) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                ReceiverSongListPanel(Modifier.weight(1f), incomingSongs, selectedSongs, { s, i -> shareViewModel.onSongSelected(s, i) }, { s -> songToView = s }, { s -> shareViewModel.acceptSong(s) }, { s -> shareViewModel.rejectSong(s) })
                LyricPreviewPanel(Modifier.weight(1f), songToView)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ReceiverSongListPanel(Modifier.weight(1f), incomingSongs, selectedSongs, { s, i -> shareViewModel.onSongSelected(s, i) }, { s -> songToView = s }, { s -> shareViewModel.acceptSong(s) }, { s -> shareViewModel.rejectSong(s) })
                LyricPreviewPanel(Modifier.weight(1f), songToView)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = {
                shareViewModel.acceptSelectedSongs()
                if (incomingSongs.isEmpty()) onBack()
            }) {
                Text("Accept Selected")
            }
            Button(onClick = {
                shareViewModel.rejectAllSongs()
                onBack()
            }) {
                Text("Reject All")
            }
        }
    }
}

@Composable
fun SongListPanel(
    modifier: Modifier = Modifier,
    title: String,
    songs: List<Song>,
    onSongClicked: (Song) -> Unit,
) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { onSongClicked(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiverSongListPanel(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    selectedSongs: SnapshotStateList<Song>,
    onSongSelected: (Song, Boolean) -> Unit,
    onViewSong: (Song) -> Unit,
    onAcceptSong: (Song) -> Unit,
    onRejectSong: (Song) -> Unit,
) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(8.dp)) {
            Text(text = "Incoming Songs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn {
                items(songs, key = { it.id }) { song ->
                    ListItem(
                        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Checkbox(checked = selectedSongs.contains(song), onCheckedChange = { onSongSelected(song, it) })
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onAcceptSong(song) }) {
                                    Icon(Icons.Default.Check, contentDescription = "Accept Song")
                                }
                                IconButton(onClick = { onRejectSong(song) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Reject Song")
                                }
                            }
                        },
                        modifier = Modifier.clickable { onViewSong(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun LyricPreviewPanel(
    modifier: Modifier = Modifier,
    song: Song?,
) {
    Card(modifier = modifier.padding(8.dp)) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            if (song != null) {
                Text(text = song.title, style = MaterialTheme.typography.headlineSmall)
                Text(text = song.artist, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                LazyColumn {
                    item { Text(song.lyrics) }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a song to preview its lyrics.")
                }
            }
        }
    }
}
