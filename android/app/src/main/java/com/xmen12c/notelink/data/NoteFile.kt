package com.xmen12c.notelink.data

import java.time.Instant
import java.util.UUID

/**
 * Note serialization: one markdown file per note with a small hand-rolled
 * frontmatter header. The file name (notes/<id>.md) is the authority for the
 * id; the header keeps a copy for human readers.
 */
object NoteFile {

    fun serialize(note: Note): String = buildString {
        append("---\n")
        append("id: ").append(note.id).append('\n')
        append("title: ").append(note.title.replace("\n", " ")).append('\n')
        append("pinned: ").append(note.pinned).append('\n')
        append("color: ").append(note.color).append('\n')
        append("updated: ").append(Instant.ofEpochMilli(note.updated).toString()).append('\n')
        append("deleted: ").append(note.deleted).append('\n')
        append("---\n")
        append(note.body)
    }

    fun parse(text: String, fallbackId: String): Note? {
        if (!text.startsWith("---")) return null
        val end = text.indexOf("\n---", 3)
        if (end < 0) return null
        val header = text.substring(3, end)
        var body = text.substring(end + 4)
        if (body.startsWith("\r\n")) body = body.substring(2) else if (body.startsWith("\n")) body = body.substring(1)

        var id = fallbackId
        var title = ""
        var pinned = false
        var color = Note.COLOR_DEFAULT
        var updated = 0L
        var deleted = false
        for (rawLine in header.lines()) {
            val line = rawLine.trim()
            val sep = line.indexOf(": ")
            if (sep <= 0) continue
            val key = line.substring(0, sep)
            val value = line.substring(sep + 2)
            when (key) {
                "id" -> if (value.isNotBlank()) id = value
                "title" -> title = value
                "pinned" -> pinned = value.equals("true", ignoreCase = true)
                "color" -> color = value
                "updated" -> updated = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
                "deleted" -> deleted = value.equals("true", ignoreCase = true)
            }
        }
        return Note(
            id = id.ifBlank { UUID.randomUUID().toString() },
            title = title,
            body = body,
            pinned = pinned,
            color = color,
            updated = updated,
            deleted = deleted,
        )
    }
}
