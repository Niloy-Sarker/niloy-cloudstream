package com.niloy

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.util.Log
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*

// TMDB Data Classes
data class TmdbSearchResponse(
    @JsonProperty("results") val results: List<TmdbSearchResult>? = null
)

data class TmdbSearchResult(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("vote_average") val rating: Double? = null
)

data class TmdbDetails(
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("poster_path") val posterPath: String? = null,
    @JsonProperty("vote_average") val rating: Double? = null,
    @JsonProperty("release_date") val releaseDate: String? = null
)

data class TmdbEpisodeDetails(
    @JsonProperty("air_date") val airDate: String? = null,
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("season_number") val seasonNumber: Int? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("vote_average") val rating: Double? = null
)

data class TmdbSeasonDetails(
    @JsonProperty("episodes") val episodes: List<TmdbEpisodeDetails>? = null
)

object TmdbHelper {
    private const val TMDB_API = "https://api.themoviedb.org/3"
    private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    private const val TAG = "TmdbHelper"
    private var lastApiCallTime = 0L
    private const val API_CALL_DELAY = 250L // 250ms delay between API calls

    // TMDB image sizes for different contexts
    object ImageSizes {
        // Smallest sizes for initial loading
        const val POSTER_SMALL = "w92"      // Smallest poster size
        const val BACKDROP_SMALL = "w300"    // Smallest backdrop size
        const val STILL_SMALL = "w92"       // Smallest still size
        
        // Medium sizes for details views
        const val POSTER_MEDIUM = "w185"    // Medium poster size
        const val BACKDROP_MEDIUM = "w780"   // Medium backdrop size
        const val STILL_MEDIUM = "w185"     // Medium still size
        
        // Large sizes for full screen/detailed views
        const val POSTER_LARGE = "w342"     // Large poster size
        const val BACKDROP_LARGE = "w1280"   // Large backdrop size
        const val STILL_LARGE = "w300"      // Large still size
    }

    private fun getApiKey(): String {
        return try {
            DhakaFlixSettingsManager.getApiKey() ?: return ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API key: ${e.message}")
            ""
        }
    }

    // Helper function to get appropriate image size based on context
    fun getTmdbImageUrl(path: String?, size: String = ImageSizes.POSTER_SMALL): String? {
        if (path == null) return null
        return "$TMDB_IMAGE_BASE_URL/$size$path"
    }

    // Helper function to get poster URL with appropriate size
    fun getPosterUrl(path: String?, isDetail: Boolean = false): String? {
        val size = when {
            isDetail -> ImageSizes.POSTER_MEDIUM
            else -> ImageSizes.POSTER_SMALL
        }
        return getTmdbImageUrl(path, size)
    }

    // Helper function to get backdrop URL with appropriate size
    fun getBackdropUrl(path: String?, isDetail: Boolean = false): String? {
        val size = when {
            isDetail -> ImageSizes.BACKDROP_MEDIUM
            else -> ImageSizes.BACKDROP_SMALL
        }
        return getTmdbImageUrl(path, size)
    }

    // Helper function to get still URL with appropriate size
    fun getStillUrl(path: String?, isDetail: Boolean = false): String? {
        val size = when {
            isDetail -> ImageSizes.STILL_MEDIUM
            else -> ImageSizes.STILL_SMALL
        }
        return getTmdbImageUrl(path, size)
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val shorter = if (s1.length < s2.length) s1 else s2
        val longer = if (s1.length < s2.length) s2 else s1
        
        // If the longer string contains the shorter one entirely
        if (longer.contains(shorter)) return 1.0
        
        // Count matching words
        val words1 = s1.split(" ").filter { it.length > 2 }
        val words2 = s2.split(" ").filter { it.length > 2 }
        
        var matches = 0
        for (word in words1) {
            if (words2.any { it.contains(word) || word.contains(it) }) {
                matches++
            }
        }
        
        return matches.toDouble() / maxOf(words1.size, words2.size)
    }

    private suspend fun makeApiCall(url: String): String? {
        return try {
            // Implement rate limiting
            val currentTime = System.currentTimeMillis()
            val timeSinceLastCall = currentTime - lastApiCallTime
            if (timeSinceLastCall < API_CALL_DELAY) {
                delay(API_CALL_DELAY - timeSinceLastCall)
            }
            
            val response = app.get(url).text
            lastApiCallTime = System.currentTimeMillis()
            response
        } catch (e: Exception) {
            Log.e(TAG, "API call failed: ${e.message}")
            null
        }
    }

    suspend fun searchTmdb(title: String, isMovie: Boolean = true): TmdbSearchResult? {
        // Check if TMDB is enabled first
        if (!DhakaFlixSettingsManager.isTmdbEnabled()) {
            Log.d(TAG, "TMDB integration is disabled, skipping search")
            return null
        }
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.d(TAG, "No API key set, skipping TMDB search")
            return null
        }

        val type = if (isMovie) "movie" else "tv"
        val cleanTitle = title.lowercase()
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\d{3,4}p"), "")
            .replace(Regex("\\.(mkv|mp4|avi)"), "")
            .trim()
        
        val url = "$TMDB_API/search/$type?api_key=$apiKey&query=${cleanTitle.encodeUrl()}"
        
        return try {
            val response = makeApiCall(url) ?: return null
            val results = parseJson<TmdbSearchResponse>(response)
            results.results?.maxByOrNull { result ->
                val resultTitle = (result.title ?: result.name ?: "").lowercase()
                calculateSimilarity(cleanTitle, resultTitle)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching TMDB: ${e.message}")
            null
        }
    }

    suspend fun getTmdbDetails(id: Int, isMovie: Boolean = true): TmdbDetails? {
        // Check if TMDB is enabled first
        if (!DhakaFlixSettingsManager.isTmdbEnabled()) {
            Log.d(TAG, "TMDB integration is disabled, skipping details")
            return null
        }
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.d(TAG, "No API key set, skipping TMDB details")
            return null
        }

        val type = if (isMovie) "movie" else "tv"
        val url = "$TMDB_API/$type/$id?api_key=$apiKey"
        
        return try {
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting TMDB details: ${e.message}")
            null
        }
    }

    // Helper function to parse episode number from filename
    private fun parseEpisodeNumberFromFilename(filename: String): Int? {
        // Match patterns like S01E00, S1E0, etc.
        val episodePattern = Regex("[Ss](\\d{1,2})[Ee](\\d{1,3})")
        val match = episodePattern.find(filename)
        return match?.groupValues?.getOrNull(2)?.toIntOrNull()
    }

    suspend fun getEpisodeDetails(
        tmdbId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        filename: String? = null
    ): TmdbEpisodeDetails? {
        // Check if TMDB is enabled first
        if (!DhakaFlixSettingsManager.isTmdbEnabled()) {
            Log.d(TAG, "TMDB integration is disabled, skipping episode details")
            return null
        }
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.d(TAG, "No API key set, skipping episode details")
            return null
        }

        // Use episode number from filename if available, otherwise use provided episodeNumber
        val actualEpisodeNumber = filename?.let { parseEpisodeNumberFromFilename(it) } ?: episodeNumber

        return try {
            val url = "$TMDB_API/tv/$tmdbId/season/$seasonNumber/episode/$actualEpisodeNumber?api_key=$apiKey"
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting episode details: ${e.message}")
            null
        }
    }

    suspend fun getSeasonDetails(
        tmdbId: Int,
        seasonNumber: Int
    ): TmdbSeasonDetails? {
        // Check if TMDB is enabled first
        if (!DhakaFlixSettingsManager.isTmdbEnabled()) {
            Log.d(TAG, "TMDB integration is disabled, skipping season details")
            return null
        }
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.d(TAG, "No API key set, skipping season details")
            return null
        }

        return try {
            val url = "$TMDB_API/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey"
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting season details: ${e.message}")
            null
        }
    }

    // Bulk load all season details for a TV show
    suspend fun getAllSeasonDetails(
        tmdbId: Int,
        seasonNumbers: List<Int>
    ): Map<Int, TmdbSeasonDetails?> = coroutineScope {
        if (!DhakaFlixSettingsManager.isTmdbEnabled()) {
            Log.d(TAG, "TMDB integration is disabled, skipping bulk season details")
            return@coroutineScope emptyMap()
        }
        
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            Log.d(TAG, "No API key set, skipping bulk season details")
            return@coroutineScope emptyMap()
        }

        val results = mutableMapOf<Int, TmdbSeasonDetails?>()
        
        // Process seasons in batches to avoid overwhelming the API
        seasonNumbers.chunked(3).forEach { batch ->
            val batchResults = batch.map { seasonNumber ->
                async {
                    seasonNumber to getSeasonDetails(tmdbId, seasonNumber)
                }
            }.awaitAll().toMap()
            
            results.putAll(batchResults)
        }
        
        return@coroutineScope results
    }

    // Get a specific episode from cached season data
    fun getEpisodeFromSeasonData(
        seasonData: TmdbSeasonDetails?,
        episodeNumber: Int
    ): TmdbEpisodeDetails? {
        return seasonData?.episodes?.find { it.episodeNumber == episodeNumber }
    }

    private fun String.encodeUrl(): String {
        return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
    }

    private suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
} 