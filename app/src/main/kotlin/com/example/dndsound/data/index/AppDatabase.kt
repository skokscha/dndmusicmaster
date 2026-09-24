package com.example.dndsound.data.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IndexedFileEntity::class, TrackOverrideEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dndsound.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
