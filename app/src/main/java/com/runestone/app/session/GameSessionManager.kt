package com.runestone.app.session

import android.content.Context
import android.util.Log
import com.runestone.app.data.db.PlayStatsEntity
import com.runestone.app.data.db.RunestoneDatabase
import com.runestone.app.util.AppScope
import kotlinx.coroutines.launch

class GameSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "GameSession"
        private const val PREFS_RUNESTONE = "runestone"
    }

    private val db = RunestoneDatabase.getInstance(context)
    private val playStatsDao = db.playStatsDao()
    private val playTimeCache = mutableMapOf<String, Long>()
    private val lastPlayedCache = mutableMapOf<String, Long>()

    data class SessionState(
        val storageName: String?,
        val gamePath: String?,
        val startedAt: Long,
        val isMinimized: Boolean,
        val isPaused: Boolean,
        val killRequest: String?,
    )

    fun getState(): SessionState {
        val prefs = context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE)
        return SessionState(
            storageName = prefs.getString("active_game_storage", null),
            gamePath = prefs.getString("active_game_path", null),
            startedAt = prefs.getLong("active_game_started_at", 0L),
            isMinimized = prefs.getBoolean("game_minimized", false),
            isPaused = prefs.getString("paused_game", null) != null,
            killRequest = prefs.getString("kill_game", null),
        )
    }

    fun start(storageName: String, gamePath: String) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE).edit()
            .putString("active_game_storage", storageName)
            .putString("active_game_path", gamePath)
            .putLong("active_game_started_at", now)
            .putLong("active_game_last_seen_at", now)
            .remove("paused_game").remove("game_minimized")
            .apply()
        AppScope.io.launch { playStatsDao.startSession(storageName, now) }
    }

    fun finalize(reason: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE)
        val storageName = prefs.getString("active_game_storage", null) ?: return false
        val startedAt = prefs.getLong("active_game_started_at", 0L)
        if (startedAt <= 0L) return false

        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceIn(0L, 14400L)
        if (elapsedSeconds > 0L) {
            playTimeCache[storageName] = (playTimeCache[storageName] ?: 0L) + elapsedSeconds
            lastPlayedCache[storageName] = System.currentTimeMillis()
            AppScope.io.launch {
                playStatsDao.addPlayTime(storageName, elapsedSeconds)
                playStatsDao.touchLastPlayed(storageName)
            }
            Log.i(TAG, "Play session finalized: $storageName +${elapsedSeconds}s ($reason)")
        }

        prefs.edit()
            .remove("active_game_storage").remove("active_game_path")
            .remove("active_game_started_at").remove("active_game_last_seen_at")
            .remove("paused_game").remove("game_minimized").remove("kill_game")
            .apply()
        return true
    }

    fun clearResumeState(reason: String) {
        finalize(reason)
        context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE).edit()
            .remove("paused_game").remove("game_minimized").remove("kill_game").apply()
    }

    fun pause(gamePath: String) {
        context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE).edit()
            .putBoolean("game_minimized", true).putString("paused_game", gamePath).apply()
    }

    fun requestKill(storageName: String) {
        context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE).edit()
            .putString("kill_game", storageName).apply()
    }

    fun clearKillSignal() {
        context.getSharedPreferences(PREFS_RUNESTONE, Context.MODE_PRIVATE).edit()
            .remove("kill_game").apply()
    }

    fun recordStop(storageName: String) {
        val now = System.currentTimeMillis()
        AppScope.io.launch {
            val entity = playStatsDao.get(storageName)
            val sessionStart = entity?.sessionStartedAt ?: 0L
            if (sessionStart > 0L) {
                val elapsed = (now - sessionStart) / 1000
                playStatsDao.addPlayTime(storageName, elapsed)
                playTimeCache[storageName] = (playTimeCache[storageName] ?: 0L) + elapsed
            }
        }
    }

    fun getPlayTime(storageName: String): Long = playTimeCache[storageName] ?: 0L

    fun getLastPlayed(storageName: String): Long = lastPlayedCache[storageName] ?: 0L

    /** Load all play stats from Room into memory cache. Call once at startup. */
    fun warmCache() {
        AppScope.io.launch {
            playStatsDao.getAll().forEach { e ->
                playTimeCache[e.storageName] = e.totalSeconds
                lastPlayedCache[e.storageName] = e.lastPlayedAt
            }
        }
    }
}
