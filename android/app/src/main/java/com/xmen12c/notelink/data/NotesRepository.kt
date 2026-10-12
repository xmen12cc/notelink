package com.xmen12c.notelink.data

import kotlinx.coroutines.flow.Flow

/** Local note operations; sync (pull/push) lives in the sync engine. */
class NotesRepository(private val dao: NoteDao) {

    fun observeActive(): Flow<List<Note>> = dao.observeActive()

    suspend fun get(id: String): Note? = dao.get(id)

    /** Creates the note if it does not exist yet. Returns false when a brand-new note is still blank. */
    suspend fun saveDraft(id: String, title: String, body: String, pinned: Boolean, color: String): Boolean {
        val existing = dao.get(id)
        if (existing == null && title.isBlank() && body.isBlank()) return false
        val note = (existing ?: Note(id = id, title = "", body = "", updated = System.currentTimeMillis())).copy(
            title = title,
            body = body,
            pinned = pinned,
            color = color,
            updated = System.currentTimeMillis(),
            dirty = true,
        )
        dao.upsert(note)
        return true
    }

    suspend fun togglePin(id: String) = mutate(id) { it.copy(pinned = !it.pinned) }

    suspend fun setColor(id: String, color: String) = mutate(id) { it.copy(color = color) }

    /** Soft-delete: tombstone now, file removed from the repo on the next sync. */
    suspend fun delete(id: String) = mutate(id) {
        it.copy(deleted = true, pinned = false, dirty = true, updated = System.currentTimeMillis())
    }

    private suspend fun mutate(id: String, transform: (Note) -> Note) {
        val existing = dao.get(id) ?: return
        dao.upsert(transform(existing).copy(dirty = true, updated = System.currentTimeMillis()))
    }
}
