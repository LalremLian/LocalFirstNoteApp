package com.lalrem.noteapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lalrem.noteapp.data.local.entity.AssetEntity
import com.lalrem.noteapp.data.local.entity.NoteEntity

@Database(entities = [NoteEntity::class, AssetEntity::class], version = 1)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
