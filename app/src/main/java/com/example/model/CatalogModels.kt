package com.example.model

enum class MediaType(val wire: String, val label: String) {
    MOVIE("movie", "Film"),
    SERIES("series", "Series"),
}

enum class WatchStatus(val wire: String, val label: String) {
    UNWATCHED("unwatched", "Unwatched"),
    BOOKMARKED("bookmarked", "Watchlist"),
    WATCHING("watching", "Watching"),
    WATCHED("watched", "Watched"),
    REWATCHING("rewatching", "Rewatching"),
    ON_HOLD("on_hold", "On Hold"),
    DROPPED("dropped", "Dropped");

    companion object {
        fun fromWire(value: String?): WatchStatus =
            entries.firstOrNull { it.wire == value } ?: UNWATCHED
    }
}

enum class DiscoverMode(val wire: String, val label: String) {
    GRID("grid", "Grid"),
    SHELF("shelf", "Shelf"),
    ORB("orb", "3D Orb");

    companion object {
        fun fromWire(value: String?): DiscoverMode =
            entries.firstOrNull { it.wire == value } ?: GRID
    }
}

enum class DiscoverCategory(val label: String) {
    ALL("All"),
    MOVIES("Movies"),
    SERIES("Series"),
    ANIME("Anime"),
    DOCUMENTARIES("Documentaries"),
}

enum class MoodCategory(val label: String, val subtitle: String) {
    FEEL_GOOD("Feel-Good", "Warm, joyful stories"),
    EMOTIONAL("Emotional", "Deeply moving cinema"),
    HEARTBREAKING("Heartbreaking", "Unforgettable tragedies"),
    MOTIVATIONAL("Motivational", "Inspiring human grit"),
    MIND_BENDING("Mind-Bending", "Complex twists & puzzles"),
    RELAXING("Relaxing", "Calm & atmospheric"),
    DARK("Dark", "Gritty & intense"),
    ROMANTIC("Romantic", "Love & longing"),
    THRILLING("Thrilling", "High-stakes suspense"),
    THOUGHT_PROVOKING("Thought-Provoking", "Philosophical & bold"),
}

enum class StatusFilter(val label: String) {
    ALL("All Statuses"),
    UNWATCHED("Unwatched"),
    WATCHING("Watching"),
    REWATCHING("Rewatching"),
    BOOKMARKED("Watchlist"),
    WATCHED("Watched"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    FAVORITES("Favourites"),
    NOT_COMPLETED("Not Completed"),
    RATED("Rated by Me"),
}

enum class LibraryFilter(val label: String) {
    ALL_LOGGED("All Logged"),
    FAVORITES("Favourites"),
    WATCHING("Watching"),
    REWATCHING("Rewatching"),
    BOOKMARKED("Watchlist"),
    WATCHED("Watched"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    RATED("Rated"),
}

enum class LibrarySortOption(val label: String) {
    RECENT("Recently Updated"),
    RATING_DESC("Highest Rated"),
    YEAR_DESC("Release Year"),
    TITLE_ASC("Title (A–Z)"),
}

enum class SortOption(val label: String) {
    RANK_ASC("Rank (#1 First)"),
    RANK_DESC("Rank (Descending)"),
    YEAR_DESC("Release Year (Newest)"),
    YEAR_ASC("Release Year (Oldest)"),
    TITLE_ASC("Title (A to Z)"),
    RATING_DESC("Personal Rating (Highest)"),
}

data class TierDefinition(
    val id: String,
    val type: MediaType,
    val minRank: Int,
    val maxRank: Int,
    val shortTitle: String,
    val fullLabel: String,
    val subtitle: String,
) {
    val totalCount: Int get() = maxRank - minRank + 1
}

object ArchiveTiers {
    val all: List<TierDefinition> = listOf(
        TierDefinition("m1", MediaType.MOVIE, 1, 150, "Movies 1–150", "Movies 1 to 150: Absolute Masterpieces", "Foundational cinema milestones"),
        TierDefinition("m2", MediaType.MOVIE, 151, 350, "Movies 151–350", "Movies 151 to 350: World & Indian Classics", "Acclaimed global and Indian auteurs"),
        TierDefinition("m3", MediaType.MOVIE, 351, 600, "Movies 351–600", "Movies 351 to 600: Thrillers, Sci-Fi & Drama", "Essential genre and dramatic works"),
        TierDefinition("m4", MediaType.MOVIE, 601, 800, "Movies 601–800", "Movies 601 to 800: Crowd-Pleasers & Blockbusters", "Memorable modern and classic spectacles"),
        TierDefinition("m5", MediaType.MOVIE, 801, 1000, "Movies 801–1,000", "Movies 801 to 1,000: Time-Pass Entertainers", "High-craft rewatchable favorites"),
        TierDefinition("s1", MediaType.SERIES, 1, 50, "Series 1–50", "Series 1 to 50: All-Time Series", "Peak television masterpieces"),
        TierDefinition("s2", MediaType.SERIES, 51, 120, "Series 51–120", "Series 51 to 120: Global, Anime & Indian Series", "International drama, anime, and Indian hits"),
        TierDefinition("s3", MediaType.SERIES, 121, 200, "Series 121–200", "Series 121 to 200: Crime, Sci-Fi & Sitcoms", "Cult favorites and landmark television"),
        TierDefinition("s4", MediaType.SERIES, 201, 250, "Series 201–250", "Series 201 to 250: Gripping Crowd-Pleasers", "Compulsive serialized storytelling"),
        TierDefinition("s5", MediaType.SERIES, 251, 300, "Series 251–300", "Series 251 to 300: Comfort Binge Series", "Enduring comfort and anthology gems"),
    )

    fun findFor(type: MediaType, rank: Int): TierDefinition =
        all.firstOrNull { it.type == type && rank in it.minRank..it.maxRank } ?: all.first()

    fun findById(id: String): TierDefinition? = all.firstOrNull { it.id == id }
}

data class CuratedBaseline(
    val director: String = "",
    val country: String = "",
    val language: String = "",
    val genre: String = "",
    val runtime: String = "",
    val description: String = "",
)

data class CatalogItem(
    val id: String,
    val type: MediaType,
    val rank: Int,
    val title: String,
    val year: Int,
    val tier: TierDefinition,
    val isAnime: Boolean,
    val isDocumentary: Boolean,
    val curated: CuratedBaseline,
    val moods: Set<MoodCategory> = emptySet(),
)

data class UserTitleState(
    val itemId: String,
    val status: WatchStatus = WatchStatus.UNWATCHED,
    val rating: Int = 0,
    val notes: String = "",
    val reviewText: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val rewatchCount: Int = 0,
    val customPosterUrl: String = "",
    val updatedAt: Long = 0L,
    val syncVersion: Int = 0,
    val isDeleted: Boolean = false,
) {
    val isLogged: Boolean
        get() = !isDeleted && (
            status != WatchStatus.UNWATCHED ||
                rating > 0 ||
                notes.isNotBlank() ||
                reviewText.isNotBlank() ||
                isFavorite ||
                rewatchCount > 0
            )

    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

data class ViewingLogEntry(
    val id: Long,
    val itemId: String,
    val loggedAt: Long,
    val actionLabel: String,
    val ratingAtLog: Int,
    val isRewatch: Boolean,
    val note: String,
)

data class SeriesEpisode(
    val itemId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val airDate: String,
    val runtimeMinutes: Int,
    val summary: String,
    val watched: Boolean = false,
) {
    val code: String
        get() = "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
}

data class ResolvedMetadata(
    val posterUrl: String = "",
    val description: String = "",
    val genre: String = "",
    val director: String = "",
    val country: String = "",
    val language: String = "",
    val runtime: String = "",
    val network: String = "",
    val wikiUrl: String = "",
    val tvmazeShowId: Int = 0,
    val fetched: Boolean = false,
    val failedAt: Long = 0L,
)

data class EnrichedCatalogItem(
    val item: CatalogItem,
    val user: UserTitleState,
    val meta: ResolvedMetadata,
) {
    val effectivePosterUrl: String
        get() = user.customPosterUrl.ifBlank { meta.posterUrl }

    val effectiveGenre: String
        get() = meta.genre.ifBlank { item.curated.genre }
            .ifBlank {
                when {
                    item.isAnime -> "Anime"
                    item.isDocumentary -> "Documentary"
                    else -> item.type.label
                }
            }

    val effectiveDescription: String
        get() = meta.description.ifBlank { item.curated.description }

    val effectiveRuntime: String
        get() = meta.runtime.ifBlank { item.curated.runtime }

    val effectiveDirector: String
        get() = meta.director.ifBlank { item.curated.director }

    val effectiveCountry: String
        get() = meta.country.ifBlank { item.curated.country }

    val effectiveLanguage: String
        get() = meta.language.ifBlank { item.curated.language }

    val effectiveMoods: Set<MoodCategory>
        get() {
            val inferred = item.moods.toMutableSet()
            val g = effectiveGenre.lowercase()
            if ("thriller" in g || "mystery" in g || "crime" in g) inferred += MoodCategory.THRILLING
            if ("sci-fi" in g || "science fiction" in g) {
                inferred += MoodCategory.MIND_BENDING
                inferred += MoodCategory.THOUGHT_PROVOKING
            }
            if ("comedy" in g || "family" in g) inferred += MoodCategory.FEEL_GOOD
            if ("drama" in g) inferred += MoodCategory.EMOTIONAL
            if ("horror" in g || "crime" in g) inferred += MoodCategory.DARK
            if ("romance" in g) inferred += MoodCategory.ROMANTIC
            if ("biography" in g || "sport" in g) inferred += MoodCategory.MOTIVATIONAL
            if ("documentary" in g || "nature" in g) inferred += MoodCategory.THOUGHT_PROVOKING
            if ("war" in g || "history" in g) inferred += MoodCategory.HEARTBREAKING
            if ("animation" in g && "horror" !in g && "crime" !in g) inferred += MoodCategory.RELAXING
            return inferred
        }
}

data class RecommendationItem(
    val enriched: EnrichedCatalogItem,
    val reason: String,
)

data class TierProgress(
    val tier: TierDefinition,
    val watchedCount: Int,
) {
    val fraction: Float
        get() = if (tier.totalCount > 0) watchedCount.toFloat() / tier.totalCount else 0f

    val percent: Int
        get() = ((fraction * 100f).toInt()).coerceIn(0, 100)
}

data class NamedCount(
    val label: String,
    val count: Int,
)

data class ArchiveStats(
    val totalTitles: Int = 1300,
    val totalMovies: Int = 1000,
    val totalSeries: Int = 300,
    val watchedMovies: Int = 0,
    val watchedSeries: Int = 0,
    val watchedAnime: Int = 0,
    val watchedDocs: Int = 0,
    val watchingCount: Int = 0,
    val rewatchingCount: Int = 0,
    val bookmarkedCount: Int = 0,
    val onHoldCount: Int = 0,
    val droppedCount: Int = 0,
    val favoritesCount: Int = 0,
    val reviewsCount: Int = 0,
    val totalRewatches: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val ratedCount: Int = 0,
    val averageRating: Float = 0f,
    val ratingDistribution: Map<Int, Int> = emptyMap(),
    val topGenres: List<NamedCount> = emptyList(),
    val topMoods: List<NamedCount> = emptyList(),
    val topDecades: List<NamedCount> = emptyList(),
    val tierProgress: List<TierProgress> = emptyList(),
) {
    val totalWatched: Int get() = watchedMovies + watchedSeries
    val remainingCount: Int get() = (totalTitles - totalWatched).coerceAtLeast(0)
    val unwatchedCount: Int
        get() = (totalTitles - totalWatched - watchingCount - rewatchingCount - bookmarkedCount - onHoldCount - droppedCount)
            .coerceAtLeast(0)
    val overallPercent: Int get() = if (totalTitles > 0) ((totalWatched * 100f) / totalTitles).toInt() else 0
    val moviesPercent: Int get() = if (totalMovies > 0) ((watchedMovies * 100f) / totalMovies).toInt() else 0
    val seriesPercent: Int get() = if (totalSeries > 0) ((watchedSeries * 100f) / totalSeries).toInt() else 0
}

data class CustomCollectionWithItems(
    val id: Long,
    val name: String,
    val description: String,
    val itemIds: List<String>,
)

data class ImportPreview(
    val entries: Map<String, UserTitleState>,
    val validCount: Int,
    val skippedCount: Int,
)

enum class SyncMode(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    SYNCING("Syncing"),
}

enum class ConflictResolutionChoice {
    KEEP_LOCAL,
    KEEP_CLOUD,
    MERGE_BOTH,
}

data class SyncConflictItem(
    val id: Long,
    val itemId: String,
    val title: String,
    val fieldName: String,
    val localValue: String,
    val cloudValue: String,
    val cloudVersion: Int,
    val detectedAt: Long,
) {
    val fieldDisplayLabel: String
        get() = when (fieldName) {
            "notes" -> "Private Notes"
            "reviewText" -> "Review"
            "rating" -> "Personal Rating"
            "status" -> "Watch Status"
            else -> fieldName.replaceFirstChar { it.uppercase() }
        }

    val supportsMergeBoth: Boolean
        get() = fieldName == "notes" || fieldName == "reviewText"
}

data class SyncUiState(
    val isSupabaseConfigured: Boolean = false,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val signedInEmail: String? = null,
    val signedInUserId: String? = null,
    val pendingCount: Int = 0,
    val conflicts: List<SyncConflictItem> = emptyList(),
    val lastSyncedAt: Long = 0L,
    val lastSyncSummary: String = "",
    val lastError: String? = null,
    val authExpired: Boolean = false,
) {
    val isSignedIn: Boolean get() = !signedInUserId.isNullOrBlank()

    val mode: SyncMode
        get() = when {
            isSyncing -> SyncMode.SYNCING
            !isOnline -> SyncMode.OFFLINE
            else -> SyncMode.ONLINE
        }
}
