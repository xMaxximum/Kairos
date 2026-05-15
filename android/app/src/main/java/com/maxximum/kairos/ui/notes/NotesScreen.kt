package com.maxximum.kairos.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maxximum.kairos.app.TodoViewModel
import com.maxximum.kairos.domain.model.KairosTag
import com.maxximum.kairos.domain.model.LocalNoteTagLink
import com.maxximum.kairos.domain.model.Note
import com.maxximum.kairos.domain.model.NoteFolder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(viewModel: TodoViewModel, onBack: () -> Unit) {
    BackHandler { onBack() }
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val folders by viewModel.noteFolders.collectAsState(initial = emptyList())
    val tags by viewModel.allTags.collectAsState(initial = emptyList())
    val noteTagLinks by viewModel.noteTagLinks.collectAsState(initial = emptyList())
    val taskReferences by viewModel.noteTaskReferences.collectAsState(initial = emptyList())
    var selectedFolderClientId by remember { mutableStateOf<String?>(null) }
    var selectedNoteClientId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var folderNameDraft by remember { mutableStateOf("") }
    var tagDraft by remember { mutableStateOf("") }

    val selectedNote = notes.firstOrNull { it.clientId == selectedNoteClientId }
    val visibleFolders = folders
        .filter { it.parentClientId == selectedFolderClientId }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
    val visibleNotes = notes
        .filter { note ->
            val normalized = query.trim().lowercase(Locale.getDefault())
            if (normalized.isNotBlank()) {
                note.searchText(tags, noteTagLinks).contains(normalized)
            } else {
                note.folderClientId == selectedFolderClientId
            }
        }
        .sortedByDescending { it.updatedAt }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Notes", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.createNote(selectedFolderClientId) }) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search notes") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folderNameDraft,
                        onValueChange = { folderNameDraft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Folder") }
                    )
                    IconButton(onClick = {
                        viewModel.createFolder(folderNameDraft, selectedFolderClientId)
                        folderNameDraft = ""
                    }) {
                        Icon(Icons.Default.Folder, contentDescription = "Create folder")
                    }
                }
                FilterChip(
                    selected = selectedFolderClientId == null,
                    onClick = { selectedFolderClientId = null },
                    label = { Text("Root") }
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleFolders, key = { it.clientId }) { folder ->
                        FolderRow(folder = folder, selected = selectedFolderClientId == folder.clientId) {
                            selectedFolderClientId = folder.clientId
                            selectedNoteClientId = null
                        }
                    }
                    items(visibleNotes, key = { it.clientId }) { note ->
                        NoteRow(
                            note = note,
                            selected = note.clientId == selectedNoteClientId,
                            tags = note.tags(tags, noteTagLinks),
                            linkedTaskCount = taskReferences.count { it.noteClientId == note.clientId },
                            onClick = { selectedNoteClientId = note.clientId }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedNote == null) {
                    Text("Select or create a note", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.createNote(selectedFolderClientId) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New note")
                    }
                } else {
                    OutlinedTextField(
                        value = selectedNote.title,
                        onValueChange = { viewModel.updateNote(selectedNote.copy(title = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    OutlinedTextField(
                        value = selectedNote.markdownBody,
                        onValueChange = { viewModel.updateNote(selectedNote.copy(markdownBody = it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = { Text("Write Markdown. Use [[task:id|Title]] for task links.") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        selectedNote.tags(tags, noteTagLinks).forEach { tag ->
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tagDraft,
                            onValueChange = { tagDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Add tag") }
                        )
                        Button(onClick = {
                            viewModel.addNoteTag(selectedNote, tagDraft)
                            tagDraft = ""
                        }) {
                            Text("Add")
                        }
                        IconButton(onClick = { viewModel.deleteNote(selectedNote) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete note")
                        }
                    }
                    val linkedTasks = taskReferences.count { it.noteClientId == selectedNote.clientId }
                    if (linkedTasks > 0) {
                        Text("$linkedTasks linked task${if (linkedTasks == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: NoteFolder, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(folder.name, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NoteRow(note: Note, selected: Boolean, tags: List<KairosTag>, linkedTaskCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(note.markdownBody.ifBlank { "Empty note" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                tags.take(3).forEach { tag ->
                    Text("#${tag.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (linkedTaskCount > 0) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.height(14.dp))
                    Text("$linkedTaskCount", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun Note.tags(tags: List<KairosTag>, links: List<LocalNoteTagLink>): List<KairosTag> {
    val tagIds = links.filter { it.noteClientId == clientId && it.deletedAt == null }.map { it.tagClientId }.toSet()
    return tags.filter { it.clientId in tagIds }
}

private fun Note.searchText(tags: List<KairosTag>, links: List<LocalNoteTagLink>): String {
    return listOf(title, markdownBody, tags(tags, links).joinToString(" ") { it.name })
        .joinToString(" ")
        .lowercase(Locale.getDefault())
}
