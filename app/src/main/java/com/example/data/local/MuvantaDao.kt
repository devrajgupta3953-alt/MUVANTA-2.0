package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MuvantaDao {

    // User title entries (exclude soft-deleted tombstones from UI queries)
    @Query("SELECT * FROM user_title_entries WHERE isDeleted = 0")
    fun observeAllUserEntries(): Flow<List<UserTitleEntryEntity>>

    @Query("SELECT * FROM user_title_entries WHERE isDeleted = 0")
    suspend fun getAllUserEntriesSnapshot(): List<UserTitleEntryEntity>

    @Query("SELECT * FROM user_title_entries")
    suspend fun getAllUserEntriesIncludingDeleted(): List<UserTitleEntryEntity>

    @Query("SELECT * FROM user_title_entries WHERE itemId = :itemId LIMIT 1")
    suspend fun getUserEntryById(itemId: String): UserTitleEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserEntry(entry: UserTitleEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserEntries(entries: List<UserTitleEntryEntity>)

    @Query("DELETE FROM user_title_entries WHERE itemId = :itemId")
    suspend fun deleteUserEntry(itemId: String)

    @Query("DELETE FROM user_title_entries")
    suspend fun clearAllUserEntries()

    @Transaction
    suspend fun replaceAllUserEntries(entries: List<UserTitleEntryEntity>) {
        clearAllUserEntries()
        if (entries.isNotEmpty()) {
            upsertUserEntries(entries)
        }
    }

    // Metadata cache & offline artwork storage
    @Query("SELECT * FROM cached_metadata")
    fun observeAllMetadata(): Flow<List<CachedMetadataEntity>>

    @Query("SELECT COUNT(*) FROM cached_metadata WHERE posterUrl != ''")
    fun observeCachedPosterCount(): Flow<Int>

    @Query("SELECT * FROM cached_metadata WHERE itemId = :itemId LIMIT 1")
    suspend fun getMetadata(itemId: String): CachedMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entity: CachedMetadataEntity)

    @Query("DELETE FROM cached_metadata WHERE itemId = :itemId")
    suspend fun deleteMetadata(itemId: String)

    @Query("UPDATE cached_metadata SET posterUrl = '', failedAt = 0")
    suspend fun clearCachedPosterUrls()

    // Custom collections
    @Query("SELECT * FROM custom_collections ORDER BY createdAt DESC")
    fun observeCollections(): Flow<List<CustomCollectionEntity>>

    @Query("SELECT * FROM custom_collections ORDER BY createdAt DESC")
    suspend fun getAllCollectionsSnapshot(): List<CustomCollectionEntity>

    @Query("SELECT * FROM custom_collections WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: Long): CustomCollectionEntity?

    @Query("SELECT * FROM custom_collections WHERE clientId = :clientId LIMIT 1")
    suspend fun getCollectionByClientId(clientId: String): CustomCollectionEntity?

    @Query("SELECT * FROM collection_items ORDER BY addedAt DESC")
    fun observeCollectionItems(): Flow<List<CollectionItemCrossRef>>

    @Query("SELECT * FROM collection_items ORDER BY addedAt DESC")
    suspend fun getAllCollectionItemsSnapshot(): List<CollectionItemCrossRef>

    @Query("SELECT itemId FROM collection_items WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    suspend fun getCollectionItemIds(collectionId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CustomCollectionEntity): Long

    @Query("UPDATE custom_collections SET name = :name, description = :description, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCollection(id: Long, name: String, description: String, updatedAt: Long)

    @Query("UPDATE custom_collections SET clientId = :clientId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCollectionClientId(id: Long, clientId: String, updatedAt: Long)

    @Query("DELETE FROM custom_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)

    @Query("DELETE FROM custom_collections")
    suspend fun clearAllCollections()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItemToCollection(ref: CollectionItemCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItemsToCollection(refs: List<CollectionItemCrossRef>)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND itemId = :itemId")
    suspend fun removeItemFromCollection(collectionId: Long, itemId: String)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId")
    suspend fun clearCollectionItems(collectionId: Long)

    @Query("DELETE FROM collection_items")
    suspend fun clearAllCollectionItems()

    // Local profile
    @Query("SELECT * FROM local_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<LocalProfileEntity?>

    @Query("SELECT * FROM local_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfileSnapshot(): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LocalProfileEntity)

    @Query("DELETE FROM local_profile")
    suspend fun clearProfile()

    // Viewing history & rewatches
    @Query("SELECT * FROM viewing_history WHERE itemId = :itemId ORDER BY loggedAt DESC")
    fun observeViewingHistoryForItem(itemId: String): Flow<List<ViewingLogEntity>>

    @Query("SELECT * FROM viewing_history ORDER BY loggedAt DESC LIMIT 100")
    fun observeRecentViewingHistory(): Flow<List<ViewingLogEntity>>

    @Query("SELECT * FROM viewing_history ORDER BY loggedAt DESC")
    suspend fun getAllViewingLogsSnapshot(): List<ViewingLogEntity>

    @Query("SELECT * FROM viewing_history WHERE id = :id LIMIT 1")
    suspend fun getViewingLogById(id: Long): ViewingLogEntity?

    @Query("SELECT * FROM viewing_history WHERE clientLogKey = :clientLogKey LIMIT 1")
    suspend fun getViewingLogByKey(clientLogKey: String): ViewingLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViewingLog(log: ViewingLogEntity): Long

    @Query("DELETE FROM viewing_history WHERE id = :id")
    suspend fun deleteViewingLog(id: Long)

    @Query("DELETE FROM viewing_history")
    suspend fun clearAllViewingHistory()

    // Series episodes & tracking
    @Query("SELECT * FROM cached_episodes WHERE itemId = :itemId ORDER BY season ASC, episode ASC")
    fun observeEpisodesForSeries(itemId: String): Flow<List<CachedEpisodeEntity>>

    @Query("SELECT COUNT(*) FROM cached_episodes WHERE itemId = :itemId")
    suspend fun countCachedEpisodes(itemId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedEpisodes(episodes: List<CachedEpisodeEntity>)

    @Query("SELECT * FROM watched_episodes WHERE itemId = :itemId")
    fun observeWatchedEpisodesForSeries(itemId: String): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes")
    suspend fun getAllWatchedEpisodesSnapshot(): List<WatchedEpisodeEntity>

    @Query("SELECT COUNT(*) FROM watched_episodes")
    fun observeTotalWatchedEpisodesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markEpisodeWatched(watched: WatchedEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markEpisodesWatched(watched: List<WatchedEpisodeEntity>)

    @Query("DELETE FROM watched_episodes WHERE itemId = :itemId AND season = :season AND episode = :episode")
    suspend fun unmarkEpisodeWatched(itemId: String, season: Int, episode: Int)

    @Query("DELETE FROM watched_episodes WHERE itemId = :itemId AND season = :season")
    suspend fun unmarkSeasonWatched(itemId: String, season: Int)

    @Query("DELETE FROM watched_episodes")
    suspend fun clearAllWatchedEpisodes()

    // Search history
    @Query("SELECT query FROM search_history ORDER BY searchedAt DESC LIMIT 10")
    fun observeSearchHistory(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistoryItem(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Persistent Offline Sync Queue
    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun observePendingOpsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    suspend fun countPendingOps(): Int

    @Query("SELECT * FROM pending_sync_ops ORDER BY createdAt ASC")
    suspend fun getAllPendingOps(): List<PendingSyncOpEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingOp(op: PendingSyncOpEntity)

    @Query("DELETE FROM pending_sync_ops WHERE opId = :opId")
    suspend fun deletePendingOp(opId: String)

    @Query("DELETE FROM pending_sync_ops WHERE entityType = :entityType AND entityKey = :entityKey")
    suspend fun deletePendingOpsForEntity(entityType: String, entityKey: String)

    @Query("UPDATE pending_sync_ops SET retryCount = retryCount + 1, lastError = :error WHERE opId = :opId")
    suspend fun markPendingOpFailure(opId: String, error: String)

    @Query("UPDATE pending_sync_ops SET ownerUserId = :userId WHERE ownerUserId = 'local'")
    suspend fun adoptLocalOpsForUser(userId: String)

    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAllPendingOps()

    // Field-level Sync Conflicts
    @Query("SELECT * FROM sync_conflicts ORDER BY detectedAt DESC")
    fun observeSyncConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts")
    suspend fun getAllSyncConflictsSnapshot(): List<SyncConflictEntity>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getSyncConflictById(id: Long): SyncConflictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncConflict(conflict: SyncConflictEntity)

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteSyncConflict(id: Long)

    @Query("DELETE FROM sync_conflicts WHERE itemId = :itemId")
    suspend fun deleteSyncConflictsForItem(itemId: String)

    @Query("DELETE FROM sync_conflicts")
    suspend fun clearAllSyncConflicts()

    // Strict Account Isolation Wipe on Sign-Out (preserves public catalog & artwork caches)
    @Transaction
    suspend fun clearAllUserAccountData() {
        clearAllUserEntries()
        clearAllViewingHistory()
        clearAllWatchedEpisodes()
        clearAllCollectionItems()
        clearAllCollections()
        clearProfile()
        clearSearchHistory()
        clearAllPendingOps()
        clearAllSyncConflicts()
    }
}
