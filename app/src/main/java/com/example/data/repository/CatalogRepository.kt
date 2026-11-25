package com.example.data.repository

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.example.data.local.CachedEpisodeEntity
import com.example.data.local.CachedMetadataEntity
import com.example.data.local.CollectionItemCrossRef
import com.example.data.local.CustomCollectionEntity
import com.example.data.local.LocalProfileEntity
import com.example.data.local.MuvantaDao
import com.example.data.local.PendingSyncOpEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.UserTitleEntryEntity
import com.example.data.local.ViewingLogEntity
import com.example.data.local.WatchedEpisodeEntity
import com.example.data.remote.MetadataService
import com.example.data.sync.MuvantaSyncEngine
import com.example.model.ArchiveTiers
import com.example.model.CatalogItem
import com.example.model.CuratedBaseline
import com.example.model.CustomCollectionWithItems
import com.example.model.DiscoverMode
import com.example.model.ImportPreview
import com.example.model.MediaType
import com.example.model.ResolvedMetadata
import com.example.model.SeriesEpisode
import com.example.model.UserTitleState
import com.example.model.ViewingLogEntry
import com.example.model.WatchStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CatalogRepository(
    private val context: Context,
    private val dao: MuvantaDao,
    private val metadataService: MetadataService,
    private val currentOwnerIdProvider: () -> String = { MuvantaSyncEngine.OWNER_LOCAL },
    private val isOnlineProvider: () -> Boolean = { true },
    private val onLocalMutationQueued: () -> Unit = {},
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetchSemaphore = Semaphore(MAX_CONCURRENT_FETCHES)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val episodesInFlight = ConcurrentHashMap.newKeySet<String>()

    val userEntriesFlow: Flow<Map<String, UserTitleState>> =
        dao.observeAllUserEntries().map { list ->
            list.associate { entity ->
                entity.itemId to UserTitleState(
                    itemId = entity.itemId,
                    status = WatchStatus.fromWire(entity.status),
                    rating = entity.rating.coerceIn(0, 10),
                    notes = entity.notes,
                    reviewText = entity.reviewText,
                    tags = entity.tags,
                    isFavorite = entity.isFavorite,
                    rewatchCount = entity.rewatchCount.coerceAtLeast(0),
                    customPosterUrl = entity.customPosterUrl,
                    updatedAt = entity.updatedAt,
                    syncVersion = entity.syncVersion,
                    isDeleted = entity.isDeleted,
                )
            }
        }

    val metadataCacheFlow: Flow<Map<String, ResolvedMetadata>> =
        dao.observeAllMetadata().map { list ->
            list.associate { entity ->
                entity.itemId to ResolvedMetadata(
                    posterUrl = entity.posterUrl,
                    description = entity.description,
                    genre = entity.genre,
                    runtime = entity.runtime,
                    network = entity.network,
                    wikiUrl = entity.wikiUrl,
                    tvmazeShowId = entity.tvmazeShowId,
                    fetched = entity.fetched,
                    failedAt = entity.failedAt,
                )
            }
        }

    val cachedPosterCountFlow: Flow<Int> = dao.observeCachedPosterCount()

    val collectionsFlow: Flow<List<CustomCollectionWithItems>> =
        combine(dao.observeCollections(), dao.observeCollectionItems()) { collections, refs ->
            val grouped = refs.groupBy { it.collectionId }
            collections.map { c ->
                CustomCollectionWithItems(
                    id = c.id,
                    name = c.name,
                    description = c.description,
                    itemIds = grouped[c.id].orEmpty().map { it.itemId },
                )
            }
        }

    val profileFlow: Flow<LocalProfileEntity> =
        dao.observeProfile().map { existing ->
            existing ?: LocalProfileEntity(
                id = 1,
                displayName = "Cinema Archivist",
                tagline = "Exploring 1,000 films and 300 television series",
                defaultDiscoverMode = DiscoverMode.GRID.wire,
            )
        }

    val searchHistoryFlow: Flow<List<String>> = dao.observeSearchHistory()

    val totalWatchedEpisodesFlow: Flow<Int> = dao.observeTotalWatchedEpisodesCount()

    fun observeViewingHistoryForItem(itemId: String): Flow<List<ViewingLogEntry>> =
        dao.observeViewingHistoryForItem(itemId).map { list ->
            list.map { e ->
                ViewingLogEntry(
                    id = e.id,
                    itemId = e.itemId,
                    loggedAt = e.loggedAt,
                    actionLabel = e.actionLabel,
                    ratingAtLog = e.ratingAtLog,
                    isRewatch = e.isRewatch,
                    note = e.note,
                )
            }
        }

    fun observeSeriesEpisodes(itemId: String): Flow<List<SeriesEpisode>> =
        combine(
            dao.observeEpisodesForSeries(itemId),
            dao.observeWatchedEpisodesForSeries(itemId),
        ) { cached, watched ->
            val watchedSet = watched.mapTo(HashSet()) { it.season to it.episode }
            cached.map { ep ->
                SeriesEpisode(
                    itemId = ep.itemId,
                    season = ep.season,
                    episode = ep.episode,
                    title = ep.title,
                    airDate = ep.airDate,
                    runtimeMinutes = ep.runtimeMinutes,
                    summary = ep.summary,
                    watched = (ep.season to ep.episode) in watchedSet,
                )
            }
        }

    suspend fun loadBundledCatalog(): List<CatalogItem> = withContext(Dispatchers.IO) {
        val raw = context.assets.open("muvanta_catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)

        val animeIds = buildSet {
            val arr = root.optJSONArray("animeIds")
            if (arr != null) {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        }
        val docIds = buildSet {
            val arr = root.optJSONArray("docIds")
            if (arr != null) {
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
        }

        val curatedMap = mutableMapOf<String, CuratedBaseline>()
        val curatedObj = root.optJSONObject("curated")
        if (curatedObj != null) {
            val keys = curatedObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = curatedObj.optJSONObject(id) ?: continue
                curatedMap[id] = CuratedBaseline(
                    director = obj.optString("dir", ""),
                    country = obj.optString("country", ""),
                    language = obj.optString("lang", ""),
                    genre = obj.optString("genre", ""),
                    runtime = obj.optString("runtime", ""),
                    description = obj.optString("desc", ""),
                )
            }
        }

        val items = ArrayList<CatalogItem>(1300)

        val moviesArr = root.optJSONArray("movies")
        if (moviesArr != null) {
            for (i in 0 until moviesArr.length()) {
                val row = moviesArr.optJSONArray(i) ?: continue
                val rank = row.optInt(0)
                val title = row.optString(1)
                val year = row.optInt(2)
                val id = "m_$rank"
                items.add(
                    CatalogItem(
                        id = id,
                        type = MediaType.MOVIE,
                        rank = rank,
                        title = title,
                        year = year,
                        tier = ArchiveTiers.findFor(MediaType.MOVIE, rank),
                        isAnime = id in animeIds,
                        isDocumentary = id in docIds,
                        curated = curatedMap[id] ?: CuratedBaseline(),
                    ),
                )
            }
        }

        val seriesArr = root.optJSONArray("series")
        if (seriesArr != null) {
            for (i in 0 until seriesArr.length()) {
                val row = seriesArr.optJSONArray(i) ?: continue
                val rank = row.optInt(0)
                val title = row.optString(1)
                val year = row.optInt(2)
                val id = "s_$rank"
                items.add(
                    CatalogItem(
                        id = id,
                        type = MediaType.SERIES,
                        rank = rank,
                        title = title,
                        year = year,
                        tier = ArchiveTiers.findFor(MediaType.SERIES, rank),
                        isAnime = id in animeIds,
                        isDocumentary = id in docIds,
                        curated = curatedMap[id] ?: CuratedBaseline(),
                    ),
                )
            }
        }

        items
    }

    fun requestMetadata(item: CatalogItem, forceRefresh: Boolean = false) {
        if (!isOnlineProvider()) return
        if (!inFlight.add(item.id)) return
        ioScope.launch {
            try {
                if (!forceRefresh) {
                    val existing = dao.getMetadata(item.id)
                    if (existing != null) {
                        val hasPoster = existing.posterUrl.isNotBlank()
                        val inCooldown = existing.failedAt > 0L &&
                            (System.currentTimeMillis() - existing.failedAt) < RETRY_COOLDOWN_MS
                        if (hasPoster || inCooldown) return@launch
                    }
                } else {
                    dao.deleteMetadata(item.id)
                }

                fetchSemaphore.withPermit {
                    val fetched = metadataService.fetchMetadata(item)
                    dao.upsertMetadata(
                        CachedMetadataEntity(
                            itemId = item.id,
                            posterUrl = fetched.posterUrl,
                            description = fetched.description,
                            genre = fetched.genre,
                            runtime = fetched.runtime,
                            network = fetched.network,
                            wikiUrl = fetched.wikiUrl,
                            tvmazeShowId = fetched.tvmazeShowId,
                            fetched = fetched.fetched,
                            failedAt = if (fetched.posterUrl.isBlank()) System.currentTimeMillis() else 0L,
                            cachedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                inFlight.remove(item.id)
            }
        }
    }

    fun ensureSeriesEpisodes(item: CatalogItem, knownShowId: Int, forceRefresh: Boolean = false) {
        if (item.type != MediaType.SERIES) return
        if (!isOnlineProvider()) return
        if (!episodesInFlight.add(item.id)) return
        ioScope.launch {
            try {
                if (!forceRefresh && dao.countCachedEpisodes(item.id) > 0) return@launch
                val (resolvedShowId, episodes) = metadataService.fetchSeriesEpisodes(item, knownShowId)
                if (episodes.isNotEmpty()) {
                    dao.upsertCachedEpisodes(
                        episodes.map { ep ->
                            CachedEpisodeEntity(
                                itemId = ep.itemId,
                                season = ep.season,
                                episode = ep.episode,
                                title = ep.title,
                                airDate = ep.airDate,
                                runtimeMinutes = ep.runtimeMinutes,
                                summary = ep.summary,
                            )
                        },
                    )
                }
                if (resolvedShowId > 0) {
                    val existingMeta = dao.getMetadata(item.id)
                    if (existingMeta != null && existingMeta.tvmazeShowId == 0) {
                        dao.upsertMetadata(existingMeta.copy(tvmazeShowId = resolvedShowId))
                    }
                }
            } finally {
                episodesInFlight.remove(item.id)
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearArtworkCache() = withContext(Dispatchers.IO) {
        dao.clearCachedPosterUrls()
        runCatching {
            val loader = Coil.imageLoader(context.applicationContext)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }

    suspend fun clearAllUserAccountData() = withContext(Dispatchers.IO) {
        dao.clearAllUserAccountData()
    }

    suspend fun countPendingOps(): Int = withContext(Dispatchers.IO) {
        dao.countPendingOps()
    }

    suspend fun toggleEpisodeWatched(itemId: String, season: Int, episode: Int, currentlyWatched: Boolean) {
        val now = System.currentTimeMillis()
        val key = "$itemId:$season:$episode"
        if (currentlyWatched) {
            dao.unmarkEpisodeWatched(itemId, season, episode)
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_EPISODE,
                entityKey = key,
                operation = MuvantaSyncEngine.OP_DELETE,
                payload = JSONObject()
                    .put("itemId", itemId)
                    .put("season", season)
                    .put("episode", episode),
                baseVersion = 0,
            )
        } else {
            dao.markEpisodeWatched(
                WatchedEpisodeEntity(
                    itemId = itemId,
                    season = season,
                    episode = episode,
                    watchedAt = now,
                ),
            )
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_EPISODE,
                entityKey = key,
                operation = MuvantaSyncEngine.OP_UPSERT,
                payload = JSONObject()
                    .put("itemId", itemId)
                    .put("season", season)
                    .put("episode", episode)
                    .put("watchedAt", now),
                baseVersion = 0,
            )
        }
    }

    suspend fun setSeasonWatched(itemId: String, season: Int, episodes: List<SeriesEpisode>, watched: Boolean) {
        val now = System.currentTimeMillis()
        if (watched) {
            dao.markEpisodesWatched(
                episodes.map { ep ->
                    WatchedEpisodeEntity(
                        itemId = itemId,
                        season = season,
                        episode = ep.episode,
                        watchedAt = now,
                    )
                },
            )
            episodes.forEach { ep ->
                enqueueOp(
                    entityType = MuvantaSyncEngine.ENTITY_EPISODE,
                    entityKey = "$itemId:$season:${ep.episode}",
                    operation = MuvantaSyncEngine.OP_UPSERT,
                    payload = JSONObject()
                        .put("itemId", itemId)
                        .put("season", season)
                        .put("episode", ep.episode)
                        .put("watchedAt", now),
                    baseVersion = 0,
                    triggerNotify = false,
                )
            }
        } else {
            dao.unmarkSeasonWatched(itemId, season)
            episodes.forEach { ep ->
                enqueueOp(
                    entityType = MuvantaSyncEngine.ENTITY_EPISODE,
                    entityKey = "$itemId:$season:${ep.episode}",
                    operation = MuvantaSyncEngine.OP_DELETE,
                    payload = JSONObject()
                        .put("itemId", itemId)
                        .put("season", season)
                        .put("episode", ep.episode),
                    baseVersion = 0,
                    triggerNotify = false,
                )
            }
        }
        onLocalMutationQueued()
    }

    suspend fun addViewingLog(
        itemId: String,
        actionLabel: String,
        ratingAtLog: Int,
        isRewatch: Boolean,
        note: String = "",
    ) {
        val now = System.currentTimeMillis()
        val logKey = UUID.randomUUID().toString()
        val cleanNote = note.trim().take(400)
        val cleanRating = ratingAtLog.coerceIn(0, 10)
        dao.insertViewingLog(
            ViewingLogEntity(
                itemId = itemId,
                loggedAt = now,
                actionLabel = actionLabel,
                ratingAtLog = cleanRating,
                isRewatch = isRewatch,
                note = cleanNote,
                clientLogKey = logKey,
            ),
        )
        enqueueOp(
            entityType = MuvantaSyncEngine.ENTITY_VIEWING_LOG,
            entityKey = logKey,
            operation = MuvantaSyncEngine.OP_UPSERT,
            payload = JSONObject()
                .put("clientLogKey", logKey)
                .put("itemId", itemId)
                .put("loggedAt", now)
                .put("actionLabel", actionLabel)
                .put("ratingAtLog", cleanRating)
                .put("isRewatch", isRewatch)
                .put("note", cleanNote),
            baseVersion = 0,
        )
    }

    suspend fun deleteViewingLog(id: Long) {
        val existing = dao.getViewingLogById(id)
        dao.deleteViewingLog(id)
        if (existing != null && existing.clientLogKey.isNotBlank()) {
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_VIEWING_LOG,
                entityKey = existing.clientLogKey,
                operation = MuvantaSyncEngine.OP_DELETE,
                payload = JSONObject().put("clientLogKey", existing.clientLogKey),
                baseVersion = 0,
            )
        }
    }

    suspend fun recordSearchQuery(query: String) {
        val clean = query.trim().take(80)
        if (clean.length < 2) return
        dao.upsertSearchHistory(SearchHistoryEntity(query = clean, searchedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSearchHistoryItem(query: String) {
        dao.deleteSearchHistoryItem(query)
    }

    suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }

    suspend fun saveUserEntry(state: UserTitleState) {
        val cleanNotes = state.notes.take(MAX_NOTE_LENGTH)
        val cleanReview = state.reviewText.take(MAX_REVIEW_LENGTH)
        val cleanTags = state.tags.take(300)
        val cleanPoster = state.customPosterUrl.trim().takeIf { MetadataService.isValidHttpsUrl(it) }.orEmpty()
        val cleanRating = state.rating.coerceIn(0, 10)
        val cleanRewatches = state.rewatchCount.coerceAtLeast(0)
        val isEmptyEntry = state.status == WatchStatus.UNWATCHED &&
            cleanRating == 0 &&
            cleanNotes.isBlank() &&
            cleanReview.isBlank() &&
            cleanTags.isBlank() &&
            !state.isFavorite &&
            cleanRewatches == 0 &&
            cleanPoster.isBlank()

        val existing = dao.getUserEntryById(state.itemId)
        val preservedVersion = existing?.syncVersion ?: state.syncVersion
        val now = System.currentTimeMillis()

        if (isEmptyEntry) {
            if (preservedVersion > 0) {
                // Keep a deletion tombstone so cloud & other devices honor the deletion
                dao.upsertUserEntry(
                    UserTitleEntryEntity(
                        itemId = state.itemId,
                        status = WatchStatus.UNWATCHED.wire,
                        rating = 0,
                        notes = "",
                        reviewText = "",
                        tags = "",
                        isFavorite = false,
                        rewatchCount = 0,
                        customPosterUrl = "",
                        updatedAt = now,
                        syncVersion = preservedVersion,
                        isDeleted = true,
                    ),
                )
                enqueueOp(
                    entityType = MuvantaSyncEngine.ENTITY_TITLE_ENTRY,
                    entityKey = state.itemId,
                    operation = MuvantaSyncEngine.OP_DELETE,
                    payload = JSONObject().put("itemId", state.itemId),
                    baseVersion = preservedVersion,
                )
            } else {
                dao.deleteUserEntry(state.itemId)
                dao.deletePendingOpsForEntity(MuvantaSyncEngine.ENTITY_TITLE_ENTRY, state.itemId)
            }
        } else {
            dao.upsertUserEntry(
                UserTitleEntryEntity(
                    itemId = state.itemId,
                    status = state.status.wire,
                    rating = cleanRating,
                    notes = cleanNotes,
                    reviewText = cleanReview,
                    tags = cleanTags,
                    isFavorite = state.isFavorite,
                    rewatchCount = cleanRewatches,
                    customPosterUrl = cleanPoster,
                    updatedAt = now,
                    syncVersion = preservedVersion,
                    isDeleted = false,
                ),
            )
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_TITLE_ENTRY,
                entityKey = state.itemId,
                operation = MuvantaSyncEngine.OP_UPSERT,
                payload = JSONObject().put("itemId", state.itemId),
                baseVersion = preservedVersion,
            )
        }
    }

    suspend fun snapshotUserEntries(): List<UserTitleEntryEntity> = dao.getAllUserEntriesSnapshot()

    suspend fun restoreUserEntries(snapshot: List<UserTitleEntryEntity>) {
        dao.replaceAllUserEntries(snapshot)
        snapshot.forEach { entity ->
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_TITLE_ENTRY,
                entityKey = entity.itemId,
                operation = MuvantaSyncEngine.OP_UPSERT,
                payload = JSONObject().put("itemId", entity.itemId),
                baseVersion = entity.syncVersion,
                triggerNotify = false,
            )
        }
        onLocalMutationQueued()
    }

    suspend fun clearAllUserEntries() {
        val current = dao.getAllUserEntriesSnapshot()
        val now = System.currentTimeMillis()
        current.forEach { entry ->
            if (entry.syncVersion > 0) {
                dao.upsertUserEntry(
                    entry.copy(
                        status = WatchStatus.UNWATCHED.wire,
                        rating = 0,
                        notes = "",
                        reviewText = "",
                        tags = "",
                        isFavorite = false,
                        rewatchCount = 0,
                        customPosterUrl = "",
                        isDeleted = true,
                        updatedAt = now,
                    ),
                )
                enqueueOp(
                    entityType = MuvantaSyncEngine.ENTITY_TITLE_ENTRY,
                    entityKey = entry.itemId,
                    operation = MuvantaSyncEngine.OP_DELETE,
                    payload = JSONObject().put("itemId", entry.itemId),
                    baseVersion = entry.syncVersion,
                    triggerNotify = false,
                )
            } else {
                dao.deleteUserEntry(entry.itemId)
                dao.deletePendingOpsForEntity(MuvantaSyncEngine.ENTITY_TITLE_ENTRY, entry.itemId)
            }
        }
        onLocalMutationQueued()
    }

    suspend fun applyImportedEntries(entries: Map<String, UserTitleState>, replaceAll: Boolean) {
        val now = System.currentTimeMillis()
        val existingById = dao.getAllUserEntriesIncludingDeleted().associateBy { it.itemId }
        if (replaceAll) {
            clearAllUserEntries()
        }
        val entities = entries.values.map { s ->
            val priorVersion = existingById[s.itemId]?.syncVersion ?: 0
            UserTitleEntryEntity(
                itemId = s.itemId,
                status = s.status.wire,
                rating = s.rating.coerceIn(0, 10),
                notes = s.notes.take(MAX_NOTE_LENGTH),
                reviewText = s.reviewText.take(MAX_REVIEW_LENGTH),
                tags = s.tags.take(300),
                isFavorite = s.isFavorite,
                rewatchCount = s.rewatchCount.coerceAtLeast(0),
                customPosterUrl = s.customPosterUrl.takeIf { MetadataService.isValidHttpsUrl(it) }.orEmpty(),
                updatedAt = now,
                syncVersion = priorVersion,
                isDeleted = false,
            )
        }
        dao.upsertUserEntries(entities)
        entities.forEach { entity ->
            enqueueOp(
                entityType = MuvantaSyncEngine.ENTITY_TITLE_ENTRY,
                entityKey = entity.itemId,
                operation = MuvantaSyncEngine.OP_UPSERT,
                payload = JSONObject().put("itemId", entity.itemId),
                baseVersion = entity.syncVersion,
                triggerNotify = false,
            )
        }
        onLocalMutationQueued()
    }

    suspend fun createCollection(name: String, description: String): Long {
        val now = System.currentTimeMillis()
        val clientId = UUID.randomUUID().toString()
        val cleanName = name.trim().take(80)
        val cleanDesc = description.trim().take(240)
        val id = dao.insertCollection(
            CustomCollectionEntity(
                name = cleanName,
                description = cleanDesc,
                createdAt = now,
                clientId = clientId,
                updatedAt = now,
            ),
        )
        enqueueCollectionSync(clientId, cleanName, cleanDesc, emptyList(), now, now, isDeleted = false)
        return id
    }

    suspend fun duplicateCollection(source: CustomCollectionWithItems): Long {
        val now = System.currentTimeMillis()
        val clientId = UUID.randomUUID().toString()
        val copyName = "${source.name} (Copy)".take(80)
        val newId = dao.insertCollection(
            CustomCollectionEntity(
                name = copyName,
                description = source.description,
                createdAt = now,
                clientId = clientId,
                updatedAt = now,
            ),
        )
        if (source.itemIds.isNotEmpty()) {
            dao.addItemsToCollection(
                source.itemIds.map { itemId ->
                    CollectionItemCrossRef(collectionId = newId, itemId = itemId, addedAt = now)
                },
            )
        }
        enqueueCollectionSync(clientId, copyName, source.description, source.itemIds, now, now, isDeleted = false)
        return newId
    }

    suspend fun renameCollection(id: Long, name: String, description: String) {
        val now = System.currentTimeMillis()
        val cleanName = name.trim().take(80)
        val cleanDesc = description.trim().take(240)
        val existing = dao.getCollectionById(id) ?: return
        val clientId = existing.clientId.ifBlank {
            UUID.randomUUID().toString().also { dao.updateCollectionClientId(id, it, now) }
        }
        dao.updateCollection(id, cleanName, cleanDesc, now)
        val items = dao.getCollectionItemIds(id)
        enqueueCollectionSync(clientId, cleanName, cleanDesc, items, existing.createdAt, now, isDeleted = false)
    }

    suspend fun deleteCollection(id: Long) {
        val existing = dao.getCollectionById(id)
        dao.deleteCollection(id)
        if (existing != null && existing.clientId.isNotBlank()) {
            val now = System.currentTimeMillis()
            enqueueCollectionSync(
                clientId = existing.clientId,
                name = existing.name,
                description = existing.description,
                itemIds = emptyList(),
                createdAt = existing.createdAt,
                updatedAt = now,
                isDeleted = true,
            )
        }
    }

    suspend fun toggleItemInCollection(collectionId: Long, itemId: String, currentlyIn: Boolean) {
        val now = System.currentTimeMillis()
        if (currentlyIn) {
            dao.removeItemFromCollection(collectionId, itemId)
        } else {
            dao.addItemToCollection(
                CollectionItemCrossRef(
                    collectionId = collectionId,
                    itemId = itemId,
                    addedAt = now,
                ),
            )
        }
        val col = dao.getCollectionById(collectionId) ?: return
        val clientId = col.clientId.ifBlank {
            UUID.randomUUID().toString().also { dao.updateCollectionClientId(collectionId, it, now) }
        }
        dao.updateCollection(collectionId, col.name, col.description, now)
        val items = dao.getCollectionItemIds(collectionId)
        enqueueCollectionSync(clientId, col.name, col.description, items, col.createdAt, now, isDeleted = false)
    }

    suspend fun saveProfile(displayName: String, tagline: String, defaultMode: DiscoverMode) {
        val cleanName = displayName.trim().ifBlank { "Cinema Archivist" }.take(60)
        val cleanTagline = tagline.trim().take(140)
        val now = System.currentTimeMillis()
        dao.upsertProfile(
            LocalProfileEntity(
                id = 1,
                displayName = cleanName,
                tagline = cleanTagline,
                defaultDiscoverMode = defaultMode.wire,
            ),
        )
        enqueueOp(
            entityType = MuvantaSyncEngine.ENTITY_PROFILE,
            entityKey = "profile",
            operation = MuvantaSyncEngine.OP_UPSERT,
            payload = JSONObject()
                .put("displayName", cleanName)
                .put("tagline", cleanTagline)
                .put("defaultDiscoverMode", defaultMode.wire)
                .put("updatedAt", now),
            baseVersion = 0,
        )
    }

    private suspend fun enqueueCollectionSync(
        clientId: String,
        name: String,
        description: String,
        itemIds: List<String>,
        createdAt: Long,
        updatedAt: Long,
        isDeleted: Boolean,
    ) {
        enqueueOp(
            entityType = MuvantaSyncEngine.ENTITY_COLLECTION,
            entityKey = clientId,
            operation = if (isDeleted) MuvantaSyncEngine.OP_DELETE else MuvantaSyncEngine.OP_UPSERT,
            payload = JSONObject()
                .put("clientId", clientId)
                .put("name", name)
                .put("description", description)
                .put("itemIds", JSONArray(itemIds))
                .put("createdAt", createdAt)
                .put("updatedAt", updatedAt),
            baseVersion = 0,
        )
    }

    private suspend fun enqueueOp(
        entityType: String,
        entityKey: String,
        operation: String,
        payload: JSONObject,
        baseVersion: Int,
        triggerNotify: Boolean = true,
    ) {
        // Coalesce repeated edits on the same entity while preserving baseVersion
        if (entityType != MuvantaSyncEngine.ENTITY_VIEWING_LOG) {
            dao.deletePendingOpsForEntity(entityType, entityKey)
        }
        dao.upsertPendingOp(
            PendingSyncOpEntity(
                opId = UUID.randomUUID().toString(),
                ownerUserId = currentOwnerIdProvider(),
                entityType = entityType,
                entityKey = entityKey,
                operation = operation,
                payloadJson = payload.toString(),
                baseVersion = baseVersion,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (triggerNotify) {
            onLocalMutationQueued()
        }
    }

    // Prototype-compatible + MUVANTA 2.0 enriched JSON backup format
    fun buildJsonBackup(entries: Map<String, UserTitleState>): String {
        val root = JSONObject()
        entries.entries.sortedBy { it.key }.forEach { (id, state) ->
            if (state.isLogged || state.customPosterUrl.isNotBlank()) {
                val obj = JSONObject().apply {
                    put("status", state.status.wire)
                    put("rating", state.rating)
                    put("notes", state.notes)
                    put("review", state.reviewText)
                    put("tags", state.tags)
                    put("favorite", state.isFavorite)
                    put("rewatches", state.rewatchCount)
                    put("customPoster", state.customPosterUrl)
                }
                root.put(id, obj)
            }
        }
        return root.toString(2)
    }

    fun parseAndValidateJsonBackup(rawJson: String, validCatalogIds: Set<String>): ImportPreview? {
        if (rawJson.toByteArray().size > MAX_IMPORT_BYTES) return null
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val clean = linkedMapOf<String, UserTitleState>()
        var skipped = 0
        val keys = root.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val entry = root.optJSONObject(id)
            if (id !in validCatalogIds || entry == null) {
                skipped++
                continue
            }
            val status = WatchStatus.fromWire(entry.optString("status", "unwatched"))
            val rating = entry.optInt("rating", 0).coerceIn(0, 10)
            val notes = entry.optString("notes", "").take(MAX_NOTE_LENGTH)
            val review = entry.optString("review", "").take(MAX_REVIEW_LENGTH)
            val tags = entry.optString("tags", "").take(300)
            val favorite = entry.optBoolean("favorite", false)
            val rewatches = entry.optInt("rewatches", 0).coerceAtLeast(0)
            val customPoster = entry.optString("customPoster", "").trim()
                .takeIf { MetadataService.isValidHttpsUrl(it) }.orEmpty()

            val state = UserTitleState(
                itemId = id,
                status = status,
                rating = rating,
                notes = notes,
                reviewText = review,
                tags = tags,
                isFavorite = favorite,
                rewatchCount = rewatches,
                customPosterUrl = customPoster,
            )
            if (state.isLogged || customPoster.isNotBlank()) {
                clean[id] = state
            }
        }
        return ImportPreview(entries = clean, validCount = clean.size, skippedCount = skipped)
    }

    fun buildCsvExport(catalog: List<CatalogItem>, entries: Map<String, UserTitleState>): String {
        val sb = StringBuilder()
        sb.appendLine("Type,Rank,Title,Year,Status,Rating,Favorite,Rewatches,Tags,Review,Notes,CustomPoster")
        catalog.forEach { item ->
            val u = entries[item.id] ?: return@forEach
            if (u.isLogged || u.customPosterUrl.isNotBlank()) {
                val row = listOf(
                    item.type.wire,
                    item.rank.toString(),
                    item.title,
                    item.year.toString(),
                    u.status.wire,
                    if (u.rating > 0) u.rating.toString() else "",
                    if (u.isFavorite) "yes" else "no",
                    u.rewatchCount.toString(),
                    u.tags,
                    u.reviewText,
                    u.notes,
                    u.customPosterUrl,
                )
                sb.appendLine(row.joinToString(",") { safeCsvCell(it) })
            }
        }
        return sb.toString()
    }

    private fun safeCsvCell(value: String): String {
        val prefixed = if (value.isNotEmpty() && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$value"
        } else {
            value
        }
        return "\"${prefixed.replace("\"", "\"\"")}\""
    }

    companion object {
        const val MAX_NOTE_LENGTH = 2000
        const val MAX_REVIEW_LENGTH = 4000
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        private const val MAX_CONCURRENT_FETCHES = 8
        private const val RETRY_COOLDOWN_MS = 60L * 1000L
    }
}
