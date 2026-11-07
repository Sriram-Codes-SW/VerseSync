package dev.sriramcodessw.versesync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sriramcodessw.versesync.data.LanguageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongScreen(
    onBack: () -> Unit, 
    onSave: (String, String, String, String) -> Unit,
    languageViewModel: LanguageViewModel
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    
    val installedLanguageCodes by languageViewModel.installedLanguages.collectAsState()
    
    // Map installed codes back to human-readable names
    val languages = languageViewModel.availableLanguages.filter { 
        installedLanguageCodes.contains(it.code) 
    }
    
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { 
        mutableStateOf(languages.firstOrNull() ?: languageViewModel.availableLanguages[0]) 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // Respect System Navigation and Status bars
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it },
            label = { Text("Artist") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Dynamic Language Selector
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLanguage.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Song Language") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.name) },
                        onClick = {
                            selectedLanguage = language
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            label = { Text("Lyrics") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Navigate back to the main screen" }
            ) {
                Text("Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { onSave(title, artist, lyrics, selectedLanguage.code) },
                modifier = Modifier.semantics { contentDescription = "Save the new song" }
            ) {
                Text("Save")
            }
        }
    }
}
