package com.xmen12c.notelink

import android.app.Application
import com.xmen12c.notelink.data.NotesDatabase
import com.xmen12c.notelink.data.NotesRepository
import com.xmen12c.notelink.data.SettingsStore
import com.xmen12c.notelink.sync.GitHubApi
import com.xmen12c.notelink.sync.SyncEngine
import com.xmen12c.notelink.sync.SyncWorker

class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.settings = SettingsStore(this)
        Graph.api = GitHubApi()
        val dao = NotesDatabase.build(this).noteDao()
        Graph.repository = NotesRepository(dao)
        Graph.syncEngine = SyncEngine(dao, Graph.api, Graph.settings)
        SyncWorker.schedule(this)
    }
}
