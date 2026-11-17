package dev.sriramcodessw.versesync.concert

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.sriramcodessw.versesync.data.Song
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private val DarkConcertColors =
    darkColorScheme(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        primary = Color(0xFFFFE082),
        onPrimary = Color.Black,
        surfaceVariant = Color(0xFF262626),
        onSurfaceVariant = Color.White,
        outline = Color.White,
    )

private val LightConcertColors =
    lightColorScheme(
        background = Color.White,
        surface = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        primary = Color(0xFF0B2A8A),
        onPrimary = Color.White,
        surfaceVariant = Color(0xFFEDEDED),
        onSurfaceVariant = Color.Black,
        outline = Color.Black,
    )

private val ControlHeight = 72.dp

/**
 * The screen used during a show (ADR-20 Phase 1): the lyrics of the current song, large and high
 * contrast, with big controls. It never uses the microphone or the network. The look is its own
 * and does not change the rest of the app.
 */
@OptIn(FlowPreview::class)
@Composable
fun ConcertScreen(
    viewModel: ConcertViewModel,
    onLeave: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val display by viewModel.display.collectAsState()
    val library by viewModel.library.collectAsState()

    // Saved, so a rotation does not close a dialog in the middle of a show.
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var showJump by rememberSaveable { mutableStateOf(false) }
    var showOtherSong by rememberSaveable { mutableStateOf(false) }
    var showDisplay by rememberSaveable { mutableStateOf(false) }

    // Keeps the screen on while the concert screen is open, with no permission needed.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // The status and navigation bar icons must be readable on the concert background, then go back as they were.
    DisposableEffect(view, display.lightTheme) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = display.lightTheme
        controller?.isAppearanceLightNavigationBars = display.lightTheme
        onDispose {
            if (controller != null && previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
            if (controller != null && previousNavigation != null) controller.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    // An edge swipe while scrolling lyrics is easy to do by accident, so leaving is confirmed.
    BackHandler { confirmLeave = true }

    // The setlist was deleted while the screen was open (for example from another window).
    LaunchedEffect(state.setlistGone) { if (state.setlistGone) onLeave() }

    MaterialTheme(
        colorScheme = if (display.lightTheme) LightConcertColors else DarkConcertColors,
        typography = MaterialTheme.typography,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val controller = state.controller
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val shown = controller.shown) {
                        ConcertShown.Empty -> {
                            Message("This setlist is empty. Leave and add songs to it.")
                        }

                        ConcertShown.Blank -> {
                            Box(modifier = Modifier.fillMaxSize())
                        }

                        is ConcertShown.InSetlist -> {
                            val song = state.songs[shown.entry.songId]
                            Column(modifier = Modifier.fillMaxSize()) {
                                Header(
                                    text = "Song ${shown.index + 1} of ${shown.total}",
                                    title = song?.title.orEmpty(),
                                    artist = song?.artist.orEmpty(),
                                )
                                if (song != null) {
                                    LyricsBody(
                                        song = song,
                                        scale = display.textScale,
                                        scrollKey = shown.entry.entryId,
                                        initialScroll = state.initialScroll,
                                        onScrolled = viewModel::saveScroll,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        is ConcertShown.OffList -> {
                            val song = library.firstOrNull { it.id == shown.songId }
                            Column(modifier = Modifier.fillMaxSize()) {
                                Header(text = "Not in the setlist", title = song?.title.orEmpty(), artist = song?.artist.orEmpty())
                                if (song == null) {
                                    Message("This song is no longer in your library.")
                                } else {
                                    LyricsBody(
                                        song = song,
                                        scale = display.textScale,
                                        scrollKey = -shown.songId - 1,
                                        initialScroll = 0,
                                        onScrolled = {},
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ControlButton(
                        label = "Previous",
                        enabled = controller.hasPrevious || controller.blank,
                        onClick = viewModel::previous,
                        modifier = Modifier.weight(1f),
                    )
                    ControlButton(
                        label = "Next",
                        enabled = controller.hasNext || controller.blank,
                        onClick = viewModel::next,
                        modifier = Modifier.weight(1f),
                        filled = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ControlButton(
                        label = "Songs",
                        onClick = { showJump = true },
                        modifier = Modifier.weight(1f),
                    )
                    ControlButton(
                        label = if (controller.blank) "Show" else "Blank",
                        onClick = viewModel::toggleBlank,
                        modifier = Modifier.weight(1f),
                    )
                    ControlButton(
                        label = "Display",
                        onClick = { showDisplay = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (showJump) {
                JumpDialog(
                    controller = controller,
                    songs = state.songs,
                    onJump = {
                        showJump = false
                        viewModel.jumpTo(it)
                    },
                    onOtherSong = {
                        showJump = false
                        showOtherSong = true
                    },
                    onBackToSetlist = {
                        showJump = false
                        viewModel.backToSetlist()
                    },
                    onDismiss = { showJump = false },
                )
            }
            if (showOtherSong) {
                OtherSongDialog(
                    library = library,
                    onPick = {
                        showOtherSong = false
                        viewModel.showOffList(it)
                    },
                    onDismiss = { showOtherSong = false },
                )
            }
            if (showDisplay) {
                DisplayDialog(
                    display = display,
                    onScale = viewModel::setTextScale,
                    onLight = viewModel::setLightTheme,
                    onDismiss = { showDisplay = false },
                )
            }
            if (confirmLeave) {
                AlertDialog(
                    onDismissRequest = { confirmLeave = false },
                    title = { Text("Leave the concert?", fontSize = 24.sp) },
                    text = { Text("Your place is saved. You can resume from Setlists, under the Folders tab.", fontSize = 18.sp) },
                    confirmButton = {
                        Button(
                            onClick = {
                                confirmLeave = false
                                onLeave()
                            },
                            modifier = Modifier.heightIn(min = 56.dp),
                        ) { Text("Leave", fontSize = 20.sp) }
                    },
                    dismissButton = {
                        Button(onClick = { confirmLeave = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text("Stay", fontSize = 20.sp)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 24.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Header(
    text: String,
    title: String,
    artist: String,
) {
    // One heading for a screen reader, read once, with the visible text and no repeated description.
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        Text(text, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 22.sp, maxLines = 1)
        Text(artist, fontSize = 16.sp, maxLines = 1)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun LyricsBody(
    song: Song,
    scale: Float,
    scrollKey: Int,
    initialScroll: Int,
    onScrolled: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new song starts at the top, or where it was saved. The saved offset is applied once the
    // lyrics are laid out, because before that there is nothing to scroll.
    val scrollState = rememberSaveable(scrollKey, saver = ScrollState.Saver) { ScrollState(0) }
    var applied by rememberSaveable(scrollKey) { mutableStateOf(initialScroll == 0) }
    LaunchedEffect(scrollKey, scrollState.maxValue) {
        // maxValue is Int.MAX_VALUE until the lyrics are measured, so wait for a real length.
        if (!applied && scrollState.maxValue in 1 until Int.MAX_VALUE) {
            scrollState.scrollTo(initialScroll.coerceAtMost(scrollState.maxValue))
            applied = true
        }
    }
    LaunchedEffect(scrollKey) {
        snapshotFlow { scrollState.value }.drop(1).debounce(500).collect { onScrolled(it) }
    }

    val locale = remember(song.language) { runCatching { LocaleList(song.language) }.getOrNull() }
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(scrollState)) {
        if (song.lyrics.isBlank()) {
            Text("No lyrics", fontSize = (26 * scale).sp, textAlign = TextAlign.Start)
        } else {
            Text(
                text = song.lyrics,
                style =
                    TextStyle(
                        fontSize = (26 * scale).sp,
                        lineHeight = (38 * scale).sp,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onBackground,
                        localeList = locale,
                    ),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    // The visible label is the accessible name, so voice control and screen readers agree.
    val common = modifier.heightIn(min = ControlHeight)
    if (filled) {
        Button(onClick = onClick, enabled = enabled, modifier = common, shape = MaterialTheme.shapes.large) {
            Text(label, fontSize = 22.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = common,
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        ) {
            Text(label, fontSize = 22.sp)
        }
    }
}

@Composable
private fun JumpDialog(
    controller: ConcertController,
    songs: Map<Int, Song>,
    onJump: (Int) -> Unit,
    onOtherSong: () -> Unit,
    onBackToSetlist: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a song", fontSize = 24.sp) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(controller.entries) { position, entry ->
                        val song = songs[entry.songId]
                        val current = position == controller.index && controller.offListSongId == null
                        Text(
                            // Marked by a symbol as well as by colour, and as selected for a screen reader.
                            text = (if (current) "> " else "") + "${position + 1}. ${song?.title.orEmpty()}",
                            fontSize = 22.sp,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .semantics { selected = current }
                                    .clickable { onJump(position) }
                                    .padding(vertical = 14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOtherSong, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("A song not in the setlist", fontSize = 18.sp)
                }
                if (controller.offListSongId != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBackToSetlist, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Back to the setlist", fontSize = 18.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Close", fontSize = 18.sp) } },
    )
}

@Composable
private fun OtherSongDialog(
    library: List<Song>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches =
        remember(library, query) {
            val q = query.trim()
            library.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A song not in the setlist", fontSize = 22.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    itemsIndexed(matches) { _, song ->
                        Text(
                            text = "${song.title} - ${song.artist}",
                            fontSize = 20.sp,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { onPick(song.id) }
                                    .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Cancel", fontSize = 18.sp) } },
    )
}

@Composable
private fun DisplayDialog(
    display: ConcertDisplay,
    onScale: (Float) -> Unit,
    onLight: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Display", fontSize = 24.sp) },
        text = {
            Column {
                Text("Text size", fontSize = 18.sp)
                Slider(
                    value = display.textScale,
                    onValueChange = onScale,
                    valueRange = ConcertDisplay.MIN_TEXT_SCALE..ConcertDisplay.MAX_TEXT_SCALE,
                    modifier = Modifier.semantics { contentDescription = "Text size" },
                )
                Spacer(Modifier.height(8.dp))
                // The whole row is the switch, so it has one name and a large target.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(value = display.lightTheme, role = Role.Switch, onValueChange = onLight),
                ) {
                    Text("Light theme", fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Switch(checked = display.lightTheme, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text("Done", fontSize = 18.sp) } },
    )
}
