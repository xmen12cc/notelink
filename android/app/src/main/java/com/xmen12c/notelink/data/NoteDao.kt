package com.xmen12c.notelink.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY pinned DESC, updated DESC")
    fun observeActive(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): Note?

    @Upsert
    suspend fun upsert(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}
