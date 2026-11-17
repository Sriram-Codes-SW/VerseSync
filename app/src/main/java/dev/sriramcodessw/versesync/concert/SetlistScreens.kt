package dev.sriramcodessw.versesync.concert

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.Setlist
import dev.sriramcodessw.versesync.data.SetlistEntryWithSong
import dev.sriramcodessw.versesync.data.Song
import kotlinx.coroutines.flow.Flow

/** The Setlists tab of the main screen (ADR-20). Concert mode is free, so nothing here looks at Pro status. */
@Composable
fun SetlistsTabContent(
    setlists: List<Setlist>,
    resume: ResumeInfo?,
    onResume: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onStart: (Int) -> Unit,
    onDelete: (Setlist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
    ) {
        if (resume != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onResume(resume.setlistId) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resume", style = MaterialTheme.typography.labelLarge)
                        Text("${resume.setlistName}, song ${resume.songNumber}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (setlists.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No setlists yet. Tap + to make one for a show.")
                }
            }
        }
        items(setlists, key = { it.id }) { setlist ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpen(setlist.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(setlist.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
                IconButton(onClick = { onStart(setlist.id) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start concert with ${setlist.name}")
                }
                IconButton(onClick = { onDelete(setlist) }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${setlist.name}")
                }
            }
            HorizontalDivider()
        }
    }
}

/** Asks for a name and makes a setlist. */
@Composable
fun NewSetlistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New setlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeleteSetlistDialog(
    setlist: Setlist,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${setlist.name}?") },
        text = { Text("The setlist is deleted. Your songs are kept.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for a new name for a setlist. */
@Composable
fun RenameSetlistDialog(
    currentName: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename setlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onRename(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shows a setlist and edits it (ADR-20): add songs at the end, move one up or down (buttons, so a
 * screen reader can use them), remove one with an undo, and rename the setlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistEditorScreen(
    name: String,
    entries: List<SetlistEntryWithSong>,
    library: List<Song>,
    removed: Flow<RemovedEntry>,
    onAddSong: (Int) -> Unit,
    onMove: (entryId: Int, by: Int) -> Unit,
    onRemove: (row: SetlistEntryWithSong, index: Int) -> Unit,
    onUndo: (RemovedEntry) -> Unit,
    onRename: (String) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Each removal offers an undo for a while. Taking it puts the song back where it was.
    LaunchedEffect(removed) {
        removed.collect { gone ->
            val result =
                snackbar.showSnackbar(
                    message = "Removed ${gone.title}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onUndo(gone)
        }
    }

    if (showRename) {
        RenameSetlistDialog(
            currentName = name,
            onRename = {
                showRename = false
                onRename(it)
            },
            onDismiss = { showRename = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename setlist")
                    }
                    IconButton(onClick = onStart, enabled = entries.isNotEmpty()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start concert")
                    }
                },
            )
        },
        // In the bottom bar, so the add button floats above it instead of covering it.
        bottomBar = {
            if (entries.isNotEmpty()) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp)) {
                    Text("Start concert")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add a song to the setlist")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("This setlist is empty. Tap + to add songs, in the order they will be played.")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(entries, key = { _, row -> row.entry.id }) { position, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text("${position + 1}. ${row.song.title}", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (row.song.lyrics.isBlank()) "${row.song.artist} - no lyrics" else row.song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            IconButton(
                                onClick = { onMove(row.entry.id, -1) },
                                enabled = position > 0,
                                modifier = Modifier.size(56.dp),
                            ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${row.song.title} up") }
                            IconButton(
                                onClick = { onMove(row.entry.id, 1) },
                                enabled = position < entries.lastIndex,
                                modifier = Modifier.size(56.dp),
                            ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${row.song.title} down") }
                            IconButton(
                                onClick = { onRemove(row, position) },
                                modifier = Modifier.size(56.dp),
                            ) { Icon(Icons.Default.Delete, contentDescription = "Remove ${row.song.title}") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        var addedCount by remember { mutableStateOf(0) }
        val matches =
            remember(library, query) {
                val q = query.trim()
                library.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
            }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Add songs") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search your library") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (addedCount > 0) Text("Added $addedCount", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(matches, key = { it.id }) { song ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 56.dp)
                                        .clickable {
                                            onAddSong(song.id)
                                            addedCount++
                                        }.padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(song.title, style = MaterialTheme.typography.bodyLarge)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Done") } },
        )
    }
}
