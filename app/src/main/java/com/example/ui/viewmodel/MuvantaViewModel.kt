package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalProfileEntity
import com.example.data.local.MuvantaDatabase
import com.example.data.local.UserTitleEntryEntity
import com.example.data.remote.MetadataService
import com.example.data.remote.SupabaseAuthService
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabasePostgrestService
import com.example.data.remote.SupabaseSession
import com.example.data.repository.CatalogRepository
import com.example.data.sync.MuvantaSyncEngine
import com.example.data.sync.NetworkMonitor
import com.example.model.ArchiveStats
import com.example.model.ArchiveTiers
import com.example.model.CatalogItem
import com.example.model.ConflictResolutionChoice
import com.example.model.CustomCollectionWithItems
import com.example.model.DiscoverCategory
import com.example.model.DiscoverMode
import com.example.model.EnrichedCatalogItem
import com.example.model.ImportPreview
import com.example.model.LibraryFilter
import com.example.model.LibrarySortOption
import com.example.model.MediaType
import com.example.model.MoodCategory
import com.example.model.NamedCount
import com.example.model.RecommendationItem
import com.example.model.ResolvedMetadata
import com.example.model.SeriesEpisode
import com.example.model.SortOption
import com.example.model.StatusFilter
import com.example.model.SyncUiState
import com.example.model.TierProgress
import com.example.model.UserTitleState
import com.example.model.ViewingLogEntry
import com.example.model.WatchStatus
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverFilterState(
    val searchQuery: String = "",
    val category: DiscoverCategory = DiscoverCategory.ALL,
    val moods: Set<MoodCategory> = emptySet(),
    val genre: String? = null,
    val languageOrCountry: String? = null,
    val minRating: Int = 0,
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val tierId: String? = null,
    val decade: Int? = null,
    val sortOption: SortOption = SortOption.RANK_ASC,
    val mode: DiscoverMode = DiscoverMode.GRID,
    val page: Int = 1,
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (category != DiscoverCategory.ALL) count++
            count += moods.size
            if (genre != null) count++
            if (languageOrCountry != null) count++
            if (minRating > 0) count++
            if (statusFilter != StatusFilter.ALL) count++
            if (tierId != null) count++
            if (decade != null) count++
            if (sortOption != SortOption.RANK_ASC) count++
            return count
        }

    val hasAnyFilterOrSearch: Boolean
        get() = activeFilterCount > 0 || searchQuery.isNotBlank()
}

data class DiscoverUiState(
    val filters: DiscoverFilterState = DiscoverFilterState(),
    val totalFilteredCount: Int = 0,
    val currentPageItems: List<EnrichedCatalogItem> = emptyList(),
    val totalPages: Int = 1,
    val isLoading: Boolean = true,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val searchPreviewResults: List<EnrichedCatalogItem> = emptyList(),
    val featuredSpotlight: List<EnrichedCatalogItem> = emptyList(),
    val filmstripReel: List<EnrichedCatalogItem> = emptyList(),
    val trendingMovies: List<EnrichedCatalogItem> = emptyList(),
    val prestigeSeries: List<EnrichedCatalogItem> = emptyList(),
    val essentialAnime: List<EnrichedCatalogItem> = emptyList(),
    val acclaimedDocs: List<EnrichedCatalogItem> = emptyList(),
    val recentlyReleased: List<EnrichedCatalogItem> = emptyList(),
    val hiddenGems: List<EnrichedCatalogItem> = emptyList(),
    val watchlistRow: List<EnrichedCatalogItem> = emptyList(),
    val continueArchive: List<EnrichedCatalogItem> = emptyList(),
    val recommendations: List<RecommendationItem> = emptyList(),
    val stats: ArchiveStats = ArchiveStats(),
    val movieCount: Int = 1000,
    val seriesCount: Int = 300,
    val animeCount: Int = 70,
    val docCount: Int = 39,
)

class MuvantaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MuvantaDatabase.getInstance(application)
    private val networkMonitor = NetworkMonitor(application)
    private val supabaseAuth = SupabaseAuthService(application)
    private val supabasePostgrest = SupabasePostgrestService()
    private val syncEngine = MuvantaSyncEngine(
        dao = database.muvantaDao(),
        authService = supabaseAuth,
        postgrest = supabasePostgrest,
        networkMonitor = networkMonitor,
        scope = viewModelScope,
    )
    private var debouncedSyncJob: Job? = null

    private val repository = CatalogRepository(
        context = application,
        dao = database.muvantaDao(),
        metadataService = MetadataService(),
        currentOwnerIdProvider = { supabaseAuth.session.value?.userId ?: MuvantaSyncEngine.OWNER_LOCAL },
        isOnlineProvider = { networkMonitor.isOnline.value },
        onLocalMutationQueued = { scheduleAutoSyncIfConnected() },
    )

    private val _catalog = MutableStateFlow<List<CatalogItem>>(emptyList())
    private val _isCatalogLoading = MutableStateFlow(true)

    private val _homeSearchQuery = MutableStateFlow("")
    private val _discoverFilters = MutableStateFlow(DiscoverFilterState())
    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL_LOGGED)
    private val _librarySearchQuery = MutableStateFlow("")
    private val _librarySort = MutableStateFlow(LibrarySortOption.RECENT)
    private val _libraryListMode = MutableStateFlow(false)
    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    private val _canUndoBackupAction = MutableStateFlow(false)
    private val _authBusy = MutableStateFlow(false)
    private val _authError = MutableStateFlow<String?>(null)
    private val _authNotice = MutableStateFlow<String?>(null)
    private val _prefilledSignInEmail = MutableStateFlow("")
    private val _signOutPendingWarning = MutableStateFlow<Int?>(null)

    private var previousEntriesSnapshot: List<UserTitleEntryEntity>? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _authSuccessNavigateHome = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val authSuccessNavigateHome: SharedFlow<Unit> = _authSuccessNavigateHome.asSharedFlow()

    private val _authSuccessNavigateSignIn = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val authSuccessNavigateSignIn: SharedFlow<Unit> = _authSuccessNavigateSignIn.asSharedFlow()

    val syncState: StateFlow<SyncUiState> = syncEngine.syncState
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()
    val authError: StateFlow<String?> = _authError.asStateFlow()
    val authNotice: StateFlow<String?> = _authNotice.asStateFlow()
    val prefilledSignInEmail: StateFlow<String> = _prefilledSignInEmail.asStateFlow()
    val signOutPendingWarning: StateFlow<Int?> = _signOutPendingWarning.asStateFlow()
    val cachedPosterCount: StateFlow<Int> = repository.cachedPosterCountFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()
    val canUndoBackupAction: StateFlow<Boolean> = _canUndoBackupAction.asStateFlow()
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()
    val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()
    val librarySort: StateFlow<LibrarySortOption> = _librarySort.asStateFlow()
    val libraryListMode: StateFlow<Boolean> = _libraryListMode.asStateFlow()

    val searchHistory: StateFlow<List<String>> = repository.searchHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val profileState: StateFlow<LocalProfileEntity> = repository.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LocalProfileEntity(
            id = 1,
            displayName = "Cinema Archivist",
            tagline = "Exploring 1,000 films and 300 television series",
            defaultDiscoverMode = DiscoverMode.GRID.wire,
        ),
    )

    val customCollections: StateFlow<List<CustomCollectionWithItems>> =
        repository.collectionsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val userEntriesMap: StateFlow<Map<String, UserTitleState>> =
        repository.userEntriesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    private val metadataMap: StateFlow<Map<String, ResolvedMetadata>> =
        repository.metadataCacheFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    private val totalWatchedEpisodes: StateFlow<Int> =
        repository.totalWatchedEpisodesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    val enrichedItems: StateFlow<List<EnrichedCatalogItem>> = combine(
        _catalog,
        userEntriesMap,
        metadataMap,
    ) { items, users, metas ->
        items.map { item ->
            EnrichedCatalogItem(
                item = item,
                user = users[item.id] ?: UserTitleState(item.id),
                meta = metas[item.id] ?: ResolvedMetadata(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val enrichedById: StateFlow<Map<String, EnrichedCatalogItem>> = enrichedItems
        .combine(_isCatalogLoading) { list, _ -> list.associateBy { it.item.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    val statsState: StateFlow<ArchiveStats> = combine(
        enrichedItems,
        totalWatchedEpisodes,
    ) { items, episodesWatched ->
        computeStats(items, episodesWatched)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ArchiveStats(),
    )

    val homeUiState: StateFlow<HomeUiState> = combine(
        enrichedItems,
        _homeSearchQuery,
        statsState,
        _isCatalogLoading,
    ) { all, query, stats, loading ->
        val byId = all.associateBy { it.item.id }
        val spotlight = SPOTLIGHT_IDS.mapNotNull { byId[it] }
        val reel = FEATURED_REEL_IDS.mapNotNull { byId[it] }
        val trendingMovies = all.filter { it.item.type == MediaType.MOVIE }.take(14)
        val prestigeSeries = all.filter { it.item.type == MediaType.SERIES }.take(14)
        val essentialAnime = all.filter { it.item.isAnime }.take(14)
        val acclaimedDocs = all.filter { it.item.isDocumentary }.take(14)
        val recentlyReleased = all
            .filter { it.item.year >= 2020 }
            .sortedWith(compareByDescending<EnrichedCatalogItem> { it.item.year }.thenBy { it.item.rank })
            .take(14)
        val hiddenGems = all
            .filter { it.item.type == MediaType.MOVIE && it.item.rank in 220..520 }
            .filter { it.user.status != WatchStatus.WATCHED }
            .take(14)
        val watchlistRow = all
            .filter { it.user.status == WatchStatus.BOOKMARKED }
            .sortedByDescending { it.user.updatedAt }
            .take(12)
        val continueArchive = all
            .filter { it.user.isLogged }
            .sortedByDescending { it.user.updatedAt }
            .take(10)

        val recommendations = computeRecommendations(all)
        val previewResults = if (query.isBlank()) {
            emptyList()
        } else {
            filterBySearch(all, query).take(8)
        }

        HomeUiState(
            isLoading = loading,
            searchQuery = query,
            searchPreviewResults = previewResults,
            featuredSpotlight = spotlight,
            filmstripReel = reel,
            trendingMovies = trendingMovies,
            prestigeSeries = prestigeSeries,
            essentialAnime = essentialAnime,
            acclaimedDocs = acclaimedDocs,
            recentlyReleased = recentlyReleased,
            hiddenGems = hiddenGems,
            watchlistRow = watchlistRow,
            continueArchive = continueArchive,
            recommendations = recommendations,
            stats = stats,
            movieCount = all.count { it.item.type == MediaType.MOVIE },
            seriesCount = all.count { it.item.type == MediaType.SERIES },
            animeCount = all.count { it.item.isAnime },
            docCount = all.count { it.item.isDocumentary },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    val discoverUiState: StateFlow<DiscoverUiState> = combine(
        enrichedItems,
        _discoverFilters,
        _isCatalogLoading,
    ) { all, filters, loading ->
        val filtered = applyDiscoverFilters(all, filters)
        val total = filtered.size
        val totalPages = max(1, ceil(total / PAGE_SIZE.toDouble()).toInt())
        val safePage = filters.page.coerceIn(1, totalPages)
        val start = (safePage - 1) * PAGE_SIZE
        val slice = if (start < total) filtered.subList(start, minOf(start + PAGE_SIZE, total)) else emptyList()

        DiscoverUiState(
            filters = filters.copy(page = safePage),
            totalFilteredCount = total,
            currentPageItems = slice,
            totalPages = totalPages,
            isLoading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiscoverUiState(),
    )

    val libraryItems: StateFlow<List<EnrichedCatalogItem>> = combine(
        enrichedItems,
        _libraryFilter,
        _librarySearchQuery,
        _librarySort,
    ) { all, filter, query, sort ->
        val statusFiltered = all.filter { enriched ->
            when (filter) {
                LibraryFilter.ALL_LOGGED -> enriched.user.isLogged
                LibraryFilter.FAVORITES -> enriched.user.isFavorite
                LibraryFilter.WATCHING -> enriched.user.status == WatchStatus.WATCHING
                LibraryFilter.REWATCHING -> enriched.user.status == WatchStatus.REWATCHING
                LibraryFilter.BOOKMARKED -> enriched.user.status == WatchStatus.BOOKMARKED
                LibraryFilter.WATCHED -> enriched.user.status == WatchStatus.WATCHED
                LibraryFilter.ON_HOLD -> enriched.user.status == WatchStatus.ON_HOLD
                LibraryFilter.DROPPED -> enriched.user.status == WatchStatus.DROPPED
                LibraryFilter.RATED -> enriched.user.rating > 0
            }
        }
        val searched = if (query.isBlank()) statusFiltered else filterBySearch(statusFiltered, query)
        when (sort) {
            LibrarySortOption.RECENT -> searched.sortedByDescending { it.user.updatedAt }
            LibrarySortOption.RATING_DESC -> searched.sortedWith(
                compareByDescending<EnrichedCatalogItem> { it.user.rating }.thenByDescending { it.user.updatedAt },
            )
            LibrarySortOption.YEAR_DESC -> searched.sortedWith(
                compareByDescending<EnrichedCatalogItem> { it.item.year }.thenBy { it.item.rank },
            )
            LibrarySortOption.TITLE_ASC -> searched.sortedBy { it.item.title.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            val loaded = repository.loadBundledCatalog()
            _catalog.value = loaded
            _isCatalogLoading.value = false
            val byId = loaded.associateBy { it.id }
            val initialPriority = buildList {
                SPOTLIGHT_IDS.mapNotNullTo(this) { byId[it] }
                FEATURED_REEL_IDS.mapNotNullTo(this) { byId[it] }
                addAll(loaded.filter { it.type == MediaType.MOVIE }.take(10))
                addAll(loaded.filter { it.type == MediaType.SERIES }.take(10))
                addAll(loaded.filter { it.isAnime }.take(6))
                addAll(loaded.filter { it.isDocumentary }.take(6))
            }.distinctBy { it.id }
            initialPriority.forEach { repository.requestMetadata(it) }
            if (SupabaseConfig.isConfigured && networkMonitor.isOnline.value && supabaseAuth.session.value != null) {
                syncNow(manual = false)
            }
        }
        viewModelScope.launch {
            repository.profileFlow.collect { profile ->
                val preferred = DiscoverMode.fromWire(profile.defaultDiscoverMode)
                if (_discoverFilters.value.mode == DiscoverMode.GRID && preferred != DiscoverMode.GRID) {
                    _discoverFilters.update { it.copy(mode = preferred) }
                }
            }
        }
        // Automatically synchronise queued offline edits whenever connectivity returns
        viewModelScope.launch {
            var wasOnline = networkMonitor.isOnline.value
            networkMonitor.isOnline.drop(1).collect { nowOnline ->
                if (!wasOnline && nowOnline && SupabaseConfig.isConfigured && supabaseAuth.session.value != null) {
                    syncNow(manual = false)
                }
                wasOnline = nowOnline
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        networkMonitor.release()
    }

    // Supabase Auth & Offline-First Sync Controls
    private fun scheduleAutoSyncIfConnected() {
        if (!SupabaseConfig.isConfigured || !networkMonitor.isOnline.value || supabaseAuth.session.value == null) {
            return
        }
        debouncedSyncJob?.cancel()
        debouncedSyncJob = viewModelScope.launch {
            delay(1_400L)
            syncNow(manual = false)
        }
    }

    suspend fun getActiveSession(): SupabaseSession? =
        supabaseAuth.getSession(isOnline = networkMonitor.isOnline.value)

    fun clearAuthFeedback() {
        _authError.value = null
        _authNotice.value = null
    }

    fun signInWithSupabase(email: String, password: String) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            _authNotice.value = null
            val result = supabaseAuth.signInWithPassword(email, password)
            val verifiedSession = if (result.isSuccess) {
                supabaseAuth.getSession(isOnline = networkMonitor.isOnline.value)
            } else {
                null
            }
            _authBusy.value = false
            result.onSuccess {
                if (verifiedSession != null) {
                    syncEngine.clearAuthExpiredFlag()
                    _prefilledSignInEmail.value = ""
                    emitMessage("Signed in as ${verifiedSession.email}.")
                    _authSuccessNavigateHome.tryEmit(Unit)
                    syncNow(manual = false)
                } else {
                    _authError.value = "No active session was returned. Please confirm your email before logging in."
                }
            }.onFailure { err ->
                _authError.value = err.message ?: "Could not sign in. Please check your credentials."
            }
        }
    }

    fun signUpWithSupabase(email: String, password: String) {
        if (_authBusy.value) return
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            _authNotice.value = null
            val result = supabaseAuth.signUp(email, password)
            _authBusy.value = false
            result.onSuccess { outcome ->
                // Never auto-login after sign-up: redirect to Sign In with email prefilled and success message
                _prefilledSignInEmail.value = outcome.email
                _authNotice.value = if (outcome.requiresEmailConfirmation) {
                    "Account created! Check your email and confirm your account before logging in."
                } else {
                    "Account created successfully! Please sign in with your password to continue."
                }
                _authSuccessNavigateSignIn.tryEmit(Unit)
            }.onFailure { err ->
                _authError.value = err.message ?: "Could not create account. Please try again."
            }
        }
    }

    fun syncNow(manual: Boolean = true) {
        viewModelScope.launch {
            val catalogById = _catalog.value.associateBy { it.id }
            val result = syncEngine.syncNow(catalogById)
            result.onSuccess { summary ->
                if (manual || summary.conflictCount > 0) {
                    emitMessage(summary.message)
                }
            }.onFailure { err ->
                if (manual) {
                    emitMessage(err.message ?: "Unable to sync right now.")
                }
            }
        }
    }

    fun resolveSyncConflict(conflictId: Long, choice: ConflictResolutionChoice) {
        viewModelScope.launch {
            val catalogById = _catalog.value.associateBy { it.id }
            syncEngine.resolveConflict(conflictId, choice, catalogById)
                .onSuccess { emitMessage(it) }
                .onFailure { emitMessage(it.message ?: "Could not resolve conflict.") }
        }
    }

    fun requestSignOut() {
        viewModelScope.launch {
            val pending = repository.countPendingOps()
            if (pending > 0) {
                _signOutPendingWarning.value = pending
            } else {
                confirmSignOut()
            }
        }
    }

    fun dismissSignOutWarning() {
        _signOutPendingWarning.value = null
    }

    fun syncThenSignOut() {
        viewModelScope.launch {
            _signOutPendingWarning.value = null
            val catalogById = _catalog.value.associateBy { it.id }
            val result = syncEngine.syncNow(catalogById)
            val remaining = repository.countPendingOps()
            if (result.isSuccess && remaining == 0) {
                confirmSignOut()
            } else {
                emitMessage("Could not sync all pending changes ($remaining remaining). Resolve issues or discard locally.")
            }
        }
    }

    fun confirmSignOut() {
        viewModelScope.launch {
            _signOutPendingWarning.value = null
            debouncedSyncJob?.cancel()
            supabaseAuth.signOutRemoteBestEffort()
            repository.clearAllUserAccountData()
            previousEntriesSnapshot = null
            _canUndoBackupAction.value = false
            emitMessage("Signed out and cleared private account data from this device.")
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            repository.clearArtworkCache()
            emitMessage("Cleared cached poster artwork. Your library and sync queue are untouched.")
        }
    }

    fun requestMetadata(item: CatalogItem, forceRefresh: Boolean = false) {
        repository.requestMetadata(item, forceRefresh)
    }

    fun observeViewingHistory(itemId: String): Flow<List<ViewingLogEntry>> =
        repository.observeViewingHistoryForItem(itemId)

    fun observeSeriesEpisodes(itemId: String): Flow<List<SeriesEpisode>> =
        repository.observeSeriesEpisodes(itemId)

    fun ensureSeriesEpisodes(item: CatalogItem, forceRefresh: Boolean = false) {
        val knownId = metadataMap.value[item.id]?.tvmazeShowId ?: 0
        repository.ensureSeriesEpisodes(item, knownId, forceRefresh)
    }

    fun toggleEpisodeWatched(itemId: String, season: Int, episode: Int, currentlyWatched: Boolean) {
        viewModelScope.launch {
            repository.toggleEpisodeWatched(itemId, season, episode, currentlyWatched)
            // Automatically mark series as Watching if unwatched and user logs an episode
            if (!currentlyWatched) {
                val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
                if (current.status == WatchStatus.UNWATCHED || current.status == WatchStatus.BOOKMARKED) {
                    repository.saveUserEntry(current.copy(status = WatchStatus.WATCHING))
                }
            }
        }
    }

    fun setSeasonWatched(itemId: String, season: Int, episodes: List<SeriesEpisode>, watched: Boolean) {
        viewModelScope.launch {
            repository.setSeasonWatched(itemId, season, episodes, watched)
            if (watched) {
                val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
                if (current.status == WatchStatus.UNWATCHED || current.status == WatchStatus.BOOKMARKED) {
                    repository.saveUserEntry(current.copy(status = WatchStatus.WATCHING))
                }
                emitMessage("Marked Season $season as watched.")
            } else {
                emitMessage("Cleared watched status for Season $season.")
            }
        }
    }

    fun getSimilarTitles(itemId: String): List<EnrichedCatalogItem> {
        val all = enrichedItems.value
        val target = all.firstOrNull { it.item.id == itemId } ?: return emptyList()
        val targetGenres = target.effectiveGenre.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val targetMoods = target.effectiveMoods

        return all
            .asSequence()
            .filter { it.item.id != itemId }
            .map { candidate ->
                var score = 0
                if (candidate.item.type == target.item.type) score += 3
                if (candidate.item.isAnime && target.item.isAnime) score += 5
                if (candidate.item.isDocumentary && target.item.isDocumentary) score += 5
                if (candidate.item.tier.id == target.item.tier.id) score += 2
                if (abs(candidate.item.year - target.item.year) <= 10) score += 2
                val candGenres = candidate.effectiveGenre.split(",").map { it.trim().lowercase() }.toSet()
                score += (candGenres intersect targetGenres).size * 4
                score += (candidate.effectiveMoods intersect targetMoods).size * 2
                candidate to score
            }
            .filter { it.second >= 5 }
            .sortedWith(compareByDescending<Pair<EnrichedCatalogItem, Int>> { it.second }.thenBy { it.first.item.rank })
            .take(8)
            .map { it.first }
            .toList()
    }

    // Search History
    fun updateHomeSearch(query: String) {
        _homeSearchQuery.value = query
    }

    fun recordSearchQuery(query: String) {
        viewModelScope.launch {
            repository.recordSearchQuery(query)
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistoryItem(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }

    // Discover Filter Mutators
    fun openDiscoverWithCategory(category: DiscoverCategory) {
        _discoverFilters.update {
            it.copy(
                category = category,
                moods = emptySet(),
                tierId = null,
                page = 1,
            )
        }
    }

    fun openDiscoverWithMood(mood: MoodCategory) {
        _discoverFilters.update {
            it.copy(
                category = DiscoverCategory.ALL,
                moods = setOf(mood),
                tierId = null,
                page = 1,
            )
        }
    }

    fun openDiscoverWithTier(tierId: String) {
        val tier = ArchiveTiers.findById(tierId)
        val cat = when (tier?.type) {
            MediaType.MOVIE -> DiscoverCategory.MOVIES
            MediaType.SERIES -> DiscoverCategory.SERIES
            null -> DiscoverCategory.ALL
        }
        _discoverFilters.update {
            it.copy(
                category = cat,
                moods = emptySet(),
                tierId = tierId,
                searchQuery = "",
                page = 1,
            )
        }
    }

    fun openDiscoverWithSearch(query: String) {
        if (query.isNotBlank()) recordSearchQuery(query)
        _discoverFilters.update {
            it.copy(searchQuery = query, page = 1)
        }
    }

    fun updateDiscoverSearch(query: String) {
        _discoverFilters.update { it.copy(searchQuery = query, page = 1) }
    }

    fun updateDiscoverCategory(category: DiscoverCategory) {
        _discoverFilters.update {
            val tierStillValid = when {
                it.tierId == null -> true
                category == DiscoverCategory.MOVIES -> it.tierId.startsWith("m")
                category == DiscoverCategory.SERIES -> it.tierId.startsWith("s")
                category == DiscoverCategory.ALL -> true
                else -> false
            }
            it.copy(
                category = category,
                tierId = if (tierStillValid) it.tierId else null,
                page = 1,
            )
        }
    }

    fun toggleDiscoverMood(mood: MoodCategory) {
        _discoverFilters.update { current ->
            val next = if (mood in current.moods) current.moods - mood else current.moods + mood
            current.copy(moods = next, page = 1)
        }
    }

    fun clearDiscoverMoods() {
        _discoverFilters.update { it.copy(moods = emptySet(), page = 1) }
    }

    fun updateDiscoverGenre(genre: String?) {
        _discoverFilters.update { it.copy(genre = genre, page = 1) }
    }

    fun updateDiscoverLanguageOrCountry(value: String?) {
        _discoverFilters.update { it.copy(languageOrCountry = value, page = 1) }
    }

    fun updateDiscoverMinRating(minRating: Int) {
        _discoverFilters.update { it.copy(minRating = minRating.coerceIn(0, 10), page = 1) }
    }

    fun updateDiscoverStatusFilter(status: StatusFilter) {
        _discoverFilters.update { it.copy(statusFilter = status, page = 1) }
    }

    fun updateDiscoverTier(tierId: String?) {
        _discoverFilters.update { it.copy(tierId = tierId, page = 1) }
    }

    fun updateDiscoverDecade(decade: Int?) {
        _discoverFilters.update { it.copy(decade = decade, page = 1) }
    }

    fun updateDiscoverSort(sort: SortOption) {
        _discoverFilters.update { it.copy(sortOption = sort, page = 1) }
    }

    fun updateDiscoverMode(mode: DiscoverMode) {
        _discoverFilters.update { it.copy(mode = mode) }
    }

    fun setDiscoverPage(page: Int) {
        _discoverFilters.update { it.copy(page = page.coerceAtLeast(1)) }
    }

    fun resetDiscoverFilters() {
        _discoverFilters.update { current ->
            DiscoverFilterState(mode = current.mode)
        }
    }

    fun pickRandomUnwatchedId(): String? {
        val pool = applyDiscoverFilters(enrichedItems.value, _discoverFilters.value)
            .filter { it.user.status != WatchStatus.WATCHED }
        if (pool.isEmpty()) {
            emitMessage("No unwatched titles match your current filters.")
            return null
        }
        return pool.random().item.id
    }

    // Library Mutators
    fun updateLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    fun updateLibrarySearch(query: String) {
        _librarySearchQuery.value = query
    }

    fun updateLibrarySort(sort: LibrarySortOption) {
        _librarySort.value = sort
    }

    fun toggleLibraryListMode() {
        _libraryListMode.update { !it }
    }

    // User Logging Actions
    fun toggleQuickWatched(itemId: String) {
        val current = enrichedById.value[itemId] ?: return
        val nextStatus = if (current.user.status == WatchStatus.WATCHED) {
            WatchStatus.UNWATCHED
        } else {
            WatchStatus.WATCHED
        }
        viewModelScope.launch {
            repository.saveUserEntry(current.user.copy(status = nextStatus))
            if (nextStatus == WatchStatus.WATCHED) {
                repository.addViewingLog(
                    itemId = itemId,
                    actionLabel = "Completed",
                    ratingAtLog = current.user.rating,
                    isRewatch = false,
                )
            }
            emitMessage(
                if (nextStatus == WatchStatus.WATCHED) {
                    "Marked \"${current.item.title}\" as watched."
                } else {
                    "Removed watched mark for \"${current.item.title}\"."
                },
            )
        }
    }

    fun setWatchStatus(itemId: String, status: WatchStatus) {
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        val changed = current.status != status
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(status = status))
            if (changed && status != WatchStatus.UNWATCHED) {
                repository.addViewingLog(
                    itemId = itemId,
                    actionLabel = status.label,
                    ratingAtLog = current.rating,
                    isRewatch = status == WatchStatus.REWATCHING,
                )
            }
        }
    }

    fun toggleFavorite(itemId: String) {
        val current = enrichedById.value[itemId] ?: return
        val nextFav = !current.user.isFavorite
        viewModelScope.launch {
            repository.saveUserEntry(current.user.copy(isFavorite = nextFav))
            emitMessage(
                if (nextFav) "Added \"${current.item.title}\" to Favourites."
                else "Removed \"${current.item.title}\" from Favourites.",
            )
        }
    }

    fun logRewatch(itemId: String, note: String = "") {
        val current = enrichedById.value[itemId] ?: return
        val nextCount = current.user.rewatchCount + 1
        viewModelScope.launch {
            val nextStatus = if (current.user.status == WatchStatus.UNWATCHED || current.user.status == WatchStatus.BOOKMARKED) {
                WatchStatus.WATCHED
            } else {
                current.user.status
            }
            repository.saveUserEntry(current.user.copy(status = nextStatus, rewatchCount = nextCount))
            repository.addViewingLog(
                itemId = itemId,
                actionLabel = "Rewatch #$nextCount",
                ratingAtLog = current.user.rating,
                isRewatch = true,
                note = note,
            )
            emitMessage("Logged rewatch #$nextCount for \"${current.item.title}\".")
        }
    }

    fun deleteViewingLog(id: Long) {
        viewModelScope.launch {
            repository.deleteViewingLog(id)
            emitMessage("Removed viewing history entry.")
        }
    }

    fun setRating(itemId: String, rating: Int) {
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(rating = rating.coerceIn(0, 10)))
        }
    }

    fun setNotes(itemId: String, notes: String) {
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(notes = notes.take(CatalogRepository.MAX_NOTE_LENGTH)))
        }
    }

    fun setReview(itemId: String, reviewText: String) {
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(reviewText = reviewText.take(CatalogRepository.MAX_REVIEW_LENGTH)))
        }
    }

    fun setTags(itemId: String, tags: String) {
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(tags = tags.take(300)))
        }
    }

    fun setCustomPoster(itemId: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty() && !MetadataService.isValidHttpsUrl(trimmed)) {
            emitMessage("Please enter a valid HTTPS image URL.")
            return
        }
        val current = enrichedById.value[itemId]?.user ?: UserTitleState(itemId)
        viewModelScope.launch {
            repository.saveUserEntry(current.copy(customPosterUrl = trimmed))
            emitMessage(if (trimmed.isNotEmpty()) "Custom poster applied." else "Custom poster cleared.")
        }
    }

    // Custom Collections
    fun createCollection(name: String, description: String) {
        if (name.isBlank()) {
            emitMessage("Please provide a collection name.")
            return
        }
        viewModelScope.launch {
            repository.createCollection(name, description)
            emitMessage("Created collection \"${name.trim()}\".")
        }
    }

    fun duplicateCollection(collection: CustomCollectionWithItems) {
        viewModelScope.launch {
            repository.duplicateCollection(collection)
            emitMessage("Duplicated \"${collection.name}\".")
        }
    }

    fun renameCollection(id: Long, name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameCollection(id, name, description)
            emitMessage("Updated collection.")
        }
    }

    fun deleteCollection(id: Long) {
        viewModelScope.launch {
            repository.deleteCollection(id)
            emitMessage("Collection deleted.")
        }
    }

    fun toggleItemInCollection(collectionId: Long, itemId: String) {
        val collection = customCollections.value.firstOrNull { it.id == collectionId } ?: return
        val exists = itemId in collection.itemIds
        viewModelScope.launch {
            repository.toggleItemInCollection(collectionId, itemId, exists)
            emitMessage(
                if (exists) "Removed from \"${collection.name}\"."
                else "Added to \"${collection.name}\".",
            )
        }
    }

    fun updateProfile(displayName: String, tagline: String, defaultMode: DiscoverMode) {
        viewModelScope.launch {
            repository.saveProfile(displayName, tagline, defaultMode)
            emitMessage("Archivist profile saved.")
        }
    }

    // Backup & Export
    fun generateJsonBackupPayload(): String = repository.buildJsonBackup(userEntriesMap.value)

    fun generateCsvExportPayload(): String = repository.buildCsvExport(_catalog.value, userEntriesMap.value)

    fun stageJsonImport(rawContent: String) {
        val validIds = _catalog.value.mapTo(HashSet()) { it.id }
        val preview = repository.parseAndValidateJsonBackup(rawContent, validIds)
        if (preview == null || preview.validCount == 0) {
            emitMessage("Invalid or empty backup file. Existing progress unchanged.")
            _importPreview.value = null
        } else {
            _importPreview.value = preview
        }
    }

    fun cancelJsonImport() {
        _importPreview.value = null
    }

    fun confirmJsonImport(replaceAll: Boolean) {
        val preview = _importPreview.value ?: return
        viewModelScope.launch {
            previousEntriesSnapshot = repository.snapshotUserEntries()
            _canUndoBackupAction.value = true
            repository.applyImportedEntries(preview.entries, replaceAll = replaceAll)
            _importPreview.value = null
            emitMessage(
                if (replaceAll) "Backup replaced existing progress (${preview.validCount} titles)."
                else "Backup merged (${preview.validCount} titles).",
            )
        }
    }

    fun clearAllLocalProgress() {
        viewModelScope.launch {
            previousEntriesSnapshot = repository.snapshotUserEntries()
            _canUndoBackupAction.value = true
            repository.clearAllUserEntries()
            emitMessage("All local progress cleared. Tap Undo to restore.")
        }
    }

    fun undoLastBackupOrClear() {
        val snap = previousEntriesSnapshot ?: return
        viewModelScope.launch {
            repository.restoreUserEntries(snap)
            previousEntriesSnapshot = null
            _canUndoBackupAction.value = false
            emitMessage("Previous viewing state restored.")
        }
    }

    fun notifyMessage(message: String) {
        emitMessage(message)
    }

    private fun emitMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun computeRecommendations(all: List<EnrichedCatalogItem>): List<RecommendationItem> {
        val favorites = all
            .filter { it.user.isFavorite || it.user.rating >= 7 || it.user.status == WatchStatus.WATCHED }
            .sortedWith(
                compareByDescending<EnrichedCatalogItem> { it.user.isFavorite }
                    .thenByDescending { it.user.rating },
            )
            .take(5)
        if (favorites.isEmpty()) return emptyList()

        val watchedIds = all
            .filter { it.user.status == WatchStatus.WATCHED || it.user.status == WatchStatus.DROPPED }
            .mapTo(HashSet()) { it.item.id }
        val primaryFav = favorites.first()
        val favGenres = favorites.flatMap { f ->
            f.effectiveGenre.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        }.toSet()
        val favMoods = favorites.flatMap { it.effectiveMoods }.toSet()

        return all.asSequence()
            .filter { it.item.id !in watchedIds }
            .map { cand ->
                val cGenres = cand.effectiveGenre.split(",").map { it.trim().lowercase() }.toSet()
                val genreOverlap = cGenres intersect favGenres
                val moodOverlap = cand.effectiveMoods intersect favMoods
                val score = genreOverlap.size * 5 + moodOverlap.size * 3 +
                    (if (cand.item.type == primaryFav.item.type) 2 else 0)
                Triple(cand, score, moodOverlap.firstOrNull()?.label ?: genreOverlap.firstOrNull())
            }
            .filter { it.second >= 4 }
            .sortedWith(compareByDescending<Triple<EnrichedCatalogItem, Int, String?>> { it.second }.thenBy { it.first.item.rank })
            .take(8)
            .map { (cand, _, reasonToken) ->
                val reason = when {
                    primaryFav.user.isFavorite -> "Because ${primaryFav.item.title} is in your Favourites"
                    primaryFav.user.rating >= 7 -> "Because you rated ${primaryFav.item.title} ${primaryFav.user.rating}/10"
                    !reasonToken.isNullOrBlank() -> "Matches your ${reasonToken.replaceFirstChar { it.uppercase() }} taste"
                    else -> "Similar to ${primaryFav.item.title}"
                }
                RecommendationItem(cand, reason)
            }
            .toList()
    }

    private fun filterBySearch(
        items: List<EnrichedCatalogItem>,
        query: String,
    ): List<EnrichedCatalogItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        val rankMatch = Regex("^#(\\d+)$").matchEntire(q)
        val rankQuery = rankMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val exactMatches = items.filter { enriched ->
            val item = enriched.item
            if (rankQuery != null) {
                item.rank == rankQuery
            } else {
                item.title.lowercase().contains(q) ||
                    item.year.toString().contains(q) ||
                    "#${item.rank}".contains(q) ||
                    enriched.effectiveGenre.lowercase().contains(q) ||
                    enriched.effectiveDirector.lowercase().contains(q) ||
                    enriched.effectiveCountry.lowercase().contains(q) ||
                    enriched.effectiveLanguage.lowercase().contains(q) ||
                    enriched.user.tags.lowercase().contains(q) ||
                    enriched.effectiveMoods.any { it.label.lowercase().contains(q) }
            }
        }
        if (exactMatches.isNotEmpty() || q.length < 4 || rankQuery != null) return exactMatches

        // Typo-tolerant fallback using edit distance on title words
        return items.filter { enriched ->
            val words = enriched.item.title.lowercase().split(Regex("\\s+"))
            val fullNorm = enriched.item.title.lowercase()
            levenshteinWithin(q, fullNorm, maxDist = 2) ||
                words.any { word -> word.length >= 4 && levenshteinWithin(q, word, maxDist = 2) }
        }
    }

    private fun levenshteinWithin(a: String, b: String, maxDist: Int): Boolean {
        if (abs(a.length - b.length) > maxDist) return false
        val m = a.length
        val n = b.length
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDist) return false
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[n] <= maxDist
    }

    private fun applyDiscoverFilters(
        items: List<EnrichedCatalogItem>,
        filters: DiscoverFilterState,
    ): List<EnrichedCatalogItem> {
        val searched = filterBySearch(items, filters.searchQuery)
        val filtered = searched.filter { enriched ->
            val item = enriched.item
            val user = enriched.user

            val categoryMatch = when (filters.category) {
                DiscoverCategory.ALL -> true
                DiscoverCategory.MOVIES -> item.type == MediaType.MOVIE
                DiscoverCategory.SERIES -> item.type == MediaType.SERIES
                DiscoverCategory.ANIME -> item.isAnime
                DiscoverCategory.DOCUMENTARIES -> item.isDocumentary
            }
            if (!categoryMatch) return@filter false

            if (filters.moods.isNotEmpty() && !filters.moods.all { it in enriched.effectiveMoods }) {
                return@filter false
            }

            if (filters.genre != null) {
                if (!enriched.effectiveGenre.contains(filters.genre, ignoreCase = true)) {
                    return@filter false
                }
            }

            if (filters.languageOrCountry != null) {
                val needle = filters.languageOrCountry.lowercase()
                val matchesOrigin = enriched.effectiveLanguage.lowercase().contains(needle) ||
                    enriched.effectiveCountry.lowercase().contains(needle) ||
                    (needle == "japanese" && item.isAnime)
                if (!matchesOrigin) return@filter false
            }

            if (filters.minRating > 0 && user.rating < filters.minRating) return@filter false

            if (filters.tierId != null && item.tier.id != filters.tierId) return@filter false

            if (filters.decade != null) {
                val dec = filters.decade
                if (dec == 1930) {
                    if (item.year >= 1940) return@filter false
                } else {
                    if (item.year < dec || item.year >= dec + 10) return@filter false
                }
            }

            when (filters.statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.UNWATCHED -> user.status == WatchStatus.UNWATCHED
                StatusFilter.WATCHING -> user.status == WatchStatus.WATCHING
                StatusFilter.REWATCHING -> user.status == WatchStatus.REWATCHING
                StatusFilter.BOOKMARKED -> user.status == WatchStatus.BOOKMARKED
                StatusFilter.WATCHED -> user.status == WatchStatus.WATCHED
                StatusFilter.ON_HOLD -> user.status == WatchStatus.ON_HOLD
                StatusFilter.DROPPED -> user.status == WatchStatus.DROPPED
                StatusFilter.FAVORITES -> user.isFavorite
                StatusFilter.NOT_COMPLETED -> user.status != WatchStatus.WATCHED
                StatusFilter.RATED -> user.rating > 0
            }
        }

        return when (filters.sortOption) {
            SortOption.RANK_ASC -> filtered.sortedWith(compareBy<EnrichedCatalogItem> { it.item.rank }.thenBy { it.item.type.ordinal })
            SortOption.RANK_DESC -> filtered.sortedWith(compareByDescending<EnrichedCatalogItem> { it.item.rank }.thenBy { it.item.type.ordinal })
            SortOption.YEAR_DESC -> filtered.sortedWith(compareByDescending<EnrichedCatalogItem> { it.item.year }.thenBy { it.item.rank })
            SortOption.YEAR_ASC -> filtered.sortedWith(compareBy<EnrichedCatalogItem> { it.item.year }.thenBy { it.item.rank })
            SortOption.TITLE_ASC -> filtered.sortedBy { it.item.title.lowercase() }
            SortOption.RATING_DESC -> filtered.sortedWith(compareByDescending<EnrichedCatalogItem> { it.user.rating }.thenBy { it.item.rank })
        }
    }

    private fun computeStats(
        items: List<EnrichedCatalogItem>,
        episodesWatched: Int,
    ): ArchiveStats {
        if (items.isEmpty()) return ArchiveStats()
        var watchedMovies = 0
        var watchedSeries = 0
        var watchedAnime = 0
        var watchedDocs = 0
        var watching = 0
        var rewatching = 0
        var bookmarked = 0
        var onHold = 0
        var dropped = 0
        var favorites = 0
        var reviews = 0
        var totalRewatches = 0
        var ratedCount = 0
        var ratingSum = 0

        val tierWatched = mutableMapOf<String, Int>()
        val ratingDist = mutableMapOf<Int, Int>()
        val genreCounts = mutableMapOf<String, Int>()
        val moodCounts = mutableMapOf<MoodCategory, Int>()
        val decadeCounts = mutableMapOf<String, Int>()

        items.forEach { enriched ->
            val item = enriched.item
            val u = enriched.user
            when (u.status) {
                WatchStatus.WATCHED -> {
                    if (item.type == MediaType.MOVIE) watchedMovies++ else watchedSeries++
                    if (item.isAnime) watchedAnime++
                    if (item.isDocumentary) watchedDocs++
                    tierWatched[item.tier.id] = (tierWatched[item.tier.id] ?: 0) + 1

                    enriched.effectiveGenre.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() && it != "Film" && it != "Series" }
                        .forEach { g -> genreCounts[g] = (genreCounts[g] ?: 0) + 1 }

                    enriched.effectiveMoods.forEach { m -> moodCounts[m] = (moodCounts[m] ?: 0) + 1 }

                    val dec = (item.year / 10) * 10
                    val decLabel = if (dec < 1940) "1930s & Earlier" else "${dec}s"
                    decadeCounts[decLabel] = (decadeCounts[decLabel] ?: 0) + 1
                }
                WatchStatus.WATCHING -> watching++
                WatchStatus.REWATCHING -> rewatching++
                WatchStatus.BOOKMARKED -> bookmarked++
                WatchStatus.ON_HOLD -> onHold++
                WatchStatus.DROPPED -> dropped++
                WatchStatus.UNWATCHED -> Unit
            }
            if (u.isFavorite) favorites++
            if (u.reviewText.isNotBlank()) reviews++
            totalRewatches += u.rewatchCount
            if (u.rating > 0) {
                ratedCount++
                ratingSum += u.rating
                ratingDist[u.rating] = (ratingDist[u.rating] ?: 0) + 1
            }
        }

        val totalMovies = items.count { it.item.type == MediaType.MOVIE }
        val totalSeries = items.count { it.item.type == MediaType.SERIES }
        return ArchiveStats(
            totalTitles = items.size,
            totalMovies = totalMovies,
            totalSeries = totalSeries,
            watchedMovies = watchedMovies,
            watchedSeries = watchedSeries,
            watchedAnime = watchedAnime,
            watchedDocs = watchedDocs,
            watchingCount = watching,
            rewatchingCount = rewatching,
            bookmarkedCount = bookmarked,
            onHoldCount = onHold,
            droppedCount = dropped,
            favoritesCount = favorites,
            reviewsCount = reviews,
            totalRewatches = totalRewatches,
            totalEpisodesWatched = episodesWatched,
            ratedCount = ratedCount,
            averageRating = if (ratedCount > 0) ratingSum.toFloat() / ratedCount else 0f,
            ratingDistribution = ratingDist,
            topGenres = genreCounts.entries
                .sortedByDescending { it.value }
                .take(6)
                .map { NamedCount(it.key, it.value) },
            topMoods = moodCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { NamedCount(it.key.label, it.value) },
            topDecades = decadeCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { NamedCount(it.key, it.value) },
            tierProgress = ArchiveTiers.all.map { tier ->
                TierProgress(tier = tier, watchedCount = tierWatched[tier.id] ?: 0)
            },
        )
    }

    companion object {
        const val PAGE_SIZE = 60
        val FILTER_GENRES = listOf(
            "Drama", "Crime", "Sci-Fi", "Thriller", "Comedy", "Action",
            "Adventure", "Animation", "Romance", "Horror", "War", "Documentary",
            "Mystery", "Biography", "Fantasy",
        )
        val FILTER_ORIGINS = listOf(
            "English", "Hindi", "Japanese", "Korean", "French",
            "Italian", "Spanish", "German", "India", "Japan", "South Korea", "USA", "UK",
        )
        private val SPOTLIGHT_IDS = listOf("m_23", "m_1", "s_1", "m_12", "m_13", "m_5")
        private val FEATURED_REEL_IDS = listOf(
            "m_1", "m_4", "m_12", "m_13", "m_16", "m_33",
            "m_44", "s_1", "s_2", "s_4", "m_146", "m_156",
        )
    }
}
