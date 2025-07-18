package com.niloy

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FTPBDProvider : MainAPI() {
    override var mainUrl = "http://media.ftpbd.net:8096"
    override var name = "(BDIX) FTPBD Emby"
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

    // Emby API Data Classes
    data class EmbySearchResult(
        @JsonProperty("Items") val items: List<EmbyItem> = listOf(),
        @JsonProperty("TotalRecordCount") val totalRecordCount: Int = 0
    )

    data class EmbyItem(
        @JsonProperty("Id") val id: String = "",
        @JsonProperty("Name") val name: String = "",
        @JsonProperty("Type") val type: String = "",
        @JsonProperty("ProductionYear") val productionYear: Int? = null,
        @JsonProperty("RunTimeTicks") val runTimeTicks: Long? = null,
        @JsonProperty("SeriesName") val seriesName: String? = null,
        @JsonProperty("SeasonName") val seasonName: String? = null,
        @JsonProperty("IndexNumber") val indexNumber: Int? = null,
        @JsonProperty("ParentIndexNumber") val parentIndexNumber: Int? = null,
        @JsonProperty("Overview") val overview: String? = null,
        @JsonProperty("CommunityRating") val communityRating: Float? = null,
        @JsonProperty("MediaStreams") val mediaStreams: List<MediaStream>? = null,
        @JsonProperty("Path") val path: String? = null,
        @JsonProperty("Studios") val studios: List<Studio>? = null,
        @JsonProperty("Genres") val genres: List<String>? = null,
        @JsonProperty("People") val people: List<Person>? = null,
        @JsonProperty("BackdropImageTags") val backdropImageTags: List<String>? = null,
        @JsonProperty("ImageTags") val imageTags: ImageTags? = null,
        @JsonProperty("SeasonId") val seasonId: String? = null,
        @JsonProperty("SeriesId") val seriesId: String? = null
    )

    data class MediaStream(
        @JsonProperty("Type") val type: String = "",
        @JsonProperty("Codec") val codec: String? = null,
        @JsonProperty("Language") val language: String? = null,
        @JsonProperty("DisplayTitle") val displayTitle: String? = null,
        @JsonProperty("Width") val width: Int? = null,
        @JsonProperty("Height") val height: Int? = null
    )

    data class Studio(
        @JsonProperty("Name") val name: String = ""
    )

    data class Person(
        @JsonProperty("Name") val name: String = "",
        @JsonProperty("Role") val role: String? = null,
        @JsonProperty("Type") val type: String = ""
    )

    data class ImageTags(
        @JsonProperty("Primary") val primary: String? = null,
        @JsonProperty("Backdrop") val backdrop: String? = null
    )

    data class EmbySeasonResult(
        @JsonProperty("Items") val items: List<EmbyItem> = listOf()
    )

    data class EmbyEpisodeResult(
        @JsonProperty("Items") val items: List<EmbyItem> = listOf()
    )

    data class EmbyAuthResult(
        @JsonProperty("AccessToken") val accessToken: String = "",
        @JsonProperty("User") val user: EmbyUser? = null
    )

    data class EmbyUser(
        @JsonProperty("Id") val id: String = "",
        @JsonProperty("Name") val name: String = ""
    )

    // Authentication variables
    private var accessToken: String? = null
    private var userId: String? = null

    private suspend fun authenticate() {
        if (accessToken == null) {
            try {
                println("Attempting to authenticate...")
                val authBody = mapOf(
                    "Username" to "BNET--USER",
                    "Password" to "",
                    "Pw" to ""
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
                
                if (accessToken != null) {
                    println("Authentication successful. User ID: $userId")
                } else {
                    println("Authentication failed - no access token received")
                }
            } catch (e: Exception) {
                // If authentication fails, try without authentication
                println("Authentication failed: ${e.message}")
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
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Movie&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending",
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
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Series&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending",
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
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items?IncludeItemTypes=Episode&Limit=20&Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating&SortBy=DateCreated&SortOrder=Descending",
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
                "$mainUrl/emby/Users/${userId ?: "anonymous"}/Items/$itemId?Fields=BasicSyncInfo,MediaSourceCount,Path,Studios,Genres,People,Overview,CommunityRating,SeasonUserData",
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
                        this.rating = item.communityRating?.times(1000)?.toInt()
                        this.tags = item.genres
                        this.duration = item.runTimeTicks?.let { (it / 10000000 / 60).toInt() } // Convert to minutes
                        this.actors = actors
                        this.posterUrl = getImageUrl(itemId, "Primary")
                        this.backgroundPosterUrl = getImageUrl(itemId, "Backdrop")
                    }
                }
                "Series" -> {
                    val episodes = getSeriesEpisodes(itemId)
                    
                    newTvSeriesLoadResponse(
                        item.name,
                        url,
                        TvType.TvSeries,
                        episodes
                    ) {
                        this.year = item.productionYear
                        this.plot = item.overview
                        this.rating = item.communityRating?.times(1000)?.toInt()
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
                        this.rating = item.communityRating?.times(1000)?.toInt()
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
                ExtractorLink(
                    this.name,
                    "${this.name} - ${videoStream?.displayTitle ?: "Direct"}",
                    streamUrl,
                    referer = mainUrl,
                    quality = quality,
                    type = ExtractorLinkType.VIDEO
                )
            )

            // Load subtitles
            mediaInfo?.mediaStreams?.filter { it.type == "Subtitle" }?.forEachIndexed { index, subtitle ->
                val subtitleUrl = if (accessToken != null) {
                    "$mainUrl/emby/Videos/$itemId/Subtitles/$index/Stream.vtt?api_key=$accessToken"
                } else {
                    "$mainUrl/emby/Videos/$itemId/Subtitles/$index/Stream.vtt"
                }
                
                subtitleCallback.invoke(
                    SubtitleFile(
                        subtitle.language ?: "Unknown",
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
                newTvSeriesSearchResponse(
                    this.name,
                    "$mainUrl/item/${this.id}",
                    TvType.TvSeries
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
