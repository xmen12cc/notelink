package com.xmen12c.notelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Screen {
    data object List : Screen
    data class Editor(val noteId: String?) : Screen
    data object Settings : Screen
}

@Composable
fun NoteLinkApp() {
    val vm: NotesViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.List) }

    when (val current = screen) {
        Screen.List -> NotesListScreen(
            vm = vm,
            onOpen = { screen = Screen.Editor(it) },
            onNew = { screen = Screen.Editor(null) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Editor -> EditorScreen(vm, current.noteId, onClose = { screen = Screen.List })
        Screen.Settings -> SettingsScreen(vm, onClose = { screen = Screen.List })
    }
}
