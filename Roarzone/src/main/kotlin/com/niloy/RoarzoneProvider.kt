package com.niloy

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder
import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.AcraApplication.Companion.context

class RoarzoneProvider : MainAPI() {
    override var mainUrl = "https://play.roarzone.info"
    override var name = "RoarZone"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Cartoon,
        TvType.AsianDrama,
        TvType.Others,
        TvType.Documentary
    )

    companion object {
        // Static reference to clear auth cache
        private var accessToken: String? = null
        private var userId: String? = null
        
        // Default credentials
        const val DEFAULT_USERNAME = "RoarZone_Guest"
        const val DEFAULT_PASSWORD = ""
        
        fun clearAuthCache() {
            accessToken = null
            userId = null
        }
    }

    // Emby API Data Classes
    data class EmbySearchResult(
        @param:JsonProperty("Items") val items: List<EmbyItem> = listOf(),
        @param:JsonProperty("TotalRecordCount") val totalRecordCount: Int = 0
    )

    data class EmbyItem(
        @param:JsonProperty("Id") val id: String = "",
        @param:JsonProperty("Name") val name: String = "",
        @param:JsonProperty("Type") val type: String = "",
        @param:JsonProperty("ProductionYear") val productionYear: Int? = null,
        @param:JsonProperty("RunTimeTicks") val runTimeTicks: Long? = null,
        @param:JsonProperty("SeriesName") val seriesName: String? = null,
        @param:JsonProperty("SeasonName") val seasonName: String? = null,
        @param:JsonProperty("IndexNumber") val indexNumber: Int? = null,
        @param:JsonProperty("ParentIndexNumber") val parentIndexNumber: Int? = null,
        @param:JsonProperty("Overview") val overview: String? = null,
        @param:JsonProperty("CommunityRating") val communityRating: Float? = null,
        @param:JsonProperty("MediaStreams") val mediaStreams: List<MediaStream>? = null,
        @param:JsonProperty("Path") val path: String? = null,
        @param:JsonProperty("Studios") val studios: List<Studio>? = null,
        @param:JsonProperty("Genres") val genres: List<String>? = null,
        @param:JsonProperty("People") val people: List<Person>? = null,
        @param:JsonProperty("BackdropImageTags") val backdropImageTags: List<String>? = null,
        @param:JsonProperty("ImageTags") val imageTags: ImageTags? = null,
        @param:JsonProperty("SeasonId") val seasonId: String? = null,
        @param:JsonProperty("SeriesId") val seriesId: String? = null
    )

    data class MediaStream(
        @param:JsonProperty("Type") val type: String = "",
        @param:JsonProperty("Codec") val codec: String? = null,
        @param:JsonProperty("Language") val language: String? = null,
        @param:JsonProperty("DisplayTitle") val displayTitle: String? = null,
        @param:JsonProperty("Width") val width: Int? = null,
        @param:JsonProperty("Height") val height: Int? = null,
        @param:JsonProperty("Index") val index: Int? = null
    )

    data class Studio(
        @param:JsonProperty("Name") val name: String = ""
    )

    data class Person(
        @param:JsonProperty("Name") val name: String = "",
        @param:JsonProperty("Role") val role: String? = null,
        @param:JsonProperty("Type") val type: String = ""
    )

    data class ImageTags(
        @param:JsonProperty("Primary") val primary: String? = null,
        @param:JsonProperty("Backdrop") val backdrop: String? = null
    )

    data class EmbySeasonResult(
        @param:JsonProperty("Items") val items: List<EmbyItem> = listOf()
    )

    data class EmbyEpisodeResult(
        @param:JsonProperty("Items") val items: List<EmbyItem> = listOf()
    )

    data class EmbyAuthResult(
        @param:JsonProperty("AccessToken") val accessToken: String = "",
        @param:JsonProperty("User") val user: EmbyUser? = null
    )

    data class EmbyUser(
        @param:JsonProperty("Id") val id: String = "",
        @param:JsonProperty("Name") val name: String = ""
    )

    private fun getStoredCredentials(): Pair<String, String> {
        return try {
            val appContext = context
            if (appContext == null) {
                println("Context is null, using default credentials")
                return Pair(RoarzoneSettingsDialog.DEFAULT_USERNAME, RoarzoneSettingsDialog.DEFAULT_PASSWORD)
            }
            
            val sharedPreferences = appContext.getSharedPreferences(
                RoarzoneSettingsDialog.PREF_NAME, 
                Context.MODE_PRIVATE
            )
            
            val isLoggedIn = sharedPreferences.getBoolean(RoarzoneSettingsDialog.KEY_IS_LOGGED_IN, false)
            
            if (isLoggedIn) {
                val username = sharedPreferences.getString(
                    RoarzoneSettingsDialog.KEY_USERNAME, 
                    RoarzoneSettingsDialog.DEFAULT_USERNAME
                ) ?: RoarzoneSettingsDialog.DEFAULT_USERNAME
                
                val password = sharedPreferences.getString(
                    RoarzoneSettingsDialog.KEY_PASSWORD, 
                    RoarzoneSettingsDialog.DEFAULT_PASSWORD
                ) ?: RoarzoneSettingsDialog.DEFAULT_PASSWORD
                
                Pair(username, password)
            } else {
                // Use default credentials if not logged in
                Pair(RoarzoneSettingsDialog.DEFAULT_USERNAME, RoarzoneSettingsDialog.DEFAULT_PASSWORD)
            }
        } catch (e: Exception) {
            println("Error getting stored credentials: ${e.message}")
            // Fallback to default credentials
            Pair(RoarzoneSettingsDialog.DEFAULT_USERNAME, RoarzoneSettingsDialog.DEFAULT_PASSWORD)
        }
    }

    private suspend fun authenticate() {
        if (accessToken == null) {
            try {
                println("Attempting to authenticate with RoarZone...")
                val (username, password) = getStoredCredentials()
                println("Using credentials - Username: $username")
                
                val authBody = mapOf(
                    "Username" to username,
                    "Password" to password,
                    "Pw" to password
                )
                
                val authResponse = app.post(
                    "$mainUrl/emby/Users/AuthenticateByName",
                    json = authBody,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Emby-Authorization" to "MediaBrowser Client=\"CloudStream\", Device=\"Android\", DeviceId=\"cloudstream-android\", Version=\"1.0\""
                    )
                ).parsedSafe<EmbyAuthResult>()

                accessToken = authResponse?.accessToken
                userId = authResponse?.user?.id
                
                if (accessToken != null && userId != null) {
                    println("Authentication successful. User ID: $userId, Token: ${accessToken?.take(10)}...")
                } else {
                    println("Authentication failed - no access token or user ID received")
                    println("Response: $authResponse")
                }
            } catch (e: Exception) {
                println("Authentication failed with exception: ${e.message}")
                e.printStackTrace()
                println("Continuing without authentication...")
            }
        }
    }

    private fun getAuthHeaders(): Map<String, String> {
        return if (accessToken != null) {
            mapOf(
                "X-Emby-Authorization" to "MediaBrowser Client=\"CloudStream\", Device=\"Android\", DeviceId=\"cloudstream-android\", Version=\"1.0\", Token=\"$accessToken\""
            )
        } else {
            mapOf(
                "X-Emby-Authorization" to "MediaBrowser Client=\"CloudStream\", Device=\"Android\", DeviceId=\"cloudstream-android\", Version=\"1.0\""
            )
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        authenticate()
        
        val homePageLists = mutableListOf<HomePageList>()
        
        // Get latest movies
        try {
            val latestMovies = app.get(
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Movie&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending&Recursive=true",
                headers = getAuthHeaders()
            ).parsedSafe<EmbySearchResult>()

            val moviesList = latestMovies?.items?.mapNotNull { item ->
                item.toSearchResponse()
            } ?: listOf()

            if (moviesList.isNotEmpty()) {
                homePageLists.add(HomePageList("Latest Movies", moviesList))
            }
        } catch (e: Exception) {
            println("Error loading latest movies: ${e.message}")
        }

        // Get latest TV shows
        try {
            val latestTvShows = app.get(
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Series&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending&Recursive=true",
                headers = getAuthHeaders()
            ).parsedSafe<EmbySearchResult>()

            val tvShowsList = latestTvShows?.items?.mapNotNull { item ->
                item.toSearchResponse()
            } ?: listOf()

            if (tvShowsList.isNotEmpty()) {
                homePageLists.add(HomePageList("Latest TV Shows", tvShowsList))
            }
        } catch (e: Exception) {
            println("Error loading latest TV shows: ${e.message}")
        }

        // Get recent episodes
        try {
            val recentEpisodes = app.get(
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Episode&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending&Recursive=true",
                headers = getAuthHeaders()
            ).parsedSafe<EmbySearchResult>()

            val episodesList = recentEpisodes?.items?.mapNotNull { item ->
                item.toSearchResponse()
            } ?: listOf()

            if (episodesList.isNotEmpty()) {
                homePageLists.add(HomePageList("Recent Episodes", episodesList))
            }
        } catch (e: Exception) {
            println("Error loading recent episodes: ${e.message}")
        }

        return newHomePageResponse(homePageLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        authenticate()
        
        val searchResults = mutableListOf<SearchResponse>()
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            // Try multiple search strategies - only include Movies and Series, not Episodes
            val searchStrategies = listOf(
                // Strategy 1: Use searchTerm parameter (lowercase)
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?searchTerm=$encodedQuery&IncludeItemTypes=Movie,Series&Limit=50&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&Recursive=true",
                
                // Strategy 2: Use NameStartsWith parameter
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?NameStartsWith=$encodedQuery&IncludeItemTypes=Movie,Series&Limit=50&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&Recursive=true",
                
                // Strategy 3: Use the Items endpoint without user-specific path
                "$mainUrl/emby/Items?searchTerm=$encodedQuery&IncludeItemTypes=Movie,Series&Limit=50&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&Recursive=true"
            )
            
            for ((index, searchUrl) in searchStrategies.withIndex()) {
                try {
                    println("Trying search strategy ${index + 1}: $searchUrl")
                    val response = app.get(
                        searchUrl,
                        headers = getAuthHeaders()
                    )
                    
                    println("Response status: ${response.code}")
                    println("Response body preview: ${response.text.take(200)}")
                    
                    val searchResponse = response.parsedSafe<EmbySearchResult>()
                    println("Parsed response: ${searchResponse?.items?.size ?: 0} items")

                    val results = searchResponse?.items?.mapNotNull { item ->
                        item.toSearchResponse()
                    } ?: listOf()
                    
                    if (results.isNotEmpty()) {
                        searchResults.addAll(results)
                        println("Found ${results.size} results using strategy ${index + 1}")
                        break // Stop trying other strategies if we found results
                    }
                } catch (e: Exception) {
                    println("Search strategy ${index + 1} failed: ${e.message}")
                    continue
                }
            }
            
            // If no results found, try a broader search without user ID
            if (searchResults.isEmpty()) {
                try {
                    println("Trying fallback search without authentication")
                    val fallbackResponse = app.get(
                        "$mainUrl/emby/Items?searchTerm=$encodedQuery&IncludeItemTypes=Movie,Series&Limit=50&Fields=BasicSyncInfo,Path,Overview&Recursive=true"
                    )
                    
                    println("Fallback response status: ${fallbackResponse.code}")
                    val parsedFallback = fallbackResponse.parsedSafe<EmbySearchResult>()

                    searchResults.addAll(
                        parsedFallback?.items?.mapNotNull { item ->
                            item.toSearchResponse()
                        } ?: listOf()
                    )
                    
                    if (searchResults.isNotEmpty()) {
                        println("Found ${searchResults.size} results using fallback search")
                    }
                } catch (e: Exception) {
                    println("Fallback search failed: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            println("Search error: ${e.message}")
            e.printStackTrace()
        }

        println("Total search results: ${searchResults.size}")
        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        authenticate()
        
        val itemId = url.substringAfterLast("/")
        
        try {
            val item = app.get(
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items/$itemId?Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating,SeasonUserData,MediaStreams",
                headers = getAuthHeaders()
            ).parsedSafe<EmbyItem>()

            return when (item?.type) {
                "Movie" -> {
                    val actors = item.people?.filter { it.type == "Actor" }?.map { person ->
                        ActorData(
                            Actor(person.name, getPersonImageUrl(itemId, person.name)),
                            roleString = person.role
                        )
                    }

                    newMovieLoadResponse(
                        item.name,
                        url,
                        TvType.Movie,
                        "$mainUrl/play/$itemId"
                    ) {
                        this.year = item.productionYear
                        this.plot = item.overview
                        this.tags = item.genres
                        this.duration = item.runTimeTicks?.let { (it / 10000000 / 60).toInt() } // Convert to minutes
                        this.actors = actors
                        this.posterUrl = getImageUrl(itemId, "Primary")
                        this.backgroundPosterUrl = getImageUrl(itemId, "Backdrop")
                    }
                }
                "Series" -> {
                    // Check if the series has anime/animation genres
                    val isAnime = item.genres?.any { genre ->
                        genre.lowercase().contains("anime") || genre.lowercase().contains("animation")
                    } ?: false
                    
                    val tvType = if (isAnime) TvType.Anime else TvType.TvSeries
                    val episodes = getSeriesEpisodes(itemId)
                    
                    newTvSeriesLoadResponse(
                        item.name,
                        url,
                        tvType,
                        episodes
                    ) {
                        this.year = item.productionYear
                        this.plot = item.overview
                        this.tags = item.genres
                        this.posterUrl = getImageUrl(itemId, "Primary")
                        this.backgroundPosterUrl = getImageUrl(itemId, "Backdrop")
                    }
                }
                "Episode" -> {
                    newMovieLoadResponse(
                        item.name,
                        url,
                        TvType.TvSeries,
                        "$mainUrl/play/$itemId"
                    ) {
                        this.year = item.productionYear
                        this.plot = item.overview
                        this.posterUrl = getImageUrl(itemId, "Primary")
                        this.backgroundPosterUrl = getImageUrl(itemId, "Backdrop")
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            println("Load error: ${e.message}")
            return null
        }
    }

    private suspend fun getSeriesEpisodes(seriesId: String): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        try {
            // Get seasons
            val seasonsResponse = app.get(
                "$mainUrl/emby/Shows/$seriesId/Seasons?userId=${userId ?: "anonymous"}&Fields=BasicSyncInfo",
                headers = getAuthHeaders()
            ).parsedSafe<EmbySeasonResult>()

            seasonsResponse?.items?.forEach { season ->
                // Get episodes for each season
                val episodesResponse = app.get(
                    "$mainUrl/emby/Shows/$seriesId/Episodes?seasonId=${season.id}&userId=${userId ?: "anonymous"}&Fields=BasicSyncInfo,MediaSourceCount,Path,Overview",
                    headers = getAuthHeaders()
                ).parsedSafe<EmbyEpisodeResult>()

                episodesResponse?.items?.forEach { episode ->
                    episodes.add(
                        newEpisode("$mainUrl/play/${episode.id}") {
                            this.name = episode.name
                            this.season = episode.parentIndexNumber
                            this.episode = episode.indexNumber
                            this.description = episode.overview
                            this.posterUrl = getImageUrl(episode.id, "Primary")
                        }
                    )
                }
            }
        } catch (e: Exception) {
            println("Error loading episodes: ${e.message}")
        }

        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        authenticate()
        
        val itemId = data.substringAfterLast("/")
        
        try {
            // Get stream URL - try different stream endpoints
            val streamUrl = if (accessToken != null) {
                "$mainUrl/emby/Videos/$itemId/stream?api_key=$accessToken&Static=true"
            } else {
                "$mainUrl/emby/Videos/$itemId/stream?Static=true"
            }
            
            // Try to get media info for quality detection
            val mediaInfo = app.get(
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items/$itemId?Fields=MediaStreams",
                headers = getAuthHeaders()
            ).parsedSafe<EmbyItem>()

            val videoStream = mediaInfo?.mediaStreams?.find { it.type == "Video" }
            val quality = when {
                videoStream?.height != null && videoStream.height >= 1080 -> Qualities.P1080.value
                videoStream?.height != null && videoStream.height >= 720 -> Qualities.P720.value
                videoStream?.height != null && videoStream.height >= 480 -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                newExtractorLink(
                    this.name,
                    "${this.name} - ${videoStream?.displayTitle ?: "Direct"}",
                    streamUrl
                ) {
                    this.referer = mainUrl
                    this.quality = quality
                }
            )

            // Load subtitles
            mediaInfo?.mediaStreams?.filter { it.type == "Subtitle" }?.forEach { subtitle ->
                val subtitleIndex = subtitle.index ?: return@forEach
                val subtitleUrl = if (accessToken != null) {
                    "$mainUrl/emby/Videos/$itemId/Subtitles/$subtitleIndex/Stream.vtt?api_key=$accessToken"
                } else {
                    "$mainUrl/emby/Videos/$itemId/Subtitles/$subtitleIndex/Stream.vtt"
                }
                
                subtitleCallback.invoke(
                    SubtitleFile(
                        subtitle.language ?: subtitle.displayTitle ?: "Unknown",
                        subtitleUrl
                    )
                )
            }

            return true
        } catch (e: Exception) {
            println("Load links error: ${e.message}")
            return false
        }
    }

    private fun EmbyItem.toSearchResponse(): SearchResponse? {
        println("Converting item to search response: ${this.name} (${this.type})")
        return when (this.type) {
            "Movie" -> {
                newMovieSearchResponse(
                    this.name,
                    "$mainUrl/item/${this.id}",
                    TvType.Movie
                ) {
                    this.year = this@toSearchResponse.productionYear
                    this.posterUrl = getImageUrl(this@toSearchResponse.id, "Primary")
                    this.quality = getQualityFromPath(this@toSearchResponse.path)
                }
            }
            "Series" -> {
                // Check if the series has anime/animation genres
                val isAnime = this.genres?.any { genre ->
                    genre.lowercase().contains("anime") || genre.lowercase().contains("animation")
                } ?: false
                
                val tvType = if (isAnime) TvType.Anime else TvType.TvSeries
                
                newTvSeriesSearchResponse(
                    this.name,
                    "$mainUrl/item/${this.id}",
                    tvType
                ) {
                    this.year = this@toSearchResponse.productionYear
                    this.posterUrl = getImageUrl(this@toSearchResponse.id, "Primary")
                }
            }
            "Episode" -> {
                // Don't include episodes in search results
                null
            }
            else -> {
                println("Unknown item type: ${this.type}")
                null
            }
        }
    }

    private fun getImageUrl(itemId: String, imageType: String): String {
        return "$mainUrl/emby/Items/$itemId/Images/$imageType"
    }

    private fun getPersonImageUrl(itemId: String, personName: String): String {
        return "$mainUrl/emby/Items/$itemId/Images/Primary"
    }

    private fun getQualityFromPath(path: String?): SearchQuality? {
        if (path == null) return null
        
        return when {
            path.contains("1080p", true) || path.contains("1920x1080", true) -> SearchQuality.HD
            path.contains("720p", true) || path.contains("1280x720", true) -> SearchQuality.HD
            path.contains("480p", true) -> SearchQuality.SD
            else -> null
        }
    }
}
