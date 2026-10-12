package com.xmen12c.notelink

import com.xmen12c.notelink.data.NotesRepository
import com.xmen12c.notelink.data.SettingsStore
import com.xmen12c.notelink.sync.GitHubApi
import com.xmen12c.notelink.sync.SyncEngine

/** App-wide service locator, wired once in [NotesApplication.onCreate]. */
object Graph {
    lateinit var settings: SettingsStore
    lateinit var api: GitHubApi
    lateinit var repository: NotesRepository
    lateinit var syncEngine: SyncEngine
}
