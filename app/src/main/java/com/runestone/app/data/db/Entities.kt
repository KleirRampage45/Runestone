package com.runestone.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val storageName: String,
    val totalSeconds: Long = 0,
    val lastPlayedAt: Long = 0,
    val sessionStartedAt: Long = 0,
)

@Entity(tableName = "game_size_cache")
data class GameSizeCacheEntity(
    @PrimaryKey val storageName: String,
    val totalBytes: Long = 0,
)

@Entity(tableName = "game_metadata_cache")
data class GameMetadataCacheEntity(
    @PrimaryKey val cacheKey: String,
    val title: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val localCoverPath: String = "",
    val developer: String = "",
    val publisher: String = "",
    val genres: String = "",
    val releaseYear: String = "",
    val metadataSource: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
)
