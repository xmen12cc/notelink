package com.xmen12c.notelink.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        fun build(context: Context): NotesDatabase =
            Room.databaseBuilder(context, NotesDatabase::class.java, "notelink.db").build()
    }
}
