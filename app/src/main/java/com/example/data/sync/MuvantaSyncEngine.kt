package com.example.data.sync

import com.example.data.local.CollectionItemCrossRef
import com.example.data.local.CustomCollectionEntity
import com.example.data.local.LocalProfileEntity
import com.example.data.local.MuvantaDao
import com.example.data.local.PendingSyncOpEntity
import com.example.data.local.SyncConflictEntity
import com.example.data.local.UserTitleEntryEntity
import com.example.data.local.ViewingLogEntity
import com.example.data.local.WatchedEpisodeEntity
import com.example.data.remote.AuthExpiredException
import com.example.data.remote.RemoteCollection
import com.example.data.remote.RemoteLibraryEntry
import com.example.data.remote.RemoteProfile
import com.example.data.remote.RemoteReview
import com.example.data.remote.RemoteViewingLog
import com.example.data.remote.RemoteWatchedEpisode
import com.example.data.remote.SupabaseAuthService
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabasePostgrestService
import com.example.model.CatalogItem
import com.example.model.ConflictResolutionChoice
import com.example.model.SyncConflictItem
import com.example.model.SyncUiState
import com.example.model.WatchStatus
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SyncResultSummary(
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictCount: Int,
    val message: String,
)

class MuvantaSyncEngine(
    private val dao: MuvantaDao,
    private val authService: SupabaseAuthService,
    private val postgrest: SupabasePostgrestService,
    private val networkMonitor: NetworkMonitor,
    scope: CoroutineScope,
) {
    private val syncMutex = Mutex()
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncedAt = MutableStateFlow(0L)
    private val _lastSyncSummary = MutableStateFlow("")
    private val _lastError = MutableStateFlow<String?>(null)
    private val _authExpired = MutableStateFlow(false)

    private val conflictsFlow = dao.observeSyncConflicts()

    val syncState: StateFlow<SyncUiState> = combine(
        networkMonitor.isOnline,
        authService.session,
        dao.observePendingOpsCount(),
        conflictsFlow,
        combine(_isSyncing, _lastSyncedAt, _lastSyncSummary, _lastError, _authExpired) { s, at, sum, err, exp ->
            SyncTransient(s, at, sum, err, exp)
        },
    ) { isOnline, session, pendingCount, conflictEntities, transient ->
        SyncUiState(
            isSupabaseConfigured = SupabaseConfig.isConfigured,
            isOnline = isOnline,
            isSyncing = transient.isSyncing,
            signedInEmail = session?.email,
            signedInUserId = session?.userId,
            pendingCount = pendingCount,
            conflicts = conflictEntities.map { e ->
                SyncConflictItem(
                    id = e.id,
                    itemId = e.itemId,
                    title = e.title,
                    fieldName = e.fieldName,
                    localValue = e.localValue,
                    cloudValue = e.cloudValue,
                    cloudVersion = e.cloudVersion,
                    detectedAt = e.detectedAt,
                )
            },
            lastSyncedAt = transient.lastSyncedAt,
            lastSyncSummary = transient.lastSummary,
            lastError = transient.lastError,
            authExpired = transient.authExpired,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncUiState(
            isSupabaseConfigured = SupabaseConfig.isConfigured,
            isOnline = networkMonitor.isOnline.value,
        ),
    )

    fun clearAuthExpiredFlag() {
        _authExpired.value = false
        _lastError.value = null
    }

    suspend fun syncNow(catalogById: Map<String, CatalogItem>): Result<SyncResultSummary> =
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!SupabaseConfig.isConfigured) {
                    return@withContext Result.failure(
                        IllegalStateException("Supabase is not configured in Secrets yet."),
                    )
                }
                if (!networkMonitor.refreshNow()) {
                    return@withContext Result.failure(
                        IllegalStateException("Device is offline. Changes remain queued locally."),
                    )
                }
                val sessionResult = authService.ensureFreshAccessToken()
                val session = sessionResult.getOrElse { err ->
                    _authExpired.value = authService.session.value != null
                    _lastError.value = err.message ?: "Sign in to synchronise with Supabase."
                    return@withContext Result.failure(err)
                }

                _isSyncing.value = true
                _lastError.value = null
                _authExpired.value = false

                var pushed = 0
                var pulled = 0
                var detectedConflicts = 0

                try {
                    // Adopt operations queued before sign-in to the active account
                    dao.adoptLocalOpsForUser(session.userId)

                    // 1. Pull current normalized cloud state & idempotent op ledger
                    val processedOpIds = postgrest.fetchProcessedOpIds(session)
                    val remoteLibraryById = postgrest.fetchAllLibraryEntries(session)
                        .associateBy { it.titleId }
                        .toMutableMap()
                    val remoteReviewsById = postgrest.fetchAllReviews(session)
                        .associateBy { it.titleId }
                        .toMutableMap()
                    val remoteUserTags = postgrest.fetchUserTags(session)
                    val tagIdToName = remoteUserTags.associate { it.id to it.name }
                    val lowerNameToTagId = remoteUserTags
                        .associate { it.name.lowercase(Locale.ROOT) to it.id }
                        .toMutableMap()
                    val remoteTagsByTitleId = postgrest.fetchUserTitleTagIds(session)
                        .mapValues { (_, tagIds) ->
                            tagIds.mapNotNull { tagIdToName[it] }
                                .distinctBy { it.lowercase(Locale.ROOT) }
                                .sortedBy { it.lowercase(Locale.ROOT) }
                        }
                        .toMutableMap()

                    val remoteMergedById = buildMergedSnapshots(
                        remoteLibraryById = remoteLibraryById,
                        remoteReviewsById = remoteReviewsById,
                        remoteTagsByTitleId = remoteTagsByTitleId,
                    ).toMutableMap()

                    val remoteLogs = postgrest.fetchViewingLogs(session)
                    val remoteEpisodes = postgrest.fetchWatchedEpisodes(session)
                    val remoteCollections = postgrest.fetchCollections(session)
                    val remoteProfile = postgrest.fetchProfile(session)

                    // Ensure any pre-existing unsynced local rows (syncVersion == 0) not yet in cloud or queue get queued
                    enqueueUnsyncedBaselineEntries(session.userId, remoteMergedById)

                    // 2. Push pending offline queue in FIFO order
                    val pendingOps = dao.getAllPendingOps()
                    val blockedEntityKeys = mutableSetOf<String>()

                    for (op in pendingOps) {
                        // Account isolation guard: never upload another user's queued ops
                        if (op.ownerUserId != session.userId && op.ownerUserId != OWNER_LOCAL) {
                            continue
                        }
                        // Idempotency guard: skip ops already confirmed in Supabase ledger
                        if (op.opId in processedOpIds) {
                            dao.deletePendingOp(op.opId)
                            continue
                        }

                        when (op.entityType) {
                            ENTITY_TITLE_ENTRY -> {
                                val titleId = op.entityKey
                                val localEntity = dao.getUserEntryById(titleId)
                                val remote = remoteMergedById[titleId]
                                if (localEntity == null) {
                                    dao.deletePendingOp(op.opId)
                                    continue
                                }

                                // Field-level conflict detection when cloud advanced past our baseVersion
                                if (remote != null && remote.version > op.baseVersion && !localEntity.isDeleted && !remote.isDeleted) {
                                    val newConflicts = detectFieldConflicts(
                                        itemId = titleId,
                                        title = catalogById[titleId]?.title ?: titleId,
                                        local = localEntity,
                                        remote = remote,
                                    )
                                    if (newConflicts.isNotEmpty()) {
                                        dao.deleteSyncConflictsForItem(titleId)
                                        newConflicts.forEach { dao.upsertSyncConflict(it) }
                                        detectedConflicts += newConflicts.size
                                        blockedEntityKeys += titleId
                                        continue
                                    }
                                }

                                val nextVersion = max(remote?.version ?: 0, max(op.baseVersion, localEntity.syncVersion)) + 1
                                val ratingNullable = localEntity.rating.takeIf { it in 1..10 }

                                // 2a. Upsert user_library_entries
                                val libPayload = RemoteLibraryEntry(
                                    userId = session.userId,
                                    titleId = titleId,
                                    status = localEntity.status,
                                    personalRating = ratingNullable,
                                    privateNotes = localEntity.notes,
                                    isFavorite = localEntity.isFavorite,
                                    rewatchCount = localEntity.rewatchCount,
                                    customPosterUrl = localEntity.customPosterUrl,
                                    syncVersion = nextVersion,
                                    isDeleted = localEntity.isDeleted,
                                    deletedAt = if (localEntity.isDeleted) localEntity.updatedAt else null,
                                    updatedAt = localEntity.updatedAt,
                                )
                                postgrest.upsertLibraryEntry(session, libPayload)
                                remoteLibraryById[titleId] = libPayload

                                // 2b. Sync reviews table (separate entity from private_notes)
                                val existingRemoteReview = remoteReviewsById[titleId]
                                val hasLocalReview = localEntity.reviewText.isNotBlank() && !localEntity.isDeleted
                                if (hasLocalReview) {
                                    val reviewPayload = RemoteReview(
                                        userId = session.userId,
                                        titleId = titleId,
                                        rating = ratingNullable,
                                        body = localEntity.reviewText,
                                        containsSpoilers = existingRemoteReview?.containsSpoilers ?: false,
                                        visibility = existingRemoteReview?.visibility ?: "private",
                                        syncVersion = nextVersion,
                                        isDeleted = false,
                                        updatedAt = localEntity.updatedAt,
                                    )
                                    postgrest.upsertReview(session, reviewPayload)
                                    remoteReviewsById[titleId] = reviewPayload
                                } else if (existingRemoteReview != null && !existingRemoteReview.isDeleted) {
                                    val tombstonedReview = existingRemoteReview.copy(
                                        rating = null,
                                        body = "",
                                        syncVersion = nextVersion,
                                        isDeleted = true,
                                        updatedAt = localEntity.updatedAt,
                                    )
                                    postgrest.upsertReview(session, tombstonedReview)
                                    remoteReviewsById[titleId] = tombstonedReview
                                }

                                // 2c. Sync normalized user_tags & user_title_tags only when changed
                                val desiredTags = if (localEntity.isDeleted) emptyList() else parseTagList(localEntity.tags)
                                val existingRemoteTags = remoteTagsByTitleId[titleId].orEmpty()
                                val normalizedDesired = desiredTags.map { it.lowercase(Locale.ROOT) }.sorted()
                                val normalizedRemote = existingRemoteTags.map { it.lowercase(Locale.ROOT) }.sorted()
                                if (normalizedDesired != normalizedRemote) {
                                    postgrest.replaceTitleTags(
                                        session = session,
                                        titleId = titleId,
                                        tagNames = desiredTags,
                                        lowerNameToTagId = lowerNameToTagId,
                                    )
                                    remoteTagsByTitleId[titleId] = desiredTags
                                }

                                postgrest.recordOpIdempotent(session, op.opId, op.entityType)
                                dao.upsertUserEntry(localEntity.copy(syncVersion = nextVersion))
                                remoteMergedById[titleId] = RemoteTitleSnapshot(
                                    titleId = titleId,
                                    status = localEntity.status,
                                    rating = localEntity.rating,
                                    notes = localEntity.notes,
                                    reviewText = if (hasLocalReview) localEntity.reviewText else "",
                                    tags = desiredTags.joinToString(", "),
                                    isFavorite = localEntity.isFavorite,
                                    rewatchCount = localEntity.rewatchCount,
                                    customPosterUrl = localEntity.customPosterUrl,
                                    version = nextVersion,
                                    isDeleted = localEntity.isDeleted,
                                    updatedAt = localEntity.updatedAt,
                                )
                                dao.deletePendingOp(op.opId)
                                pushed++
                            }

                            ENTITY_VIEWING_LOG -> {
                                val json = JSONObject(op.payloadJson)
                                val clientLogKey = json.optString("clientLogKey", op.entityKey)
                                if (op.operation == OP_DELETE) {
                                    postgrest.deleteViewingLog(session, clientLogKey)
                                } else {
                                    val ratingRaw = json.optInt("ratingAtLog", 0)
                                    postgrest.upsertViewingLog(
                                        session,
                                        RemoteViewingLog(
                                            userId = session.userId,
                                            clientLogKey = clientLogKey,
                                            titleId = json.optString("itemId"),
                                            loggedAt = json.optLong("loggedAt"),
                                            actionLabel = json.optString("actionLabel"),
                                            ratingAtLog = ratingRaw.takeIf { it in 1..10 },
                                            isRewatch = json.optBoolean("isRewatch"),
                                            note = json.optString("note"),
                                        ),
                                    )
                                }
                                postgrest.recordOpIdempotent(session, op.opId, op.entityType)
                                dao.deletePendingOp(op.opId)
                                pushed++
                            }

                            ENTITY_EPISODE -> {
                                val json = JSONObject(op.payloadJson)
                                val titleId = json.optString("itemId")
                                val season = json.optInt("season")
                                val episode = json.optInt("episode")
                                if (op.operation == OP_DELETE) {
                                    postgrest.deleteWatchedEpisode(session, titleId, season, episode)
                                } else {
                                    postgrest.upsertWatchedEpisode(
                                        session,
                                        RemoteWatchedEpisode(
                                            userId = session.userId,
                                            titleId = titleId,
                                            seasonNumber = season,
                                            episodeNumber = episode,
                                            watchedAt = json.optLong("watchedAt", System.currentTimeMillis()),
                                        ),
                                    )
                                }
                                postgrest.recordOpIdempotent(session, op.opId, op.entityType)
                                dao.deletePendingOp(op.opId)
                                pushed++
                            }

                            ENTITY_COLLECTION -> {
                                val json = JSONObject(op.payloadJson)
                                val clientId = json.optString("clientId", op.entityKey)
                                val itemsArr = json.optJSONArray("itemIds")
                                val itemIds = buildList {
                                    if (itemsArr != null) {
                                        for (i in 0 until itemsArr.length()) add(itemsArr.optString(i))
                                    }
                                }.filter { it.isNotBlank() }

                                postgrest.upsertCollectionWithItems(
                                    session,
                                    RemoteCollection(
                                        userId = session.userId,
                                        clientId = clientId,
                                        name = json.optString("name", "Collection"),
                                        description = json.optString("description", ""),
                                        itemIds = itemIds,
                                        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                                        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                                        isDeleted = op.operation == OP_DELETE,
                                    ),
                                )
                                postgrest.recordOpIdempotent(session, op.opId, op.entityType)
                                dao.deletePendingOp(op.opId)
                                pushed++
                            }

                            ENTITY_PROFILE -> {
                                val json = JSONObject(op.payloadJson)
                                postgrest.upsertProfile(
                                    session,
                                    RemoteProfile(
                                        userId = session.userId,
                                        displayName = json.optString("displayName", "Cinema Archivist"),
                                        tagline = json.optString("tagline", ""),
                                        defaultDiscoverMode = json.optString("defaultDiscoverMode", "grid"),
                                        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                                    ),
                                )
                                postgrest.recordOpIdempotent(session, op.opId, op.entityType)
                                dao.deletePendingOp(op.opId)
                                pushed++
                            }
                        }
                    }

                    // 3. Apply cloud updates to local Room storage (respecting conflicts & tombstones)
                    val remainingPending = dao.getAllPendingOps()
                    val pendingTitleIds = remainingPending
                        .filter { it.entityType == ENTITY_TITLE_ENTRY }
                        .mapTo(HashSet()) { it.entityKey }
                    val conflictedIds = dao.getAllSyncConflictsSnapshot().mapTo(HashSet()) { it.itemId }

                    val localEntriesById = dao.getAllUserEntriesIncludingDeleted().associateBy { it.itemId }
                    for (remote in remoteMergedById.values) {
                        if (remote.titleId in blockedEntityKeys || remote.titleId in pendingTitleIds || remote.titleId in conflictedIds) {
                            continue
                        }
                        val local = localEntriesById[remote.titleId]
                        if (local == null || remote.version > local.syncVersion) {
                            if (remote.isDeleted && local == null) continue
                            dao.upsertUserEntry(
                                UserTitleEntryEntity(
                                    itemId = remote.titleId,
                                    status = remote.status,
                                    rating = remote.rating,
                                    notes = remote.notes,
                                    customPosterUrl = remote.customPosterUrl,
                                    updatedAt = remote.updatedAt,
                                    reviewText = remote.reviewText,
                                    tags = remote.tags,
                                    isFavorite = remote.isFavorite,
                                    rewatchCount = remote.rewatchCount,
                                    syncVersion = remote.version,
                                    isDeleted = remote.isDeleted,
                                ),
                            )
                            pulled++
                        }
                    }

                    // Merge remote viewing logs (append-only by clientLogKey)
                    val existingLogKeys = dao.getAllViewingLogsSnapshot()
                        .mapNotNull { it.clientLogKey.takeIf(String::isNotBlank) }
                        .toHashSet()
                    for (rLog in remoteLogs) {
                        if (rLog.clientLogKey !in existingLogKeys) {
                            dao.insertViewingLog(
                                ViewingLogEntity(
                                    itemId = rLog.titleId,
                                    loggedAt = rLog.loggedAt,
                                    actionLabel = rLog.actionLabel,
                                    ratingAtLog = rLog.ratingAtLog ?: 0,
                                    isRewatch = rLog.isRewatch,
                                    note = rLog.note,
                                    clientLogKey = rLog.clientLogKey,
                                ),
                            )
                            existingLogKeys += rLog.clientLogKey
                            pulled++
                        }
                    }

                    // Merge remote watched episodes
                    val pendingEpisodeKeys = remainingPending
                        .filter { it.entityType == ENTITY_EPISODE }
                        .mapTo(HashSet()) { it.entityKey }
                    val localEpKeys = dao.getAllWatchedEpisodesSnapshot()
                        .mapTo(HashSet()) { "${it.itemId}:${it.season}:${it.episode}" }
                    for (rEp in remoteEpisodes) {
                        val key = "${rEp.titleId}:${rEp.seasonNumber}:${rEp.episodeNumber}"
                        if (key !in localEpKeys && key !in pendingEpisodeKeys) {
                            dao.markEpisodeWatched(
                                WatchedEpisodeEntity(
                                    itemId = rEp.titleId,
                                    season = rEp.seasonNumber,
                                    episode = rEp.episodeNumber,
                                    watchedAt = rEp.watchedAt,
                                ),
                            )
                            pulled++
                        }
                    }

                    // Merge remote custom collections & ordered items
                    val pendingCollectionKeys = remainingPending
                        .filter { it.entityType == ENTITY_COLLECTION }
                        .mapTo(HashSet()) { it.entityKey }
                    for (rCol in remoteCollections) {
                        if (rCol.clientId in pendingCollectionKeys) continue
                        val existing = dao.getCollectionByClientId(rCol.clientId)
                        if (rCol.isDeleted) {
                            if (existing != null) {
                                dao.deleteCollection(existing.id)
                                pulled++
                            }
                        } else if (existing == null) {
                            val newId = dao.insertCollection(
                                CustomCollectionEntity(
                                    name = rCol.name,
                                    description = rCol.description,
                                    createdAt = rCol.createdAt,
                                    clientId = rCol.clientId,
                                    updatedAt = rCol.updatedAt,
                                ),
                            )
                            if (rCol.itemIds.isNotEmpty()) {
                                dao.addItemsToCollection(
                                    rCol.itemIds.mapIndexed { idx, titleId ->
                                        CollectionItemCrossRef(
                                            collectionId = newId,
                                            itemId = titleId,
                                            addedAt = rCol.updatedAt + idx,
                                        )
                                    },
                                )
                            }
                            pulled++
                        } else if (rCol.updatedAt > existing.updatedAt) {
                            dao.updateCollection(existing.id, rCol.name, rCol.description, rCol.updatedAt)
                            dao.clearCollectionItems(existing.id)
                            if (rCol.itemIds.isNotEmpty()) {
                                dao.addItemsToCollection(
                                    rCol.itemIds.mapIndexed { idx, titleId ->
                                        CollectionItemCrossRef(
                                            collectionId = existing.id,
                                            itemId = titleId,
                                            addedAt = rCol.updatedAt + idx,
                                        )
                                    },
                                )
                            }
                            pulled++
                        }
                    }

                    // Pull cloud profile if no pending local profile edit
                    val hasPendingProfile = remainingPending.any { it.entityType == ENTITY_PROFILE }
                    if (remoteProfile != null && !hasPendingProfile) {
                        dao.upsertProfile(
                            LocalProfileEntity(
                                id = 1,
                                displayName = remoteProfile.displayName,
                                tagline = remoteProfile.tagline,
                                defaultDiscoverMode = remoteProfile.defaultDiscoverMode,
                            ),
                        )
                    }

                    val now = System.currentTimeMillis()
                    val summaryText = when {
                        detectedConflicts > 0 -> "Synced with $detectedConflicts conflict(s) needing review."
                        pushed == 0 && pulled == 0 -> "Everything is up to date."
                        else -> "Pushed $pushed, pulled $pulled update(s)."
                    }
                    _lastSyncedAt.value = now
                    _lastSyncSummary.value = summaryText
                    _lastError.value = null
                    Result.success(SyncResultSummary(pushed, pulled, detectedConflicts, summaryText))
                } catch (e: AuthExpiredException) {
                    _authExpired.value = true
                    _lastError.value = "Session expired. Sign in again to sync queued changes."
                    Result.failure(e)
                } catch (e: Exception) {
                    _lastError.value = e.message ?: "Sync failed. Changes remain safely queued."
                    Result.failure(e)
                } finally {
                    _isSyncing.value = false
                }
            }
        }

    suspend fun resolveConflict(
        conflictId: Long,
        choice: ConflictResolutionChoice,
        catalogById: Map<String, CatalogItem>,
    ): Result<String> = withContext(Dispatchers.IO) {
        val conflict = dao.getSyncConflictById(conflictId)
            ?: return@withContext Result.failure(IllegalArgumentException("Conflict no longer exists."))
        val entry = dao.getUserEntryById(conflict.itemId)
            ?: return@withContext Result.failure(IllegalArgumentException("Local entry not found."))

        val resolvedValue = when (choice) {
            ConflictResolutionChoice.KEEP_LOCAL -> conflict.localValue
            ConflictResolutionChoice.KEEP_CLOUD -> conflict.cloudValue
            ConflictResolutionChoice.MERGE_BOTH -> {
                if (conflict.localValue.isBlank()) conflict.cloudValue
                else if (conflict.cloudValue.isBlank()) conflict.localValue
                else "${conflict.localValue}\n\n--- Cloud Version ---\n${conflict.cloudValue}"
            }
        }

        val updatedEntry = when (conflict.fieldName) {
            "notes" -> entry.copy(notes = resolvedValue.take(2000))
            "reviewText" -> entry.copy(reviewText = resolvedValue.take(4000))
            "rating" -> entry.copy(rating = resolvedValue.toIntOrNull()?.coerceIn(0, 10) ?: entry.rating)
            "status" -> entry.copy(status = WatchStatus.fromWire(resolvedValue).wire)
            else -> entry
        }.copy(
            syncVersion = conflict.cloudVersion,
            updatedAt = System.currentTimeMillis(),
        )

        dao.upsertUserEntry(updatedEntry)
        dao.deleteSyncConflict(conflictId)

        // If no more conflicts remain for this title, queue a clean upsert based on cloudVersion
        val remainingForItem = dao.getAllSyncConflictsSnapshot().any { it.itemId == conflict.itemId }
        if (!remainingForItem) {
            val owner = authService.session.value?.userId ?: OWNER_LOCAL
            dao.deletePendingOpsForEntity(ENTITY_TITLE_ENTRY, conflict.itemId)
            dao.upsertPendingOp(
                PendingSyncOpEntity(
                    opId = UUID.randomUUID().toString(),
                    ownerUserId = owner,
                    entityType = ENTITY_TITLE_ENTRY,
                    entityKey = conflict.itemId,
                    operation = OP_UPSERT,
                    payloadJson = JSONObject().put("itemId", conflict.itemId).toString(),
                    baseVersion = conflict.cloudVersion,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (networkMonitor.isOnline.value && authService.session.value != null) {
                syncNow(catalogById)
            }
        }
        Result.success("Resolved ${conflict.fieldName} conflict for ${conflict.title}.")
    }

    private fun buildMergedSnapshots(
        remoteLibraryById: Map<String, RemoteLibraryEntry>,
        remoteReviewsById: Map<String, RemoteReview>,
        remoteTagsByTitleId: Map<String, List<String>>,
    ): Map<String, RemoteTitleSnapshot> {
        val allTitleIds = LinkedHashSet<String>().apply {
            addAll(remoteLibraryById.keys)
            addAll(remoteReviewsById.keys)
            addAll(remoteTagsByTitleId.keys)
        }
        return buildMap(allTitleIds.size) {
            for (titleId in allTitleIds) {
                val lib = remoteLibraryById[titleId]
                val rev = remoteReviewsById[titleId]
                val tags = remoteTagsByTitleId[titleId].orEmpty()
                val activeReviewText = if (rev != null && !rev.isDeleted) rev.body else ""
                val resolvedRating = lib?.personalRating ?: rev?.takeIf { !it.isDeleted }?.rating ?: 0
                val version = max(lib?.syncVersion ?: 0, rev?.syncVersion ?: 0).coerceAtLeast(1)
                val updatedAt = max(lib?.updatedAt ?: 0L, rev?.updatedAt ?: 0L)
                put(
                    titleId,
                    RemoteTitleSnapshot(
                        titleId = titleId,
                        status = lib?.status ?: WatchStatus.UNWATCHED.wire,
                        rating = resolvedRating,
                        notes = lib?.privateNotes.orEmpty(),
                        reviewText = activeReviewText,
                        tags = tags.joinToString(", "),
                        isFavorite = lib?.isFavorite ?: false,
                        rewatchCount = lib?.rewatchCount ?: 0,
                        customPosterUrl = lib?.customPosterUrl.orEmpty(),
                        version = version,
                        isDeleted = lib?.isDeleted ?: false,
                        updatedAt = updatedAt,
                    ),
                )
            }
        }
    }

    private fun parseTagList(rawCsv: String): List<String> =
        rawCsv.split(",")
            .map { it.trim().take(40) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }

    private suspend fun enqueueUnsyncedBaselineEntries(
        userId: String,
        remoteEntriesById: Map<String, RemoteTitleSnapshot>,
    ) {
        val existingPendingKeys = dao.getAllPendingOps()
            .filter { it.entityType == ENTITY_TITLE_ENTRY }
            .mapTo(HashSet()) { it.entityKey }
        val localEntries = dao.getAllUserEntriesIncludingDeleted()
        val now = System.currentTimeMillis()
        for (entry in localEntries) {
            if (entry.syncVersion == 0 && !entry.isDeleted && entry.itemId !in existingPendingKeys) {
                val remote = remoteEntriesById[entry.itemId]
                if (remote == null) {
                    dao.upsertPendingOp(
                        PendingSyncOpEntity(
                            opId = UUID.randomUUID().toString(),
                            ownerUserId = userId,
                            entityType = ENTITY_TITLE_ENTRY,
                            entityKey = entry.itemId,
                            operation = OP_UPSERT,
                            payloadJson = JSONObject().put("itemId", entry.itemId).toString(),
                            baseVersion = 0,
                            createdAt = now,
                        ),
                    )
                }
            }
        }
    }

    private fun detectFieldConflicts(
        itemId: String,
        title: String,
        local: UserTitleEntryEntity,
        remote: RemoteTitleSnapshot,
    ): List<SyncConflictEntity> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<SyncConflictEntity>()

        // Never silently overwrite conflicting private notes or reviews
        if (local.notes.trim() != remote.notes.trim() && local.notes.isNotBlank() && remote.notes.isNotBlank()) {
            out += SyncConflictEntity(
                itemId = itemId,
                title = title,
                fieldName = "notes",
                localValue = local.notes,
                cloudValue = remote.notes,
                cloudVersion = remote.version,
                detectedAt = now,
            )
        }
        if (local.reviewText.trim() != remote.reviewText.trim() && local.reviewText.isNotBlank() && remote.reviewText.isNotBlank()) {
            out += SyncConflictEntity(
                itemId = itemId,
                title = title,
                fieldName = "reviewText",
                localValue = local.reviewText,
                cloudValue = remote.reviewText,
                cloudVersion = remote.version,
                detectedAt = now,
            )
        }
        if (local.rating != remote.rating && local.rating > 0 && remote.rating > 0) {
            out += SyncConflictEntity(
                itemId = itemId,
                title = title,
                fieldName = "rating",
                localValue = local.rating.toString(),
                cloudValue = remote.rating.toString(),
                cloudVersion = remote.version,
                detectedAt = now,
            )
        }
        if (local.status != remote.status && local.status != WatchStatus.UNWATCHED.wire && remote.status != WatchStatus.UNWATCHED.wire) {
            out += SyncConflictEntity(
                itemId = itemId,
                title = title,
                fieldName = "status",
                localValue = local.status,
                cloudValue = remote.status,
                cloudVersion = remote.version,
                detectedAt = now,
            )
        }
        return out
    }

    private data class RemoteTitleSnapshot(
        val titleId: String,
        val status: String,
        val rating: Int,
        val notes: String,
        val reviewText: String,
        val tags: String,
        val isFavorite: Boolean,
        val rewatchCount: Int,
        val customPosterUrl: String,
        val version: Int,
        val isDeleted: Boolean,
        val updatedAt: Long,
    )

    private data class SyncTransient(
        val isSyncing: Boolean,
        val lastSyncedAt: Long,
        val lastSummary: String,
        val lastError: String?,
        val authExpired: Boolean,
    )

    companion object {
        const val OWNER_LOCAL = "local"
        const val ENTITY_TITLE_ENTRY = "title_entry"
        const val ENTITY_VIEWING_LOG = "viewing_log"
        const val ENTITY_EPISODE = "episode"
        const val ENTITY_COLLECTION = "collection"
        const val ENTITY_PROFILE = "profile"

        const val OP_UPSERT = "UPSERT"
        const val OP_DELETE = "DELETE"
    }
}
