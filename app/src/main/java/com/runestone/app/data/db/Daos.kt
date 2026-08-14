package com.runestone.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayStatsDao {
    @Query("SELECT * FROM play_stats WHERE storageName = :storageName")
    suspend fun get(storageName: String): PlayStatsEntity?

    @Query("SELECT * FROM play_stats")
    suspend fun getAll(): List<PlayStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: PlayStatsEntity)

    @Query("DELETE FROM play_stats WHERE storageName = :storageName")
    suspend fun delete(storageName: String)

    @Query("UPDATE play_stats SET totalSeconds = totalSeconds + :seconds, sessionStartedAt = 0 WHERE storageName = :storageName")
    suspend fun addPlayTime(storageName: String, seconds: Long)

    @Query("UPDATE play_stats SET lastPlayedAt = :now, sessionStartedAt = :now WHERE storageName = :storageName")
    suspend fun touchLastPlayed(storageName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE play_stats SET sessionStartedAt = :now WHERE storageName = :storageName")
    suspend fun startSession(storageName: String, now: Long = System.currentTimeMillis())
}

@Dao
interface GameSizeCacheDao {
    @Query("SELECT * FROM game_size_cache WHERE storageName = :storageName")
    suspend fun get(storageName: String): GameSizeCacheEntity?

    @Query("SELECT * FROM game_size_cache")
    suspend fun getAll(): List<GameSizeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: GameSizeCacheEntity)

    @Query("DELETE FROM game_size_cache WHERE storageName = :storageName")
    suspend fun delete(storageName: String)
}

@Dao
interface GameMetadataCacheDao {
    @Query("SELECT * FROM game_metadata_cache WHERE cacheKey = :cacheKey")
    suspend fun get(cacheKey: String): GameMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: GameMetadataCacheEntity)

    @Query("DELETE FROM game_metadata_cache WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)

    @Query("DELETE FROM game_metadata_cache WHERE cachedAt < :expiredBefore")
    suspend fun deleteOlderThan(expiredBefore: Long)
}
