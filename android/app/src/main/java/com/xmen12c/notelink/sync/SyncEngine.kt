package com.xmen12c.notelink.sync

import com.xmen12c.notelink.data.Note
import com.xmen12c.notelink.data.NoteDao
import com.xmen12c.notelink.data.NoteFile
import com.xmen12c.notelink.data.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val at: Long) : SyncState
    data class Error(val message: String, val at: Long) : SyncState
}

/**
 * Two-way merge against the notes repo, per-note last-writer-wins on the
 * `updated` timestamp. A local note that was never pushed (no remote sha) or
 * has unsaved edits is pushed; a remote file that differs from the locally
 * recorded sha is pulled unless local edits are newer.
 */
class SyncEngine(
    private val dao: NoteDao,
    private val api: GitHubApi,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun sync(): SyncState = mutex.withLock {
        val cfg = settings.load()
        if (cfg == null) {
            // First-run state, not an error: stay quiet until the user connects.
            return _state.value
        }
        _state.value = SyncState.Syncing
        try {
            val remote = api.listNotes(cfg) // path -> sha
            val remoteIds = remote.keys.map { idFromPath(it) }.toSet()
            val locals = dao.getAll().associateBy { it.id }

            // 1) Pull: fetch remote files that differ from what we last saw.
            for ((path, sha) in remote) {
                val id = idFromPath(path)
                val local = locals[id]
                if (local != null && local.remoteSha == sha) continue
                val remoteNote = api.getNote(cfg, path)
                    ?.let { NoteFile.parse(it, id) }
                    ?.copy(id = id)
                    ?: continue
                when {
                    local == null -> if (!remoteNote.deleted) {
                        dao.upsert(remoteNote.copy(remoteSha = sha, dirty = false))
                    }
                    // A clean local copy always follows the remote. A dirty one only
                    // yields when the remote edit is newer (last-writer-wins).
                    !local.dirty || remoteNote.updated >= local.updated ->
                        dao.upsert(remoteNote.copy(id = local.id, remoteSha = sha, dirty = false))
                    // else: local is dirty and newer -> push below.
                }
            }

            // 2) Purge tombstones whose file is gone from the repo.
            dao.getAll()
                .filter { it.deleted && !it.dirty && it.id !in remoteIds }
                .forEach { dao.delete(it.id) }

            // 3) Push: dirty notes, plus anything whose remote file disappeared
            //    (rescues local-only notes if the repo folder was emptied).
            for (local in dao.getAll()) {
                val path = "notes/${local.id}.md"
                if (!local.dirty && remote.containsKey(path)) continue
                val newSha = api.putNote(cfg, path, local, remote[path])
                dao.upsert(local.copy(remoteSha = newSha, dirty = false))
            }

            _state.value = SyncState.Success(now())
        } catch (e: Exception) {
            _state.value = SyncState.Error(e.message ?: e.javaClass.simpleName, now())
        }
        _state.value
    }

    fun isConfigured(): Boolean = settings.load() != null

    private fun idFromPath(path: String) = path.removePrefix("notes/").removeSuffix(".md")

    private fun now() = System.currentTimeMillis()
}
