package com.runestone.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlayStatsEntity::class, GameSizeCacheEntity::class, GameMetadataCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RunestoneDatabase : RoomDatabase() {
    abstract fun playStatsDao(): PlayStatsDao
    abstract fun gameSizeCacheDao(): GameSizeCacheDao
    abstract fun gameMetadataCacheDao(): GameMetadataCacheDao

    companion object {
        @Volatile
        private var instance: RunestoneDatabase? = null

        fun getInstance(context: Context): RunestoneDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RunestoneDatabase::class.java,
                    "runestone.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
