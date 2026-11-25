package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_title_entries")
data class UserTitleEntryEntity(
    @PrimaryKey val itemId: String,
    val status: String,
    val rating: Int,
    val notes: String,
    val customPosterUrl: String,
    val updatedAt: Long,
    val reviewText: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val rewatchCount: Int = 0,
    val syncVersion: Int = 0,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "cached_metadata")
data class CachedMetadataEntity(
    @PrimaryKey val itemId: String,
    val posterUrl: String,
    val description: String,
    val genre: String,
    val runtime: String,
    val network: String,
    val wikiUrl: String,
    val fetched: Boolean,
    val failedAt: Long,
    val cachedAt: Long,
    val tvmazeShowId: Int = 0,
)

@Entity(tableName = "custom_collections")
data class CustomCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val createdAt: Long,
    val clientId: String = "",
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("collectionId"), Index("itemId")],
)
data class CollectionItemCrossRef(
    val collectionId: Long,
    val itemId: String,
    val addedAt: Long,
)

@Entity(tableName = "local_profile")
data class LocalProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val tagline: String,
    val defaultDiscoverMode: String,
)

@Entity(
    tableName = "viewing_history",
    indices = [Index("itemId"), Index("loggedAt")],
)
data class ViewingLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: String,
    val loggedAt: Long,
    val actionLabel: String,
    val ratingAtLog: Int,
    val isRewatch: Boolean,
    val note: String,
    val clientLogKey: String = "",
)

@Entity(
    tableName = "cached_episodes",
    primaryKeys = ["itemId", "season", "episode"],
    indices = [Index("itemId")],
)
data class CachedEpisodeEntity(
    val itemId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val airDate: String,
    val runtimeMinutes: Int,
    val summary: String,
)

@Entity(
    tableName = "watched_episodes",
    primaryKeys = ["itemId", "season", "episode"],
    indices = [Index("itemId")],
)
data class WatchedEpisodeEntity(
    val itemId: String,
    val season: Int,
    val episode: Int,
    val watchedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Entity(
    tableName = "pending_sync_ops",
    indices = [Index("ownerUserId"), Index("createdAt")],
)
data class PendingSyncOpEntity(
    @PrimaryKey val opId: String,
    val ownerUserId: String,
    val entityType: String,
    val entityKey: String,
    val operation: String,
    val payloadJson: String,
    val baseVersion: Int,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String = "",
)

@Entity(
    tableName = "sync_conflicts",
    indices = [Index("itemId")],
)
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: String,
    val title: String,
    val fieldName: String,
    val localValue: String,
    val cloudValue: String,
    val cloudVersion: Int,
    val detectedAt: Long,
)
