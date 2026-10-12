package com.xmen12c.notelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xmen12c.notelink.data.Note
import com.xmen12c.notelink.ui.theme.notePalette
import kotlinx.coroutines.delay
import java.util.UUID

private data class Draft(val title: String, val body: String, val pinned: Boolean, val color: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: NotesViewModel, noteId: String?, onClose: () -> Unit) {
    val isNew = noteId == null
    val id = remember { noteId ?: UUID.randomUUID().toString() }

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableStateOf(Note.COLOR_DEFAULT) }
    var loaded by rememberSaveable { mutableStateOf(isNew) }
    var showColors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastSaved by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            vm.getNote(noteId)?.let { note ->
                title = note.title
                body = note.body
                pinned = note.pinned
                color = note.color
                loaded = true
            }
        }
    }

    val draft = Draft(title, body, pinned, color)

    // Debounced autosave ~1 s after the last keystroke.
    LaunchedEffect(title, body, pinned, color, loaded) {
        if (!loaded) return@LaunchedEffect
        if (lastSaved == null) {
            lastSaved = draft
            return@LaunchedEffect
        }
        if (draft == lastSaved) return@LaunchedEffect
        delay(900)
        if (Draft(title, body, pinned, color) == lastSaved) return@LaunchedEffect
        vm.save(id, title, body, pinned, color)
        lastSaved = Draft(title, body, pinned, color)
    }

    // Safety sweep: while typing continuously the debounce never fires, so
    // flush whatever changed at least every few seconds. Offline edits simply
    // queue in the local database until the next successful sync.
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        while (true) {
            delay(4_000)
            val current = Draft(title, body, pinned, color)
            if (current != lastSaved) {
                vm.save(id, current.title, current.body, current.pinned, current.color)
                lastSaved = current
            }
        }
    }

    fun persistAndClose() {
        if (loaded) {
            val current = Draft(title, body, pinned, color)
            if (current != lastSaved) {
                vm.save(id, current.title, current.body, current.pinned, current.color)
                lastSaved = current
            }
        }
        onClose()
    }

    BackHandler { persistAndClose() }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("The note disappears here and on your other devices at the next sync.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                    onClose()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { persistAndClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(
                        text = if (isNew) "New note" else "Note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (pinned) "Unpin" else "Pin",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                    IconButton(onClick = { showColors = !showColors }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Note color")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = showColors) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Note.COLORS.forEach { candidate ->
                        val palette = notePalette(candidate)
                        val selected = color == candidate
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(palette.container)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { color = candidate },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = palette.onContainer,
                                )
                            }
                        }
                    }
                }
            }
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        "Title",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = flatTextFieldColors(),
            )
            TextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("Start typing…") },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = flatTextFieldColors(),
            )
        }
    }
}
