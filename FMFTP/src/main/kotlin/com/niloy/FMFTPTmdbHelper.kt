package com.niloy

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.util.Log

// TMDB Data Classes
data class TmdbMovieDetails(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("title") val title: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null,
    @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
    @param:JsonProperty("vote_average") val rating: Double? = null,
    @param:JsonProperty("release_date") val releaseDate: String? = null,
    @param:JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @param:JsonProperty("runtime") val runtime: Int? = null,
    @param:JsonProperty("tagline") val tagline: String? = null
)

data class TmdbTvDetails(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null,
    @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
    @param:JsonProperty("vote_average") val rating: Double? = null,
    @param:JsonProperty("first_air_date") val firstAirDate: String? = null,
    @param:JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @param:JsonProperty("number_of_seasons") val numberOfSeasons: Int? = null,
    @param:JsonProperty("seasons") val seasons: List<TmdbSeasonInfo>? = null
)

data class TmdbGenre(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("name") val name: String? = null
)

data class TmdbSeasonInfo(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("season_number") val seasonNumber: Int? = null,
    @param:JsonProperty("episode_count") val episodeCount: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null
)

data class TmdbEpisodeDetails(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("air_date") val airDate: String? = null,
    @param:JsonProperty("episode_number") val episodeNumber: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("season_number") val seasonNumber: Int? = null,
    @param:JsonProperty("still_path") val stillPath: String? = null,
    @param:JsonProperty("vote_average") val rating: Double? = null,
    @param:JsonProperty("runtime") val runtime: Int? = null
)

data class TmdbSeasonDetails(
    @param:JsonProperty("id") val id: Int? = null,
    @param:JsonProperty("season_number") val seasonNumber: Int? = null,
    @param:JsonProperty("name") val name: String? = null,
    @param:JsonProperty("overview") val overview: String? = null,
    @param:JsonProperty("poster_path") val posterPath: String? = null,
    @param:JsonProperty("episodes") val episodes: List<TmdbEpisodeDetails>? = null
)

// Combined response for TV show with all seasons
data class TmdbTvShowWithSeasons(
    val tvDetails: TmdbTvDetails?,
    val seasonDetails: Map<Int, TmdbSeasonDetails?>
)

object FMFTPTmdbHelper {
    private const val TMDB_API = "https://api.themoviedb.org/3"
    private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p"
    private const val TAG = "FMFTPTmdbHelper"
    private var lastApiCallTime = 0L
    private const val API_CALL_DELAY = 250L // 250ms delay between API calls

    // TMDB image sizes
    object ImageSizes {
        const val POSTER_SMALL = "w185"
        const val POSTER_MEDIUM = "w342"
        const val POSTER_LARGE = "w500"
        const val BACKDROP_SMALL = "w300"
        const val BACKDROP_MEDIUM = "w780"
        const val BACKDROP_LARGE = "w1280"
        const val STILL_SMALL = "w185"
        const val STILL_MEDIUM = "w300"
        const val STILL_LARGE = "w500"
    }

    private fun getApiKey(): String {
        return try {
            FMFTPSettingsManager.getApiKey() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error getting API key: ${e.message}")
            ""
        }
    }

    fun isEnabled(): Boolean {
        return FMFTPSettingsManager.isTmdbEnabled() && getApiKey().isNotEmpty()
    }

    // Helper function to get image URL
    fun getImageUrl(path: String?, size: String = ImageSizes.POSTER_MEDIUM): String? {
        if (path.isNullOrEmpty()) return null
        return "$TMDB_IMAGE_BASE_URL/$size$path"
    }

    fun getPosterUrl(path: String?): String? = getImageUrl(path, ImageSizes.POSTER_MEDIUM)
    fun getBackdropUrl(path: String?): String? = getImageUrl(path, ImageSizes.BACKDROP_MEDIUM)
    fun getStillUrl(path: String?): String? = getImageUrl(path, ImageSizes.STILL_MEDIUM)

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

    /**
     * Get movie details from TMDB using TMDB ID
     */
    suspend fun getMovieDetails(tmdbId: Int): TmdbMovieDetails? {
        if (!isEnabled()) {
            Log.d(TAG, "TMDB integration is disabled")
            return null
        }

        val apiKey = getApiKey()
        val url = "$TMDB_API/movie/$tmdbId?api_key=$apiKey"
        
        return try {
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting movie details: ${e.message}")
            null
        }
    }

    /**
     * Get TV show details from TMDB using TMDB ID
     */
    suspend fun getTvDetails(tmdbId: Int): TmdbTvDetails? {
        if (!isEnabled()) {
            Log.d(TAG, "TMDB integration is disabled")
            return null
        }

        val apiKey = getApiKey()
        val url = "$TMDB_API/tv/$tmdbId?api_key=$apiKey"
        
        return try {
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting TV details: ${e.message}")
            null
        }
    }

    /**
     * Get season details from TMDB
     */
    suspend fun getSeasonDetails(tmdbId: Int, seasonNumber: Int): TmdbSeasonDetails? {
        if (!isEnabled()) {
            Log.d(TAG, "TMDB integration is disabled")
            return null
        }

        val apiKey = getApiKey()
        val url = "$TMDB_API/tv/$tmdbId/season/$seasonNumber?api_key=$apiKey"
        
        return try {
            val response = makeApiCall(url) ?: return null
            parseJson(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting season details: ${e.message}")
            null
        }
    }

    /**
     * Get all required data for a TV show in minimal API calls
     * This fetches TV details and all unique season details
     */
    suspend fun getTvShowWithAllSeasons(
        tmdbId: Int,
        seasonNumbers: Set<Int>
    ): TmdbTvShowWithSeasons {
        if (!isEnabled()) {
            Log.d(TAG, "TMDB integration is disabled")
            return TmdbTvShowWithSeasons(null, emptyMap())
        }

        // First get TV details to understand the show
        val tvDetails = getTvDetails(tmdbId)
        
        if (tvDetails == null) {
            return TmdbTvShowWithSeasons(null, emptyMap())
        }

        // Fetch all unique seasons sequentially
        val seasonDetailsMap = mutableMapOf<Int, TmdbSeasonDetails?>()
        
        for (seasonNumber in seasonNumbers) {
            seasonDetailsMap[seasonNumber] = getSeasonDetails(tmdbId, seasonNumber)
        }

        return TmdbTvShowWithSeasons(tvDetails, seasonDetailsMap)
    }

    /**
     * Find episode details from pre-fetched season data
     */
    fun findEpisodeInSeasonData(
        seasonDetails: TmdbSeasonDetails?,
        episodeNumber: Int
    ): TmdbEpisodeDetails? {
        return seasonDetails?.episodes?.find { it.episodeNumber == episodeNumber }
    }

    private suspend fun delay(ms: Long) {
        Thread.sleep(ms)
    }
}
