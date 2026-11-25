package com.example.data.remote

import com.example.model.CatalogItem
import com.example.model.MediaType
import com.example.model.ResolvedMetadata
import com.example.model.SeriesEpisode
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class MetadataService {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchMetadata(item: CatalogItem): ResolvedMetadata = withContext(Dispatchers.IO) {
        var poster = ""
        var desc = item.curated.description
        var genre = item.curated.genre
        var runtime = item.curated.runtime
        var network = ""
        var wikiUrl = defaultWikiSearchUrl(item)
        var tvmazeShowId = 0
        val cleanTitle = cleanQueryTitle(item.title)

        if (item.type == MediaType.SERIES) {
            // 1. TVmaze is the cleanest source for 2:3 TV series posters, summaries & show IDs
            val tv = runCatching { fetchTvmaze(cleanTitle, item.year) }.getOrNull()
            if (tv != null) {
                if (tv.showId > 0) tvmazeShowId = tv.showId
                if (tv.poster.isNotBlank()) poster = tv.poster
                if (desc.isBlank() && tv.desc.isNotBlank()) desc = tv.desc
                if (genre.isBlank() && tv.genre.isNotBlank()) genre = tv.genre
                if (tv.network.isNotBlank()) network = tv.network
                if (runtime.isBlank() && tv.runtime.isNotBlank()) runtime = tv.runtime
            }
            // 2. Fallback to iTunes TV Show artwork if TVmaze lacked a poster
            if (poster.isBlank()) {
                val itunesTv = runCatching { fetchItunesMedia(cleanTitle, item.year, media = "tvShow") }.getOrNull()
                if (itunesTv != null) {
                    if (itunesTv.poster.isNotBlank()) poster = itunesTv.poster
                    if (desc.isBlank() && itunesTv.desc.isNotBlank()) desc = itunesTv.desc
                    if (genre.isBlank() && itunesTv.genre.isNotBlank()) genre = itunesTv.genre
                }
            }
            // 3. Enrich with Wikipedia summary & link (or fallback thumbnail)
            if (poster.isBlank() || desc.isBlank()) {
                val wiki = runCatching {
                    fetchWikipedia(item, "$cleanTitle ${item.year} television series")
                        ?: fetchWikipedia(item, "$cleanTitle TV series")
                }.getOrNull()
                if (wiki != null) {
                    if (poster.isBlank() && wiki.poster.isNotBlank()) poster = wiki.poster
                    if (desc.isBlank() && wiki.desc.isNotBlank()) desc = wiki.desc
                    if (wiki.wikiUrl.isNotBlank()) wikiUrl = wiki.wikiUrl
                }
            }
        } else {
            // 1. iTunes Search API first for Movies: official high-res portrait posters on mzstatic.com
            val itunes = runCatching { fetchItunesMedia(cleanTitle, item.year, media = "movie") }.getOrNull()
            if (itunes != null) {
                if (itunes.poster.isNotBlank()) poster = itunes.poster
                if (desc.isBlank() && itunes.desc.isNotBlank()) desc = itunes.desc
                if (genre.isBlank() && itunes.genre.isNotBlank()) genre = itunes.genre
            }
            // 2. Wikipedia for encyclopedic synopsis, canonical wikiUrl, and poster fallback
            if (poster.isBlank() || desc.isBlank()) {
                val wiki = runCatching {
                    fetchWikipedia(item, "$cleanTitle ${item.year} film")
                        ?: fetchWikipedia(item, "$cleanTitle film")
                }.getOrNull()
                if (wiki != null) {
                    if (poster.isBlank() && wiki.poster.isNotBlank()) poster = wiki.poster
                    if (desc.isBlank() && wiki.desc.isNotBlank()) desc = wiki.desc
                    if (wiki.wikiUrl.isNotBlank()) wikiUrl = wiki.wikiUrl
                }
            }
        }

        val succeeded = poster.isNotBlank() || desc.isNotBlank()
        ResolvedMetadata(
            posterUrl = poster,
            description = desc,
            genre = genre,
            director = item.curated.director,
            country = item.curated.country,
            language = item.curated.language,
            runtime = runtime,
            network = network,
            wikiUrl = wikiUrl,
            tvmazeShowId = tvmazeShowId,
            fetched = succeeded,
            failedAt = if (succeeded) 0L else System.currentTimeMillis(),
        )
    }

    suspend fun fetchSeriesEpisodes(item: CatalogItem, knownShowId: Int): Pair<Int, List<SeriesEpisode>> =
        withContext(Dispatchers.IO) {
            if (item.type != MediaType.SERIES) return@withContext 0 to emptyList()
            val showId = if (knownShowId > 0) {
                knownShowId
            } else {
                runCatching { fetchTvmaze(cleanQueryTitle(item.title), item.year)?.showId ?: 0 }
                    .getOrDefault(0)
            }
            if (showId <= 0) return@withContext 0 to emptyList()

            val raw = runCatching { getString("https://api.tvmaze.com/shows/$showId/episodes") }.getOrNull()
                ?: return@withContext showId to emptyList()
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return@withContext showId to emptyList()
            val episodes = ArrayList<SeriesEpisode>(arr.length())
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                val season = ep.optInt("season", 0)
                val number = ep.optInt("number", 0)
                if (season <= 0 || number <= 0) continue
                val name = ep.optString("name", "Episode $number").ifBlank { "Episode $number" }
                val airDate = ep.optString("airdate", "")
                val runtime = ep.optInt("runtime", 0)
                val summary = stripHtml(ep.optString("summary", ""))
                episodes.add(
                    SeriesEpisode(
                        itemId = item.id,
                        season = season,
                        episode = number,
                        title = name,
                        airDate = airDate,
                        runtimeMinutes = runtime,
                        summary = summary,
                    ),
                )
            }
            showId to episodes
        }

    private fun getString(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", CLIENT_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }

    private data class WikiResult(val poster: String, val desc: String, val wikiUrl: String)

    private fun fetchWikipedia(item: CatalogItem, query: String): WikiResult? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded" +
            "&gsrlimit=5&prop=pageimages|extracts|info&inprop=url&piprop=thumbnail&pithumbsize=500" +
            "&pilicense=any&exintro=1&explaintext=1&exsentences=3&format=json"
        val raw = getString(url) ?: return null
        val pages = JSONObject(raw).optJSONObject("query")?.optJSONObject("pages") ?: return null

        val normTarget = normalize(item.title)
        val yearStr = item.year.toString()
        val mediaWords = if (item.type == MediaType.MOVIE) {
            listOf("film", "movie")
        } else {
            listOf("series", "television", "tv", "miniseries", "anime")
        }

        var bestPage: JSONObject? = null
        var bestScore = -1
        var bestIndex = Int.MAX_VALUE

        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val pTitleRaw = page.optString("title", "")
            val pTitle = normalize(pTitleRaw)
            val pExt = page.optString("extract", "").lowercase()
            val thumb = page.optJSONObject("thumbnail")?.optString("source", "") ?: ""
            var score = 0
            if (pTitle.contains(normTarget) || normTarget.contains(pTitle)) score += 4
            if (pTitleRaw.contains(yearStr) || pExt.contains(yearStr)) score += 3
            if (mediaWords.any { w -> pExt.contains(w) || pTitleRaw.lowercase().contains(w) }) score += 2
            if (isValidHttpsUrl(thumb)) score += 1
            val idx = page.optInt("index", 99)
            if (score > bestScore || (score == bestScore && idx < bestIndex)) {
                bestScore = score
                bestIndex = idx
                bestPage = page
            }
        }

        if (bestPage == null || bestScore < 4) return null
        val thumb = bestPage.optJSONObject("thumbnail")?.optString("source", "") ?: ""
        val fullUrl = bestPage.optString("fullurl", "")
        val titleSlug = bestPage.optString("title", "").replace(" ", "_")
        val safeUrl = when {
            isValidHttpsUrl(fullUrl) -> fullUrl
            titleSlug.isNotBlank() -> "https://en.wikipedia.org/wiki/${URLEncoder.encode(titleSlug, "UTF-8")}"
            else -> defaultWikiSearchUrl(item)
        }
        return WikiResult(
            poster = if (isValidHttpsUrl(thumb)) thumb else "",
            desc = bestPage.optString("extract", "").trim(),
            wikiUrl = safeUrl,
        )
    }

    private data class ItunesResult(val poster: String, val desc: String, val genre: String)

    private fun fetchItunesMedia(title: String, year: Int, media: String): ItunesResult? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val entity = if (media == "tvShow") "&entity=tvSeason" else "&entity=movie"
        val url = "https://itunes.apple.com/search?term=$encoded&media=$media$entity&limit=10"
        val raw = getString(url) ?: return null
        val results = JSONObject(raw).optJSONArray("results") ?: return null
        val norm = normalize(title)

        var bestObj: JSONObject? = null
        var bestScore = -1

        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val rawName = if (media == "tvShow") {
                obj.optString("collectionName", obj.optString("artistName", ""))
            } else {
                obj.optString("trackName", "")
            }
            val rName = normalize(rawName)
            if (rName.isBlank()) continue
            val art = obj.optString("artworkUrl100", "")
            if (!isValidHttpsUrl(art)) continue

            val releaseDate = obj.optString("releaseDate", "")
            val rYear = releaseDate.take(4).toIntOrNull() ?: 0
            var score = 0
            when {
                rName == norm -> score += 10
                rName.startsWith(norm) || norm.startsWith(rName) -> score += 7
                rName.contains(norm) || norm.contains(rName) -> score += 4
                else -> continue
            }
            if (rYear > 0) {
                val diff = abs(rYear - year)
                when {
                    diff == 0 -> score += 6
                    diff <= 2 -> score += 4
                    diff <= 5 -> score += 2
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestObj = obj
            }
        }

        val chosen = bestObj ?: return null
        if (bestScore < 7) return null
        val art = chosen.optString("artworkUrl100", "")
        val highResPoster = art
            .replace("100x100bb", "600x900bb")
            .replace("100x100", "600x900")
        return ItunesResult(
            poster = highResPoster,
            desc = chosen.optString("longDescription", chosen.optString("shortDescription", "")).trim(),
            genre = chosen.optString("primaryGenreName", "").trim(),
        )
    }

    private data class TvmazeResult(
        val showId: Int,
        val poster: String,
        val desc: String,
        val genre: String,
        val network: String,
        val runtime: String,
    )

    private fun fetchTvmaze(title: String, year: Int): TvmazeResult? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val url = "https://api.tvmaze.com/search/shows?q=$encoded"
        val raw = getString(url) ?: return null
        val arr = JSONArray(raw)
        if (arr.length() == 0) return null
        val norm = normalize(title)
        var bestShow: JSONObject? = null
        var bestScore = -1

        for (i in 0 until arr.length()) {
            val show = arr.optJSONObject(i)?.optJSONObject("show") ?: continue
            val name = show.optString("name", "")
            if (name.isBlank()) continue
            val sn = normalize(name)
            val sy = show.optString("premiered", "").take(4).toIntOrNull() ?: 0
            var score = 0
            when {
                sn == norm -> score += 10
                sn.contains(norm) || norm.contains(sn) -> score += 6
                else -> continue
            }
            if (sy > 0) {
                val diff = abs(sy - year)
                when {
                    diff == 0 -> score += 6
                    diff <= 2 -> score += 4
                    diff <= 4 -> score += 1
                }
            }
            val hasImg = show.optJSONObject("image") != null
            if (hasImg) score += 2
            if (score > bestScore) {
                bestScore = score
                bestShow = show
            }
        }
        val chosen = bestShow ?: return null
        val imgObj = chosen.optJSONObject("image")
        val rawImg = (imgObj?.optString("medium", "")?.ifBlank { imgObj.optString("original", "") } ?: "")
            .replace(Regex("^http:"), "https:")
        val genresArr = chosen.optJSONArray("genres")
        val genres = buildList {
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optString(i, "").trim()
                    if (g.isNotBlank()) add(g)
                }
            }
        }.joinToString(", ")
        val network = chosen.optJSONObject("network")?.optString("name", "")?.ifBlank { null }
            ?: chosen.optJSONObject("webChannel")?.optString("name", "") ?: ""
        val avgRuntime = chosen.optInt("averageRuntime", 0)

        return TvmazeResult(
            showId = chosen.optInt("id", 0),
            poster = if (isValidHttpsUrl(rawImg)) rawImg else "",
            desc = stripHtml(chosen.optString("summary", "")),
            genre = genres,
            network = network,
            runtime = if (avgRuntime > 0) "$avgRuntime min per episode" else "",
        )
    }

    companion object {
        const val CLIENT_USER_AGENT = "MuvantaArchive/2.0 (https://muvanta.app; Android Cinema Archive)"

        fun isValidHttpsUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            if (trimmed.length > 1000 || trimmed.any { it.isISOControl() || it.isWhitespace() }) return false
            val parsed = trimmed.toHttpUrlOrNull() ?: return false
            if (!parsed.isHttps) return false
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
            return isPublicHost(parsed.host)
        }

        fun isValidWikipediaUrl(url: String?): Boolean {
            if (!isValidHttpsUrl(url)) return false
            val host = url!!.trim().toHttpUrlOrNull()?.host?.lowercase() ?: return false
            return host == "wikipedia.org" || host.endsWith(".wikipedia.org")
        }

        fun isPublicHost(rawHost: String): Boolean {
            val host = rawHost.trim().lowercase().removePrefix("[").removeSuffix("]")
            if (host.isBlank() || host == "localhost" || host.endsWith(".localhost")) return false
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan")) return false

            // Reject IPv6 loopback, link-local, unique-local, and unspecified addresses
            if (host.contains(':')) {
                if (host == "::" || host == "::1" || host.startsWith("fe80:") ||
                    host.startsWith("fc") || host.startsWith("fd") || host.startsWith("::ffff:")
                ) {
                    return false
                }
                return true
            }

            // Require a qualified public domain name or public IPv4 address
            val ipv4Parts = host.split(".")
            if (ipv4Parts.size == 4 && ipv4Parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }) {
                val a = ipv4Parts[0].toInt()
                val b = ipv4Parts[1].toInt()
                if (a == 0 || a == 10 || a == 127) return false
                if (a == 100 && b in 64..127) return false
                if (a == 169 && b == 254) return false
                if (a == 172 && b in 16..31) return false
                if (a == 192 && b == 168) return false
                if (a >= 224) return false
                return true
            }

            return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
        }

        fun defaultWikiSearchUrl(item: CatalogItem): String {
            val encoded = URLEncoder.encode("${item.title} ${item.year}", "UTF-8")
            return "https://en.wikipedia.org/wiki/Special:Search?search=$encoded"
        }

        private fun cleanQueryTitle(title: String): String =
            title.replace(Regex("\\s*\\(US\\)$|\\s*\\(UK\\)$"), "").trim()

        private fun normalize(value: String): String =
            value.lowercase().replace(Regex("[^a-z0-9]"), "")

        private fun stripHtml(html: String): String =
            html.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
    }
}
