package com.runestone.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.runestone.app.data.EngineType
import com.runestone.app.data.GameConfigService
import com.runestone.app.provider.AvailableGame
import com.runestone.app.session.GameSessionManager
import com.runestone.app.services.CoverExtractor
import com.runestone.app.services.GameMetadataService
import com.runestone.app.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeUiState(
    val cards: List<GameCardInfo> = emptyList(),
    val isLoading: Boolean = true,
    val activeFilter: EngineType? = null,
    val searchQuery: String = "",
    val currentSort: SortMode = SortMode.DATE_ADDED,
    val cardLayout: HomeCardLayout = HomeCardLayout.GRID_2,
    val pausedGame: GameCardInfo? = null,
)

class GameListViewModel(
    application: Application,
    private val workspaceManager: WorkspaceManager,
    private val sessionManager: GameSessionManager,
    private val metadataService: GameMetadataService,
) : AndroidViewModel(application) {
    private val context = application
    private val gameSizeCache = mutableMapOf<String, Long>()
    private val gameSizeInFlight = mutableSetOf<String>()
    private val metadataWarmupInFlight = mutableSetOf<String>()
    var gameMetadataCache = mutableMapOf<String, GameMetadataService.GameMetadata>()
    var availableGames: List<AvailableGame> = emptyList()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _games = MutableStateFlow<List<WorkspaceManager.GameInfo>>(emptyList())
    val games: StateFlow<List<WorkspaceManager.GameInfo>> = _games.asStateFlow()

    init { refreshGames() }

    fun refreshGames() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val scanned = withContext(Dispatchers.IO) { workspaceManager.scanInstalledGames() }
            _games.value = scanned
            val cards = buildCards(scanned)
            _uiState.value = _uiState.value.copy(cards = cards, isLoading = false)
        }
    }

    fun setFilter(engine: EngineType?) { _uiState.value = _uiState.value.copy(activeFilter = engine); applyFilters() }
    fun setSearch(query: String) { _uiState.value = _uiState.value.copy(searchQuery = query); applyFilters() }
    fun setSort(sort: SortMode) { _uiState.value = _uiState.value.copy(currentSort = sort); applyFilters() }
    fun setLayout(layout: HomeCardLayout) { _uiState.value = _uiState.value.copy(cardLayout = layout) }

    fun applyFilters() {
        val state = _uiState.value
        var filtered = _games.value
        if (state.activeFilter != null) filtered = filtered.filter { it.engineType == state.activeFilter }
        if (state.searchQuery.isNotEmpty()) filtered = filtered.filter { it.displayName.contains(state.searchQuery, ignoreCase = true) }
        filtered = when (state.currentSort) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
            SortMode.RECENT -> filtered.sortedByDescending { sessionManager.getLastPlayed(it.storageName) }
            SortMode.DATE_ADDED -> filtered.sortedByDescending { File(it.originalPath).parentFile?.lastModified() ?: 0L }
        }
        val cards = buildCards(filtered)
        _uiState.value = state.copy(cards = cards, pausedGame = cards.find { it.isPaused })
    }

    private fun buildCards(games: List<WorkspaceManager.GameInfo>): List<GameCardInfo> {
        val state = _uiState.value
        return games.map { g ->
            val card = toCardInfo(g)
            if (card.coverUrl != null) return@map card
            val coverUrl = availableGames.firstOrNull {
                it.title.equals(card.displayName, ignoreCase = true) ||
                it.title.contains(card.displayName, ignoreCase = true) ||
                card.displayName.contains(it.title, ignoreCase = true)
            }?.coverUrl ?: gameMetadataCache[card.displayName]?.coverUrl
            card.copy(coverUrl = coverUrl ?: extractFallbackCover(g))
        }
    }

    private fun extractFallbackCover(g: WorkspaceManager.GameInfo): String? {
        val path = CoverExtractor.extractFallbackCover(context, g.storageName, File(g.originalPath))
        return path?.let { "local:$it" }
    }

    private fun toCardInfo(g: WorkspaceManager.GameInfo): GameCardInfo {
        val perGame = runCatching { GameConfigService(context, workspaceManager).loadPerGame(g.storageName) }.getOrNull()
        val metadata = perGame?.metadata?.takeIf {
            it.gameTitle.isBlank() || metadataTitleMatches(g.displayName, it.gameTitle)
        }
        val coverUrl = perGame?.game?.customCoverPath?.let { if (File(it).exists()) "local:$it" else null }
            ?: metadata?.localCoverPath?.takeIf { it.isNotEmpty() }?.let { if (File(it).exists()) "local:$it" else null }
        return GameCardInfo(
            storageName = g.storageName, displayName = metadata?.gameTitle?.takeIf { it.isNotEmpty() } ?: g.displayName,
            engineType = g.engineType, fileCount = g.fileCount, fileSize = cachedGameSize(g),
            totalPlayTime = sessionManager.getPlayTime(g.storageName), lastPlayedTimestamp = sessionManager.getLastPlayed(g.storageName),
            isReady = true, coverUrl = coverUrl,
            metadataDeveloper = metadata?.developer ?: "", metadataGenres = metadata?.genres ?: "", metadataYear = metadata?.releaseYear ?: "",
        )
    }

    private fun cachedGameSize(g: WorkspaceManager.GameInfo): Long = gameSizeCache[g.storageName] ?: 0L

    private fun metadataTitleMatches(a: String, b: String): Boolean {
        fun norm(v: String) = v.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9]+"), " ").trim()
        val i = norm(a); val m = norm(b)
        if (i.isBlank() || m.isBlank()) return false
        if (i == m || (i.length >= 6 && (i.contains(m) || m.contains(i)))) return true
        val iT = i.split(" ").filter { it.length > 1 }.toSet()
        val mT = m.split(" ").filter { it.length > 1 }.toSet()
        return iT.isNotEmpty() && iT.intersect(mT).size >= minOf(2, iT.size)
    }

    class Factory(
        private val application: Application,
        private val workspaceManager: WorkspaceManager,
        private val sessionManager: GameSessionManager,
        private val metadataService: GameMetadataService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameListViewModel(application, workspaceManager, sessionManager, metadataService) as T
    }
}
