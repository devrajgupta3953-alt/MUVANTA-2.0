package com.example.data.remote

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AuthExpiredException(message: String = "Session expired. Please sign in again.") : IOException(message)

/**
 * Pure-JDK UTC ISO-8601 helper compatible with minSdk 24 (no java.time desugaring required).
 * Formats epoch millis for PostgreSQL TIMESTAMPTZ and parses variable-precision Postgres timestamps
 * (e.g., 2026-11-25T21:54:00.123456+00:00, 2026-11-25T21:54:00Z, 2026-11-25T21:54:00.12-05:00).
 */
internal object IsoTime {
    private val isoRegex = Regex(
        """^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2}|[+-]\d{2})?$""",
    )
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun formatEpochMs(epochMs: Long): String {
        val safeMs = if (epochMs > 0L) epochMs else System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = utcZone
        }
        return fmt.format(Date(safeMs))
    }

    fun parseEpochMs(raw: String?): Long {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return 0L
        // Backward compatibility if an integer epoch was ever returned
        trimmed.toLongOrNull()?.let { return it }

        val match = isoRegex.matchEntire(trimmed) ?: return 0L
        val datePart = match.groupValues[1]
        val timePart = match.groupValues[2]
        val fracRaw = match.groupValues[3]
        val tzRaw = match.groupValues[4]

        val millisPart = fracRaw.padEnd(3, '0').take(3)
        val utcDate = runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply {
                timeZone = utcZone
                isLenient = false
            }
            fmt.parse("$datePart $timePart.$millisPart")
        }.getOrNull() ?: return 0L

        val offsetMs = parseOffsetMs(tzRaw)
        return utcDate.time - offsetMs
    }

    private fun parseOffsetMs(tz: String): Long {
        if (tz.isEmpty() || tz == "Z") return 0L
        val sign = if (tz.startsWith("-")) -1L else 1L
        val digits = tz.substring(1).replace(":", "")
        val hours = digits.take(2).toLongOrNull() ?: 0L
        val mins = if (digits.length >= 4) digits.substring(2, 4).toLongOrNull() ?: 0L else 0L
        return sign * ((hours * 60L + mins) * 60_000L)
    }
}

data class RemoteProfile(
    val userId: String,
    val displayName: String,
    val tagline: String,
    val defaultDiscoverMode: String,
    val updatedAt: Long,
)

data class RemoteLibraryEntry(
    val userId: String,
    val titleId: String,
    val status: String,
    val personalRating: Int?,
    val privateNotes: String,
    val isFavorite: Boolean,
    val rewatchCount: Int,
    val customPosterUrl: String,
    val syncVersion: Int,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val updatedAt: Long,
)

data class RemoteReview(
    val userId: String,
    val titleId: String,
    val rating: Int?,
    val body: String,
    val containsSpoilers: Boolean,
    val visibility: String,
    val syncVersion: Int,
    val isDeleted: Boolean,
    val updatedAt: Long,
)

data class RemoteUserTag(
    val id: String,
    val name: String,
)

data class RemoteViewingLog(
    val userId: String,
    val clientLogKey: String,
    val titleId: String,
    val loggedAt: Long,
    val actionLabel: String,
    val ratingAtLog: Int?,
    val isRewatch: Boolean,
    val note: String,
)

data class RemoteWatchedEpisode(
    val userId: String,
    val titleId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAt: Long,
)

data class RemoteCollection(
    val id: String = "",
    val userId: String,
    val clientId: String,
    val name: String,
    val description: String,
    val coverPosterUrl: String = "",
    val visibility: String = "private",
    val syncVersion: Int = 1,
    val itemIds: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean,
)

class SupabasePostgrestService {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun tableUrl(
        table: String,
        queryParams: Map<String, String> = emptyMap(),
    ): String {
        SupabaseConfig.securityWarning?.let { throw IOException(it) }
        val builder = "${SupabaseConfig.baseUrl}/rest/v1/$table".toHttpUrl().newBuilder()
        queryParams.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun baseRequestBuilder(url: String, session: SupabaseSession): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")

    private fun executeJson(request: Request): String {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401) {
                throw AuthExpiredException()
            }
            if (!response.isSuccessful) {
                val msg = runCatching {
                    val obj = JSONObject(raw)
                    obj.optString("message")
                        .ifBlank { obj.optString("hint") }
                        .ifBlank { obj.optString("error") }
                }.getOrNull().orEmpty()
                throw IOException(
                    if (msg.isNotBlank()) "Cloud sync error (${response.code}): $msg"
                    else "Cloud sync error (${response.code}).",
                )
            }
            return raw
        }
    }

    private fun JSONObject.optNullableRating(key: String): Int? {
        if (isNull(key)) return null
        val value = optInt(key, 0)
        return value.takeIf { it in 1..10 }
    }

    private fun sanitizeHttpsUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (MetadataService.isValidHttpsUrl(trimmed)) trimmed else ""
    }

    private fun sanitizeStatus(raw: String): String =
        if (raw in VALID_STATUSES) raw else "unwatched"

    private fun sanitizeDiscoverMode(raw: String): String =
        if (raw in VALID_DISCOVER_MODES) raw else "grid"

    private fun sanitizeVisibility(raw: String, allowUnlisted: Boolean): String =
        when (raw) {
            "private", "public" -> raw
            "unlisted" -> if (allowUnlisted) raw else "private"
            else -> "private"
        }

    // -------------------------------------------------------------------------
    // A. User Profiles (public.user_profiles) — NEVER sends `role` from client
    // -------------------------------------------------------------------------

    suspend fun fetchProfile(session: SupabaseSession): RemoteProfile? = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_profiles",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "user_id,display_name,tagline,default_discover_mode,updated_at",
                "limit" to "1",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        if (arr.length() == 0) return@withContext null
        val obj = arr.getJSONObject(0)
        RemoteProfile(
            userId = obj.optString("user_id", session.userId),
            displayName = obj.optString("display_name", "Cinema Archivist"),
            tagline = obj.optString("tagline", ""),
            defaultDiscoverMode = sanitizeDiscoverMode(obj.optString("default_discover_mode", "grid")),
            updatedAt = IsoTime.parseEpochMs(obj.optString("updated_at")),
        )
    }

    suspend fun upsertProfile(session: SupabaseSession, profile: RemoteProfile) = withContext(Dispatchers.IO) {
        val url = tableUrl("user_profiles", mapOf("on_conflict" to "user_id"))
        val safeName = profile.displayName.trim().ifBlank { "Cinema Archivist" }.take(60)
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("display_name", safeName)
            put("tagline", profile.tagline.take(160))
            put("default_discover_mode", sanitizeDiscoverMode(profile.defaultDiscoverMode))
            put("updated_at", IsoTime.formatEpochMs(profile.updatedAt))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    // -------------------------------------------------------------------------
    // B. Personal Library Entries (public.user_library_entries)
    // -------------------------------------------------------------------------

    suspend fun fetchAllLibraryEntries(session: SupabaseSession): List<RemoteLibraryEntry> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_library_entries",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "user_id,title_id,status,personal_rating,private_notes,is_favorite,rewatch_count,custom_poster_url,sync_version,is_deleted,deleted_at,updated_at",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val titleId = obj.optString("title_id", "")
                if (titleId.isBlank()) continue
                val deletedAtMs = IsoTime.parseEpochMs(obj.optString("deleted_at")).takeIf { it > 0L }
                add(
                    RemoteLibraryEntry(
                        userId = obj.optString("user_id", session.userId),
                        titleId = titleId,
                        status = sanitizeStatus(obj.optString("status", "unwatched")),
                        personalRating = obj.optNullableRating("personal_rating"),
                        privateNotes = obj.optString("private_notes", ""),
                        isFavorite = obj.optBoolean("is_favorite", false),
                        rewatchCount = obj.optInt("rewatch_count", 0).coerceAtLeast(0),
                        customPosterUrl = obj.optString("custom_poster_url", ""),
                        syncVersion = obj.optInt("sync_version", 1).coerceAtLeast(0),
                        isDeleted = obj.optBoolean("is_deleted", false),
                        deletedAt = deletedAtMs,
                        updatedAt = IsoTime.parseEpochMs(obj.optString("updated_at")),
                    ),
                )
            }
        }
    }

    suspend fun upsertLibraryEntry(session: SupabaseSession, entry: RemoteLibraryEntry) = withContext(Dispatchers.IO) {
        val url = tableUrl("user_library_entries", mapOf("on_conflict" to "user_id,title_id"))
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("title_id", entry.titleId)
            put("status", sanitizeStatus(entry.status))
            put("personal_rating", entry.personalRating?.takeIf { it in 1..10 } ?: JSONObject.NULL)
            put("private_notes", entry.privateNotes.take(2000))
            put("is_favorite", entry.isFavorite)
            put("rewatch_count", entry.rewatchCount.coerceAtLeast(0))
            put("custom_poster_url", sanitizeHttpsUrl(entry.customPosterUrl))
            put("sync_version", entry.syncVersion.coerceAtLeast(1))
            put("is_deleted", entry.isDeleted)
            put(
                "deleted_at",
                if (entry.isDeleted) IsoTime.formatEpochMs(entry.deletedAt ?: entry.updatedAt) else JSONObject.NULL,
            )
            put("updated_at", IsoTime.formatEpochMs(entry.updatedAt))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    // -------------------------------------------------------------------------
    // C. Reviews (public.reviews)
    // -------------------------------------------------------------------------

    suspend fun fetchAllReviews(session: SupabaseSession): List<RemoteReview> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "reviews",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "user_id,title_id,rating,body,contains_spoilers,visibility,sync_version,is_deleted,updated_at",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val titleId = obj.optString("title_id", "")
                if (titleId.isBlank()) continue
                add(
                    RemoteReview(
                        userId = obj.optString("user_id", session.userId),
                        titleId = titleId,
                        rating = obj.optNullableRating("rating"),
                        body = obj.optString("body", ""),
                        containsSpoilers = obj.optBoolean("contains_spoilers", false),
                        visibility = sanitizeVisibility(obj.optString("visibility", "private"), allowUnlisted = false),
                        syncVersion = obj.optInt("sync_version", 1).coerceAtLeast(0),
                        isDeleted = obj.optBoolean("is_deleted", false),
                        updatedAt = IsoTime.parseEpochMs(obj.optString("updated_at")),
                    ),
                )
            }
        }
    }

    suspend fun upsertReview(session: SupabaseSession, review: RemoteReview) = withContext(Dispatchers.IO) {
        val url = tableUrl("reviews", mapOf("on_conflict" to "user_id,title_id"))
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("title_id", review.titleId)
            put("rating", review.rating?.takeIf { it in 1..10 } ?: JSONObject.NULL)
            put("body", review.body.take(4000))
            put("contains_spoilers", review.containsSpoilers)
            put("visibility", sanitizeVisibility(review.visibility, allowUnlisted = false))
            put("sync_version", review.syncVersion.coerceAtLeast(1))
            put("is_deleted", review.isDeleted)
            put("updated_at", IsoTime.formatEpochMs(review.updatedAt))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    // -------------------------------------------------------------------------
    // D. Normalized User Tags (public.user_tags & public.user_title_tags)
    // -------------------------------------------------------------------------

    suspend fun fetchUserTags(session: SupabaseSession): List<RemoteUserTag> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_tags",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "id,name",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "").trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(RemoteUserTag(id = id, name = name))
                }
            }
        }
    }

    suspend fun fetchUserTitleTagIds(session: SupabaseSession): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_title_tags",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "title_id,tag_id",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        val grouped = linkedMapOf<String, MutableList<String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val titleId = obj.optString("title_id", "")
            val tagId = obj.optString("tag_id", "")
            if (titleId.isNotBlank() && tagId.isNotBlank()) {
                grouped.getOrPut(titleId) { mutableListOf() }.add(tagId)
            }
        }
        grouped
    }

    private fun createUserTagSync(session: SupabaseSession, cleanName: String): RemoteUserTag? {
        val url = tableUrl("user_tags")
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("name", cleanName)
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401) throw AuthExpiredException()
            if (response.code == 409) {
                // Concurrent device inserted the same case-insensitive tag; caller will re-fetch
                return null
            }
            if (!response.isSuccessful) {
                throw IOException("Cloud sync error (${response.code}) while saving tag.")
            }
            val arr = JSONArray(raw.ifBlank { "[]" })
            if (arr.length() == 0) return null
            val obj = arr.getJSONObject(0)
            val id = obj.optString("id", "")
            val name = obj.optString("name", cleanName)
            return if (id.isNotBlank()) RemoteUserTag(id = id, name = name) else null
        }
    }

    /**
     * Replaces all tag links for a single (user_id, title_id) pair.
     * Mutates [lowerNameToTagId] in place when creating new tags so subsequent titles reuse IDs.
     */
    suspend fun replaceTitleTags(
        session: SupabaseSession,
        titleId: String,
        tagNames: List<String>,
        lowerNameToTagId: MutableMap<String, String>,
    ) = withContext(Dispatchers.IO) {
        val cleanNames = tagNames
            .map { it.trim().take(40) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }

        val resolvedTagIds = mutableListOf<String>()
        var needsRefetch = false
        for (name in cleanNames) {
            val key = name.lowercase(Locale.ROOT)
            val existingId = lowerNameToTagId[key]
            if (existingId != null) {
                resolvedTagIds += existingId
            } else {
                val created = createUserTagSync(session, name)
                if (created != null) {
                    lowerNameToTagId[key] = created.id
                    resolvedTagIds += created.id
                } else {
                    needsRefetch = true
                }
            }
        }

        if (needsRefetch) {
            fetchUserTags(session).forEach { tag ->
                lowerNameToTagId[tag.name.lowercase(Locale.ROOT)] = tag.id
            }
            resolvedTagIds.clear()
            cleanNames.forEach { name ->
                lowerNameToTagId[name.lowercase(Locale.ROOT)]?.let { resolvedTagIds += it }
            }
        }

        // Delete existing links for this title
        val deleteUrl = tableUrl(
            table = "user_title_tags",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "title_id" to "eq.$titleId",
            ),
        )
        executeJson(baseRequestBuilder(deleteUrl, session).delete().build())

        // Insert new links if any
        if (resolvedTagIds.isNotEmpty()) {
            val insertUrl = tableUrl("user_title_tags", mapOf("on_conflict" to "user_id,title_id,tag_id"))
            val bodyArray = JSONArray()
            resolvedTagIds.distinct().forEach { tagId ->
                bodyArray.put(
                    JSONObject().apply {
                        put("user_id", session.userId)
                        put("title_id", titleId)
                        put("tag_id", tagId)
                    },
                )
            }
            val req = baseRequestBuilder(insertUrl, session)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(bodyArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeJson(req)
        }
    }

    // -------------------------------------------------------------------------
    // E. Viewing Logs (public.viewing_logs)
    // -------------------------------------------------------------------------

    suspend fun fetchViewingLogs(session: SupabaseSession): List<RemoteViewingLog> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "viewing_logs",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "user_id,title_id,client_log_key,logged_at,action_label,rating_at_log,is_rewatch,note",
                "order" to "logged_at.desc",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val key = obj.optString("client_log_key", "")
                val titleId = obj.optString("title_id", "")
                if (key.isNotBlank() && titleId.isNotBlank()) {
                    add(
                        RemoteViewingLog(
                            userId = obj.optString("user_id", session.userId),
                            clientLogKey = key,
                            titleId = titleId,
                            loggedAt = IsoTime.parseEpochMs(obj.optString("logged_at")),
                            actionLabel = obj.optString("action_label", ""),
                            ratingAtLog = obj.optNullableRating("rating_at_log"),
                            isRewatch = obj.optBoolean("is_rewatch", false),
                            note = obj.optString("note", ""),
                        ),
                    )
                }
            }
        }
    }

    suspend fun upsertViewingLog(session: SupabaseSession, log: RemoteViewingLog) = withContext(Dispatchers.IO) {
        val url = tableUrl("viewing_logs", mapOf("on_conflict" to "user_id,client_log_key"))
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("title_id", log.titleId)
            put("client_log_key", log.clientLogKey)
            put("logged_at", IsoTime.formatEpochMs(log.loggedAt))
            put("action_label", log.actionLabel.ifBlank { "Logged activity" })
            put("rating_at_log", log.ratingAtLog?.takeIf { it in 1..10 } ?: JSONObject.NULL)
            put("is_rewatch", log.isRewatch)
            put("note", log.note.take(500))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun deleteViewingLog(session: SupabaseSession, clientLogKey: String) = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "viewing_logs",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "client_log_key" to "eq.$clientLogKey",
            ),
        )
        executeJson(baseRequestBuilder(url, session).delete().build())
    }

    // -------------------------------------------------------------------------
    // F. Watched Episodes (public.user_watched_episodes)
    // -------------------------------------------------------------------------

    suspend fun fetchWatchedEpisodes(session: SupabaseSession): List<RemoteWatchedEpisode> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_watched_episodes",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "user_id,title_id,season_number,episode_number,watched_at",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val titleId = obj.optString("title_id", "")
                val season = obj.optInt("season_number", 0)
                val episode = obj.optInt("episode_number", 0)
                if (titleId.isNotBlank() && season > 0 && episode > 0) {
                    add(
                        RemoteWatchedEpisode(
                            userId = obj.optString("user_id", session.userId),
                            titleId = titleId,
                            seasonNumber = season,
                            episodeNumber = episode,
                            watchedAt = IsoTime.parseEpochMs(obj.optString("watched_at")),
                        ),
                    )
                }
            }
        }
    }

    suspend fun upsertWatchedEpisode(session: SupabaseSession, ep: RemoteWatchedEpisode) = withContext(Dispatchers.IO) {
        if (ep.seasonNumber <= 0 || ep.episodeNumber <= 0) return@withContext
        val url = tableUrl(
            "user_watched_episodes",
            mapOf("on_conflict" to "user_id,title_id,season_number,episode_number"),
        )
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("title_id", ep.titleId)
            put("season_number", ep.seasonNumber)
            put("episode_number", ep.episodeNumber)
            put("watched_at", IsoTime.formatEpochMs(ep.watchedAt))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    suspend fun deleteWatchedEpisode(
        session: SupabaseSession,
        titleId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ) = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "user_watched_episodes",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "title_id" to "eq.$titleId",
                "season_number" to "eq.$seasonNumber",
                "episode_number" to "eq.$episodeNumber",
            ),
        )
        executeJson(baseRequestBuilder(url, session).delete().build())
    }

    // -------------------------------------------------------------------------
    // G. Collections & Ordered Collection Items (public.collections & public.collection_items)
    // -------------------------------------------------------------------------

    suspend fun fetchCollections(session: SupabaseSession): List<RemoteCollection> = withContext(Dispatchers.IO) {
        val colsUrl = tableUrl(
            table = "collections",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "id,user_id,client_id,name,description,cover_poster_url,visibility,sync_version,is_deleted,created_at,updated_at",
            ),
        )
        val colsRaw = executeJson(baseRequestBuilder(colsUrl, session).get().build())
        val colsArr = JSONArray(colsRaw.ifBlank { "[]" })
        if (colsArr.length() == 0) return@withContext emptyList()

        val ownedIds = hashSetOf<String>()
        for (i in 0 until colsArr.length()) {
            val id = colsArr.getJSONObject(i).optString("id", "")
            if (id.isNotBlank()) ownedIds += id
        }

        // Fetch ordered items for the user's collections
        val itemsByCollectionId = hashMapOf<String, MutableList<Pair<Int, String>>>()
        if (ownedIds.isNotEmpty()) {
            val itemsUrl = tableUrl(
                table = "collection_items",
                queryParams = mapOf(
                    "select" to "collection_id,title_id,position",
                    "order" to "position.asc",
                ),
            )
            val itemsRaw = executeJson(baseRequestBuilder(itemsUrl, session).get().build())
            val itemsArr = JSONArray(itemsRaw.ifBlank { "[]" })
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val colId = obj.optString("collection_id", "")
                val titleId = obj.optString("title_id", "")
                val pos = obj.optInt("position", i)
                if (colId in ownedIds && titleId.isNotBlank()) {
                    itemsByCollectionId.getOrPut(colId) { mutableListOf() }.add(pos to titleId)
                }
            }
        }

        buildList(colsArr.length()) {
            for (i in 0 until colsArr.length()) {
                val obj = colsArr.getJSONObject(i)
                val id = obj.optString("id", "")
                val clientId = obj.optString("client_id", "")
                if (clientId.isBlank()) continue
                val orderedTitleIds = itemsByCollectionId[id]
                    ?.sortedBy { it.first }
                    ?.map { it.second }
                    .orEmpty()
                add(
                    RemoteCollection(
                        id = id,
                        userId = obj.optString("user_id", session.userId),
                        clientId = clientId,
                        name = obj.optString("name", "Untitled Collection"),
                        description = obj.optString("description", ""),
                        coverPosterUrl = obj.optString("cover_poster_url", ""),
                        visibility = sanitizeVisibility(obj.optString("visibility", "private"), allowUnlisted = true),
                        syncVersion = obj.optInt("sync_version", 1).coerceAtLeast(1),
                        itemIds = orderedTitleIds,
                        createdAt = IsoTime.parseEpochMs(obj.optString("created_at")),
                        updatedAt = IsoTime.parseEpochMs(obj.optString("updated_at")),
                        isDeleted = obj.optBoolean("is_deleted", false),
                    ),
                )
            }
        }
    }

    suspend fun upsertCollectionWithItems(session: SupabaseSession, col: RemoteCollection) = withContext(Dispatchers.IO) {
        val url = tableUrl("collections", mapOf("on_conflict" to "user_id,client_id"))
        val safeName = col.name.trim().ifBlank { "Untitled Collection" }.take(80)
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("client_id", col.clientId)
            put("name", safeName)
            put("description", col.description.take(300))
            put("cover_poster_url", sanitizeHttpsUrl(col.coverPosterUrl))
            put("visibility", sanitizeVisibility(col.visibility, allowUnlisted = true))
            put("sync_version", col.syncVersion.coerceAtLeast(1))
            put("is_deleted", col.isDeleted)
            put("created_at", IsoTime.formatEpochMs(col.createdAt))
            put("updated_at", IsoTime.formatEpochMs(col.updatedAt))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates,return=representation")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        val raw = executeJson(req)
        val arr = JSONArray(raw.ifBlank { "[]" })
        val collectionUuid = if (arr.length() > 0) arr.getJSONObject(0).optString("id", "") else col.id
        if (collectionUuid.isBlank()) return@withContext

        // Replace collection_items atomically per collection
        val deleteItemsUrl = tableUrl(
            table = "collection_items",
            queryParams = mapOf("collection_id" to "eq.$collectionUuid"),
        )
        executeJson(baseRequestBuilder(deleteItemsUrl, session).delete().build())

        if (!col.isDeleted && col.itemIds.isNotEmpty()) {
            val distinctItems = col.itemIds.filter { it.isNotBlank() }.distinct()
            val addedAtIso = IsoTime.formatEpochMs(col.updatedAt)
            val bodyArray = JSONArray()
            distinctItems.forEachIndexed { index, titleId ->
                bodyArray.put(
                    JSONObject().apply {
                        put("collection_id", collectionUuid)
                        put("title_id", titleId)
                        put("position", index)
                        put("added_at", addedAtIso)
                    },
                )
            }
            val insertItemsUrl = tableUrl("collection_items", mapOf("on_conflict" to "collection_id,title_id"))
            val insertReq = baseRequestBuilder(insertItemsUrl, session)
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(bodyArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeJson(insertReq)
        }
    }

    // -------------------------------------------------------------------------
    // H. Offline Idempotent Sync Ledger (public.sync_op_ledger)
    // -------------------------------------------------------------------------

    suspend fun fetchProcessedOpIds(session: SupabaseSession): Set<String> = withContext(Dispatchers.IO) {
        val url = tableUrl(
            table = "sync_op_ledger",
            queryParams = mapOf(
                "user_id" to "eq.${session.userId}",
                "select" to "op_id",
            ),
        )
        val raw = executeJson(baseRequestBuilder(url, session).get().build())
        val arr = JSONArray(raw.ifBlank { "[]" })
        buildSet(arr.length()) {
            for (i in 0 until arr.length()) {
                val id = arr.getJSONObject(i).optString("op_id", "")
                if (id.isNotBlank()) add(id)
            }
        }
    }

    suspend fun recordOpIdempotent(
        session: SupabaseSession,
        opId: String,
        entityType: String = "",
    ) = withContext(Dispatchers.IO) {
        val url = tableUrl("sync_op_ledger", mapOf("on_conflict" to "user_id,op_id"))
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("op_id", opId)
            put("entity_type", entityType)
            put("processed_at", IsoTime.formatEpochMs(System.currentTimeMillis()))
        }
        val req = baseRequestBuilder(url, session)
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        executeJson(req)
    }

    companion object {
        private val VALID_STATUSES = setOf(
            "unwatched",
            "bookmarked",
            "watching",
            "watched",
            "rewatching",
            "on_hold",
            "dropped",
        )
        private val VALID_DISCOVER_MODES = setOf("grid", "shelf", "orb")
    }
}
