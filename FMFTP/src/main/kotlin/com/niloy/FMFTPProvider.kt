package com.niloy

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder

class FMFTPProvider : MainAPI() {
    override var mainUrl = "https://fmftp.net"
    override var name = "FMFTP"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    companion object {
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    }

    // Data classes for API responses
    data class TrendingResponse(
        @param:JsonProperty("success") val success: Boolean = false,
        @param:JsonProperty("data") val data: List<TrendingItem> = listOf()
    )

    data class TrendingItem(
        @param:JsonProperty("content_type") val contentType: String = "",
        @param:JsonProperty("content_id") val contentId: Int = 0,
        @param:JsonProperty("content") val content: ContentInfo? = null
    )

    data class ContentInfo(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null
    )

    data class MoviesListResponse(
        @param:JsonProperty("total") val total: Int = 0,
        @param:JsonProperty("pages") val pages: Int = 0,
        @param:JsonProperty("current_page") val currentPage: Int = 1,
        @param:JsonProperty("limit") val limit: Int = 20,
        @param:JsonProperty("data") val data: List<MovieItem> = listOf()
    )

    data class MovieItem(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("genre") val genre: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("Library") val library: LibraryInfo? = null
    )

    data class TvShowsListResponse(
        @param:JsonProperty("total") val total: Int = 0,
        @param:JsonProperty("pages") val pages: Int = 0,
        @param:JsonProperty("current_page") val currentPage: Int = 1,
        @param:JsonProperty("limit") val limit: Int = 20,
        @param:JsonProperty("data") val data: List<TvShowItem> = listOf()
    )

    data class TvShowItem(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("tmdb_id") val tmdbId: String? = null,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("genre") val genre: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("Library") val library: LibraryInfo? = null
    )

    data class SearchItem(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("imdb_id") val imdbId: String? = null,
        @param:JsonProperty("tmdb_id") val tmdbId: String? = null,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("logo_path") val logoPath: String? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("genre") val genre: String? = null,
        @param:JsonProperty("casts") val casts: String? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("overview") val overview: String? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("Library") val library: LibraryInfo? = null
    )

    data class LibraryInfo(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("name") val name: String = "",
        @param:JsonProperty("type") val type: String = ""
    )

    data class MovieDetails(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("imdb_id") val imdbId: String? = null,
        @param:JsonProperty("tmdb_id") val tmdbId: String? = null,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("original_title") val originalTitle: String? = null,
        @param:JsonProperty("tagline") val tagline: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("release_date") val releaseDate: String? = null,
        @param:JsonProperty("logo_path") val logoPath: String? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("trailers") val trailers: String? = null,
        @param:JsonProperty("genre") val genre: String? = null,
        @param:JsonProperty("casts") val casts: String? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("overview") val overview: String? = null,
        @param:JsonProperty("Library") val library: LibraryInfo? = null
    )

    data class TvShowDetails(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("tmdb_id") val tmdbId: String? = null,
        @param:JsonProperty("title") val title: String = "",
        @param:JsonProperty("original_title") val originalTitle: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("release_date") val releaseDate: String? = null,
        @param:JsonProperty("logo_path") val logoPath: String? = null,
        @param:JsonProperty("poster_path") val posterPath: String? = null,
        @param:JsonProperty("backdrop_path") val backdropPath: String? = null,
        @param:JsonProperty("trailer") val trailer: String? = null,
        @param:JsonProperty("genre") val genre: String? = null,
        @param:JsonProperty("casts") val casts: String? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("overview") val overview: String? = null,
        @param:JsonProperty("episodes") val episodes: List<EpisodeInfo>? = null,
        @param:JsonProperty("Library") val libraryInfo: LibraryInfo? = null,
        @param:JsonProperty("library") val libraryId: Int? = null
    )

    data class EpisodeInfo(
        @param:JsonProperty("id") val id: Int = 0,
        @param:JsonProperty("tmdb_id") val tmdbId: String? = null,
        @param:JsonProperty("show_id") val showId: Int = 0,
        @param:JsonProperty("name") val name: String = "",
        @param:JsonProperty("season_number") val seasonNumber: Int = 1,
        @param:JsonProperty("episode_number") val episodeNumber: Int = 1,
        @param:JsonProperty("release_date") val releaseDate: String? = null,
        @param:JsonProperty("still_path") val stillPath: String? = null,
        @param:JsonProperty("online_rating") val onlineRating: Double? = null,
        @param:JsonProperty("overview") val overview: String? = null
    )

    private fun getImageUrl(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return if (path.startsWith("http")) path else "$TMDB_IMAGE_BASE$path"
    }

    override val mainPage = mainPageOf(
        "$mainUrl/api/view-tracking/trending?period=week&content_type=MOVIE&limit=20&offset=0" to "Trending Movies",
        "$mainUrl/api/movies?limit=20&fields=id,title,genre,year,views,download,online_rating,release_date,poster_path,backdrop_path&library=1&page=1&sort=release_date" to "Bollywood Movies",
        "$mainUrl/api/movies?limit=20&fields=id,title,genre,year,views,download,online_rating,release_date,poster_path,backdrop_path&library=2&page=1&sort=release_date" to "Hollywood Movies",
        "$mainUrl/api/tv-shows?limit=20&library=9&page=1&sort=release_date" to "English TV Shows",
        "$mainUrl/api/tv-shows?limit=20&library=10&page=1&sort=release_date" to "Hindi TV Shows",
        "$mainUrl/api/tv-shows?limit=20&library=11&page=1&sort=release_date" to "Korean TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data
        
        // Determine the type of API based on URL
        val items = when {
            url.contains("view-tracking/trending") -> {
                // Handle trending API
                val offset = (page - 1) * 20
                val fullUrl = url.replace("offset=0", "offset=$offset")
                val response = app.get(fullUrl).parsedSafe<TrendingResponse>()
                
                response?.data?.mapNotNull { item ->
                    val content = item.content ?: return@mapNotNull null
                    val isMovie = item.contentType == "MOVIE"
                    
                    if (isMovie) {
                        newMovieSearchResponse(
                            name = content.title,
                            url = "$mainUrl/api/movies/${content.id}",
                            type = TvType.Movie
                        ) {
                            this.posterUrl = getImageUrl(content.posterPath)
                            this.year = content.year
                        }
                    } else {
                        newTvSeriesSearchResponse(
                            name = content.title,
                            url = "$mainUrl/api/tv-shows/${content.id}?fields=episodes",
                            type = TvType.TvSeries
                        ) {
                            this.posterUrl = getImageUrl(content.posterPath)
                            this.year = content.year
                        }
                    }
                } ?: listOf()
            }
            url.contains("/api/movies") -> {
                // Handle movies API
                val fullUrl = url.replace("page=1", "page=$page")
                val response = app.get(fullUrl).parsedSafe<MoviesListResponse>()
                
                response?.data?.map { movie ->
                    newMovieSearchResponse(
                        name = movie.title,
                        url = "$mainUrl/api/movies/${movie.id}",
                        type = TvType.Movie
                    ) {
                        this.posterUrl = getImageUrl(movie.posterPath)
                        this.year = movie.year
                    }
                } ?: listOf()
            }
            url.contains("/api/tv-shows") -> {
                // Handle TV shows API
                val fullUrl = url.replace("page=1", "page=$page")
                val response = app.get(fullUrl).parsedSafe<TvShowsListResponse>()
                
                response?.data?.map { show ->
                    newTvSeriesSearchResponse(
                        name = show.title,
                        url = "$mainUrl/api/tv-shows/${show.id}?fields=episodes",
                        type = TvType.TvSeries
                    ) {
                        this.posterUrl = getImageUrl(show.posterPath)
                        this.year = show.year
                    }
                } ?: listOf()
            }
            else -> listOf()
        }

        return newHomePageResponse(
            list = HomePageList(request.name, items),
            hasNext = items.size >= 20
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        
        try {
            val encodedQuery = query.replace(" ", "+")
            val response = app.get("$mainUrl/api/search?search=$encodedQuery").text
            val searchResults = com.lagradost.cloudstream3.utils.AppUtils.parseJson<List<SearchResult>>(response)
            
            results.addAll(searchResults.map { item ->
                val type = if (item.type == "movie" || item.Library?.type == "MOVIE") TvType.Movie else TvType.TvSeries
                val posterUrl = item.poster_path?.let { "$TMDB_IMAGE_BASE$it" }
                
                if (type == TvType.Movie) {
                    newMovieSearchResponse(
                        item.title,
                        "$mainUrl/api/movies/${item.id}",
                        type
                    ) {
                        this.posterUrl = posterUrl
                        this.year = item.year
                    }
                } else {
                    newTvSeriesSearchResponse(
                        item.title,
                        "$mainUrl/api/tv-shows/${item.id}?fields=episodes",
                        type
                    ) {
                        this.posterUrl = posterUrl
                        this.year = item.year
                    }
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
    
    // Data class for search with snake_case field names to match JSON directly
    data class SearchResult(
        val id: Int,
        val title: String,
        val poster_path: String?,
        val backdrop_path: String?,
        val year: Int?,
        val online_rating: Double?,
        val overview: String?,
        val genre: String?,
        val casts: String?,
        val type: String?,
        val Library: LibraryInfo?
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        return if (url.contains("/movies/")) {
            loadMovie(url)
        } else {
            loadTvShow(url)
        }
    }

    private suspend fun loadMovie(url: String): LoadResponse {
        val response = app.get(url).parsedSafe<MovieDetails>()
            ?: throw ErrorLoadingException("Could not load movie details")

        val actors = response.casts?.split(",")?.map {
            ActorData(Actor(it.trim()))
        }

        // Try to get TMDB data if enabled and tmdb_id is available
        val tmdbData = response.tmdbId?.toIntOrNull()?.let { tmdbId ->
            if (FMFTPTmdbHelper.isEnabled()) {
                FMFTPTmdbHelper.getMovieDetails(tmdbId)
            } else null
        }

        // Use TMDB data if available, otherwise fall back to FMFTP data
        val finalPoster = tmdbData?.posterPath?.let { FMFTPTmdbHelper.getPosterUrl(it) } 
            ?: getImageUrl(response.posterPath)
        val finalBackdrop = tmdbData?.backdropPath?.let { FMFTPTmdbHelper.getBackdropUrl(it) } 
            ?: getImageUrl(response.backdropPath)
        val finalPlot = tmdbData?.overview?.takeIf { it.isNotEmpty() } ?: response.overview
        val finalRating = tmdbData?.rating?.times(1000)?.toInt() 
            ?: response.onlineRating?.times(1000)?.toInt()
        val finalTags = tmdbData?.genres?.mapNotNull { it.name } 
            ?: response.genre?.split(",")?.map { it.trim() }

        return newMovieLoadResponse(
            name = response.title,
            url = url,
            type = TvType.Movie,
            dataUrl = "$mainUrl/api/stream/video/stream?type=movies&id=${response.id}"
        ) {
            this.posterUrl = finalPoster
            this.backgroundPosterUrl = finalBackdrop
            this.year = response.year
            this.plot = finalPlot
            this.tags = finalTags
            @Suppress("DEPRECATION")
            this.rating = finalRating
            this.actors = actors
            tmdbData?.runtime?.let { this.duration = it }
        }
    }

    private suspend fun loadTvShow(url: String): LoadResponse {
        val responseText = app.get(url).text
        val response = com.lagradost.cloudstream3.utils.AppUtils.parseJson<TvShowDetailsRaw>(responseText)

        val actors = response.casts?.split(",")?.map {
            ActorData(Actor(it.trim()))
        }

        // Get unique season numbers from episodes
        val seasonNumbers = (response.episodes ?: emptyList())
            .map { it.season_number }
            .toSet()

        // Try to get TMDB data if enabled and tmdb_id is available
        val tmdbId = response.tmdb_id?.toIntOrNull()
        val tmdbData = if (tmdbId != null && FMFTPTmdbHelper.isEnabled()) {
            FMFTPTmdbHelper.getTvShowWithAllSeasons(tmdbId, seasonNumbers)
        } else null

        // Sort episodes by season number first, then by episode number
        val episodes = (response.episodes ?: emptyList())
            .sortedWith(compareBy({ it.season_number }, { it.episode_number }))
            .map { ep ->
                // Try to find TMDB episode data
                val tmdbEpisode = tmdbData?.seasonDetails?.get(ep.season_number)?.let { seasonData ->
                    FMFTPTmdbHelper.findEpisodeInSeasonData(seasonData, ep.episode_number)
                }

                // Use TMDB data if available, otherwise fall back to FMFTP data
                val episodePoster = tmdbEpisode?.stillPath?.let { FMFTPTmdbHelper.getStillUrl(it) }
                    ?: getImageUrl(ep.still_path)
                val episodeRating = tmdbEpisode?.rating?.times(10)?.toInt()
                    ?: ep.online_rating?.times(10)?.toInt()
                val episodeDescription = tmdbEpisode?.overview?.takeIf { it.isNotEmpty() }
                    ?: ep.overview
                val episodeName = tmdbEpisode?.name?.takeIf { it.isNotEmpty() }
                    ?: ep.name
                    ?: "Episode ${ep.episode_number}"

                newEpisode("$mainUrl/api/stream/video/stream?type=tv_shows&id=${ep.id}") {
                    this.name = episodeName
                    this.season = ep.season_number
                    this.episode = ep.episode_number
                    this.posterUrl = episodePoster
                    @Suppress("DEPRECATION")
                    this.rating = episodeRating
                    this.description = episodeDescription
                }
            }

        // Use TMDB show data if available
        val tvDetails = tmdbData?.tvDetails
        val finalPoster = tvDetails?.posterPath?.let { FMFTPTmdbHelper.getPosterUrl(it) }
            ?: getImageUrl(response.poster_path)
        val finalBackdrop = tvDetails?.backdropPath?.let { FMFTPTmdbHelper.getBackdropUrl(it) }
            ?: getImageUrl(response.backdrop_path)
        val finalPlot = tvDetails?.overview?.takeIf { it.isNotEmpty() } ?: response.overview
        val finalRating = tvDetails?.rating?.times(1000)?.toInt()
            ?: response.online_rating?.times(1000)?.toInt()
        val finalTags = tvDetails?.genres?.mapNotNull { it.name }
            ?: response.genre?.split(",")?.map { it.trim() }

        return newTvSeriesLoadResponse(
            name = response.title,
            url = url,
            type = TvType.TvSeries,
            episodes = episodes
        ) {
            this.posterUrl = finalPoster
            this.backgroundPosterUrl = finalBackdrop
            this.year = response.year
            this.plot = finalPlot
            this.tags = finalTags
            @Suppress("DEPRECATION")
            this.rating = finalRating
            this.actors = actors
        }
    }

    // Raw data classes with snake_case to match JSON directly (no Jackson needed)
    data class TvShowDetailsRaw(
        val id: Int = 0,
        val tmdb_id: String? = null,
        val title: String = "",
        val original_title: String? = null,
        val year: Int? = null,
        val release_date: String? = null,
        val logo_path: String? = null,
        val poster_path: String? = null,
        val backdrop_path: String? = null,
        val trailer: String? = null,
        val genre: String? = null,
        val casts: String? = null,
        val online_rating: Double? = null,
        val overview: String? = null,
        val episodes: List<EpisodeInfoRaw>? = null
    )

    data class EpisodeInfoRaw(
        val id: Int = 0,
        val tmdb_id: String? = null,
        val show_id: Int = 0,
        val name: String? = null,
        val season_number: Int = 1,
        val episode_number: Int = 1,
        val release_date: String? = null,
        val still_path: String? = null,
        val online_rating: Double? = null,
        val overview: String? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            // The API returns a 302 redirect to the actual video URL
            val response = app.get(
                data,
                allowRedirects = false
            )
            
            val location = response.headers["Location"] ?: response.headers["location"]
            
            if (location != null) {
                // Clean up the URL (it may start with //)
                val videoUrl = if (location.startsWith("//")) {
                    "https:$location"
                } else if (location.startsWith("/")) {
                    "$mainUrl$location"
                } else {
                    location
                }
                
                // Determine quality from URL
                val quality = when {
                    videoUrl.contains("2160p", ignoreCase = true) -> Qualities.P2160.value
                    videoUrl.contains("1080p", ignoreCase = true) -> Qualities.P1080.value
                    videoUrl.contains("720p", ignoreCase = true) -> Qualities.P720.value
                    videoUrl.contains("480p", ignoreCase = true) -> Qualities.P480.value
                    videoUrl.contains("360p", ignoreCase = true) -> Qualities.P360.value
                    else -> Qualities.P1080.value // Default to 1080p
                }
                
                val linkType = if (videoUrl.contains(".m3u8")) {
                    ExtractorLinkType.M3U8
                } else {
                    ExtractorLinkType.VIDEO
                }
                
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = videoUrl,
                        type = linkType
                    ) {
                        this.referer = mainUrl
                        this.quality = quality
                    }
                )
                
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}
