package com.xmen12c.notelink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val color: String = COLOR_DEFAULT,
    val updated: Long,
    val deleted: Boolean = false,
    /** Git blob sha of the remote file at last sync; null = never pushed. */
    val remoteSha: String? = null,
    /** True when local edits have not been pushed yet. */
    val dirty: Boolean = true,
) {
    companion object {
        const val COLOR_DEFAULT = "default"
        val COLORS = listOf(COLOR_DEFAULT, "yellow", "green", "blue", "pink", "gray")
    }
}
