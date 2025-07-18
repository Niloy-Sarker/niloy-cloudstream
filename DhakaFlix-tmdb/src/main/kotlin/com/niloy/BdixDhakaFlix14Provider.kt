package com.niloy

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.*
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections

open class BdixDhakaFlix14Provider : MainAPI() {
    override var mainUrl = "http://172.16.50.14"
    override var name = "(BDIX) DhakaFlix 14"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.AnimeMovie, TvType.TvSeries
    )

    init {
        // Clear provider cache on initialization
        getProviderCache(name).clearCache()
    }

    // Clear cache when provider is destroyed
    protected fun finalize() {
        getProviderCache(name).clearCache()
    }

    open val year = 2025
    open val tvSeriesKeyword: List<String>? = listOf("KOREAN%20TV%20%26%20WEB%20Series")
    open val serverName: String = "DHAKA-FLIX-14"    // Simple cache implementation with optimized settings
    companion object {
        private val caches = mutableMapOf<String, ProviderCache>()
        private const val POSTER_CACHE_DURATION = 1 * 60 * 60 * 1000L // 1 hour (reduced from 2)
        private const val SEARCH_CACHE_DURATION = 30 * 60 * 1000L // 30 minutes (reduced from 1 hour)  
        private const val TMDB_CACHE_DURATION = 15 * 60 * 1000L // 15 minutes (reduced from 30)
        private const val BATCH_SIZE = 6 // Increased for better performance
        
        @Synchronized
        private fun getProviderCache(providerName: String): ProviderCache {
            return caches.getOrPut(providerName) { ProviderCache() }
        }
    }

    private class ProviderCache {
        private val posterCache = Collections.synchronizedMap(mutableMapOf<String, Pair<String?, Long>>())
        private val searchCache = Collections.synchronizedMap(mutableMapOf<String, Pair<TmdbSearchResult?, Long>>())
        private val tmdbDetailsCache = Collections.synchronizedMap(mutableMapOf<String, Pair<TmdbDetails?, Long>>())
        private val seasonDetailsCache = Collections.synchronizedMap(mutableMapOf<String, Pair<TmdbSeasonDetails?, Long>>())

        fun getPosterCache(): MutableMap<String, Pair<String?, Long>> = posterCache
        fun getSearchCache(): MutableMap<String, Pair<TmdbSearchResult?, Long>> = searchCache
        fun getTmdbDetailsCache(): MutableMap<String, Pair<TmdbDetails?, Long>> = tmdbDetailsCache
        fun getSeasonDetailsCache(): MutableMap<String, Pair<TmdbSeasonDetails?, Long>> = seasonDetailsCache

        fun <T> getFromCache(
            cache: MutableMap<String, Pair<T?, Long>>,
            key: String,
            duration: Long
        ): T? {
            val (value, timestamp) = cache[key] ?: return null
            return if (System.currentTimeMillis() - timestamp < duration) value else null
        }        fun <T> addToCache(
            cache: MutableMap<String, Pair<T?, Long>>,
            key: String,
            value: T?
        ) {
            synchronized(cache) {
                // More aggressive cache cleanup for better performance
                if (cache.size > 50) { // Reduced from 100
                    val currentTime = System.currentTimeMillis()
                    // Remove expired entries
                    val iterator = cache.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if ((currentTime - entry.value.second) > TMDB_CACHE_DURATION) {
                            iterator.remove()
                        }
                    }
                    // If still too large, remove oldest entries
                    if (cache.size > 25) { // Reduced from 50
                        val sortedEntries = cache.entries.sortedBy { it.value.second }
                        sortedEntries.take(sortedEntries.size - 25).forEach { cache.remove(it.key) }
                    }
                }
                cache[key] = Pair(value, System.currentTimeMillis())
            }
        }

        fun clearCache() {
            synchronized(this) {
                getPosterCache().clear()
                getSearchCache().clear()
                getTmdbDetailsCache().clear()
                getSeasonDetailsCache().clear()
            }
        }
    }

    override val mainPage = mainPageOf(
        "Animation Movies (1080p)/" to "Animation Movies",
        "English Movies (1080p)/($year) 1080p/" to "English Movies",
        "Hindi Movies/($year)/" to "Hindi Movies",
        "SOUTH INDIAN MOVIES/Hindi Dubbed/($year)/" to "South Movies Hindi Dubbed",
        "/KOREAN TV %26 WEB Series/" to "Korean TV & WEB Series"
    )

    // Number of items to load per page
    private val itemsPerPage = 12

    // Implement lazy loading for TMDB data
    private suspend fun lazyLoadTmdbData(
        name: String,
        isMovie: Boolean,
        loadDetails: Boolean = false
    ): TmdbSearchResult? = coroutineScope {
        val cleanName = cleanNameForSearch(name)
        val cacheKey = "$cleanName:$isMovie"
        val providerCache = getProviderCache(this@BdixDhakaFlix14Provider.name)
        
        // First try to get from cache
        providerCache.getFromCache(providerCache.getSearchCache(), cacheKey, SEARCH_CACHE_DURATION)?.let { 
            return@coroutineScope it 
        }
        
        // If not in cache and we don't need details immediately, launch async
        if (!loadDetails) {
            launch {
                TmdbHelper.searchTmdb(cleanName, isMovie)?.let { result ->
                    providerCache.addToCache(providerCache.getSearchCache(), cacheKey, result)
                }
            }
            return@coroutineScope null
        }
        
        // If we need details now, do the search
        val result = TmdbHelper.searchTmdb(cleanName, isMovie)
        providerCache.addToCache(providerCache.getSearchCache(), cacheKey, result)
        return@coroutineScope result
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse = coroutineScope {
        val doc = app.get("$mainUrl/$serverName/${request.data}").document
        
        // Ensure page is at least 1
        val safePage = maxOf(1, page)
        val startIndex = ((safePage - 1) * itemsPerPage + 2).coerceAtLeast(2)
        val endIndex = (startIndex + itemsPerPage).coerceAtLeast(startIndex)
        
        val rows = doc.select("tbody > tr")
        val totalItems = rows.size
        
        // Ensure we don't exceed the available items
        val safeStartIndex = startIndex.coerceAtMost(totalItems)
        val safeEndIndex = endIndex.coerceAtMost(totalItems)
        
        val homeResponse = if (safeStartIndex < safeEndIndex) {
            rows.filterIndexed { index, _ -> index in safeStartIndex until safeEndIndex }
        } else {
            emptyList()
        }        // Process items in smaller batches - enable local poster loading for better UX
        val home = homeResponse.chunked(BATCH_SIZE).flatMap { chunk ->
            chunk.map { post ->
                async {
                    getPostResult(post, loadTmdbData = false, loadLocalPosters = true) // Enable local poster loading
                }
            }.awaitAll()
        }
        
        val hasNextPage = totalItems > safeEndIndex
        
        newHomePageResponse(request.name, home, hasNextPage)
    }

    private fun cleanNameForSearch(name: String): String {
        // Remove quality tags, file extensions, and brackets content
        return name.replace(Regex("\\d{3,4}p.*"), "") // Remove quality tags
            .replace(Regex("\\.(mkv|mp4|avi|mov)"), "") // Remove file extensions
            .replace(Regex("\\[.*?\\]"), "") // Remove square bracket content
            .replace(Regex("\\s*\\([^)]*TV Series[^)]*\\)"), "") // Remove TV Series with any text in parentheses
            .replace(Regex("\\s*\\([^)]*\\)"), "") // Remove any remaining parentheses content
            .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
            .trim()
    }    private suspend fun getPostResult(
        post: Element,
        loadTmdbData: Boolean = false,
        loadLocalPosters: Boolean = false
    ): SearchResponse {
        val folderHtml = post.select("td.fb-n > a")
        val rawName = folderHtml.text()
        val name = cleanNameForSearch(rawName)  // Use the same cleaning logic as search
        val url = mainUrl + folderHtml.attr("href")
        
        // Only load TMDB data if specifically requested
        val tmdbData = if (loadTmdbData) {
            lazyLoadTmdbData(name, isMovie = !containsAnyLoop(url, tvSeriesKeyword))
        } else null
        
        // Load local posters if requested (lightweight operation)
        val posterUrl = when {
            loadTmdbData -> findPosterUrl(url) // Full poster loading including TMDB fallback
            loadLocalPosters -> DhakaFlixUtils.findPosterLight(url, mainUrl) // Local posters only
            else -> null // No poster loading
        }
        
        return newAnimeSearchResponse(name, url, TvType.Movie) {
            addDubStatus(
                dubExist = hasMultiAudio(rawName),  // Check for multi audio indicators
                subExist = false
            )
            
            if (posterUrl?.isNotEmpty() == true) {
                this.posterUrl = posterUrl
            }
        }
    }    override suspend fun search(query: String): List<SearchResponse> {
        // Use fast search with lightweight local poster loading
        return DhakaFlixUtils.doSearch(
            query = query,
            mainUrl = mainUrl,
            serverName = serverName,
            api = this,
            findPosterFunc = { url -> DhakaFlixUtils.findPosterLight(url, mainUrl) } // Enable local poster loading
        )
    }

    // Use the common utility function for nameFromUrl
    protected fun nameFromUrl(href: String): String {
        return DhakaFlixUtils.nameFromUrl(href)
    }

    // Check if filename contains multi-audio indicators
    private fun hasMultiAudio(filename: String): Boolean {
        val multiAudioIndicators = listOf(
            "dual", "multi", "hindi", "english", "tamil", "telugu", "malayalam", 
            "kannada", "bengali", "urdu", "punjabi", "gujarati", "marathi",
            "audio", "dubbed", "dub", "lang", "language", "multilang"
        )
        
        val lowerFilename = filename.lowercase()
        return multiAudioIndicators.any { indicator ->
            lowerFilename.contains(indicator)
        }
    }      // Update findPosterUrl to prioritize local images over TMDB
    suspend fun findPosterUrl(contentUrl: String): String? {
        val providerCache = getProviderCache(name)
        
        // First check cache
        providerCache.getFromCache(providerCache.getPosterCache(), contentUrl, POSTER_CACHE_DURATION)?.let { 
            return it 
        }
        
        // Priority 1: Look for local images in content folder
        val localPoster = DhakaFlixUtils.findPoster(contentUrl, mainUrl)
        if (localPoster != null) {
            providerCache.addToCache(providerCache.getPosterCache(), contentUrl, localPoster)
            return localPoster
        }
        
        // Priority 2: Try to get from TMDB only if no local poster found
        // Extract name from URL for TMDB search
        val name = contentUrl.split("/").filter { it.isNotEmpty() }.lastOrNull()
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
            ?.let { cleanNameForSearch(it) }
        
        val result = if (name != null) {
            try {
                val tmdbData = lazyLoadTmdbData(name, !containsAnyLoop(contentUrl, tvSeriesKeyword))
                tmdbData?.posterPath?.let { TmdbHelper.getPosterUrl(it, isDetail = false) }
            } catch (e: Exception) {
                null
            }
        } else null
        
        providerCache.addToCache(providerCache.getPosterCache(), contentUrl, result)
        return result
    }

    /**
     * Helper method to create search responses for shared utility functions
     */
    open fun createSearchResponse(name: String, url: String, posterUrl: String?): SearchResponse {
        return newMovieSearchResponse(name, url, TvType.Movie) {
            if (posterUrl?.isNotEmpty() == true) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse = coroutineScope {
        val doc = app.get(url).document
        var name = ""
        var imageLink = ""
        var link = ""
        var plot: String? = null
        var rating: Int? = null
        var year: Int? = null
        var tmdbId: Int? = null
        
        // Get name from URL
        name = url.split("/").filter { it.isNotEmpty() }.last()
            .let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
          // Load TMDB data with details since this is a detail view
        val tmdbData = lazyLoadTmdbData(name, !containsAnyLoop(url, tvSeriesKeyword), loadDetails = true)
        
        // Priority 1: Try to get local poster from content folder first
        imageLink = findPosterUrl(url) ?: ""
        
        // Priority 2: If no local poster found, use TMDB poster
        if (imageLink.isEmpty()) {
            imageLink = tmdbData?.posterPath?.let { TmdbHelper.getPosterUrl(it, isDetail = true) } ?: ""
        }
        
        if (tmdbData != null) {
            tmdbId = tmdbData.id
            plot = tmdbData.overview
            rating = tmdbData.rating?.times(10)?.toInt()
            tmdbData.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()?.let {
                if (year == null) year = it
            }
        }

        // Extract year if present in the name
        if (year == null) {
            year = extractYear(name)
        }

        // Determine if this is a TV series or a movie based on URL
        val isTvSeries = containsAnyLoop(url, tvSeriesKeyword)
        
        if (isTvSeries) {
            val episodesData = mutableListOf<Episode>()
            
            // Process episodes using improved season logic
            doc.select("tbody > tr:gt(1)").forEach {
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    val folderName = it.select("td.fb-n > a").text()
                    val seasonInfo = parseSeasonInfo(folderName)
                    
                    val seasonNum: Int
                    val seasonName: String?
                    
                    if (seasonInfo.first != null) {
                        // Regular season with a number
                        seasonNum = seasonInfo.first!!
                        seasonName = null
                    } else {
                        // Special season (OVA, Specials, etc.) - use Season 0
                        seasonNum = 0
                        seasonName = seasonInfo.second
                    }
                    
                    link = mainUrl + it.select("td.fb-n > a").attr("href")
                    // Extract season data with TMDB ID for episode details
                    seasonExtractor(link, episodesData, seasonNum, tmdbId, seasonName)                } else if (imageLink.isEmpty()) {
                    val filename = it.select("td.fb-n > a").text().lowercase()
                    val href = it.select("td.fb-n > a").attr("href")
                    
                    // Enhanced local image detection with priority order
                    val posterPatterns = listOf(
                        "poster.jpg", "poster.jpeg", "poster.png",
                        "cover.jpg", "cover.jpeg", "cover.png", 
                        "a_AL_.jpg", "a_AL_.jpeg", "a_AL_.png",
                        "thumbnail.jpg", "thumbnail.jpeg", "thumbnail.png"
                    )
                    
                    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")
                    
                    // Check for exact poster filenames first
                    if (posterPatterns.contains(filename)) {
                        imageLink = mainUrl + href
                    }
                    // Then check for poster-like names
                    else if ((filename.contains("poster") || filename.contains("cover")) && 
                             imageExtensions.any { ext -> filename.endsWith(ext) }) {
                        imageLink = mainUrl + href
                    }
                    // Finally any image file as fallback
                    else if (imageExtensions.any { ext -> filename.endsWith(ext) }) {
                        imageLink = mainUrl + href
                    }
                } else {
                    val folderHtml = it.select("td.fb-n > a")
                    val title = folderHtml.text()
                    val link2 = mainUrl + folderHtml.attr("href")
                    if (!title.contains(Regex("\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE))) {
                        val episodeNum = episodesData.size + 1
                        // Try to get episode details from TMDB
                        val episodeDetails = if (tmdbId != null) {
                            TmdbHelper.getEpisodeDetails(tmdbId, 1, episodeNum)
                        } else null
                        
                        episodesData.add(
                            newEpisode(link2) {
                                this.name = episodeDetails?.name ?: title
                                this.season = 1
                                this.episode = episodeNum
                                this.description = episodeDetails?.overview
                                episodeDetails?.rating?.let { rating ->
                                    this.rating = (rating * 10).toInt()
                                }
                                episodeDetails?.stillPath?.let { still ->
                                    // Use small size for episode stills initially
                                    this.posterUrl = TmdbHelper.getStillUrl(still)
                                }
                            }
                        )
                    }
                }
            }
            
            newTvSeriesLoadResponse(name, url, TvType.TvSeries, episodesData) {
                this.posterUrl = imageLink
                this.plot = plot
                this.rating = rating
                this.year = year
            }
        } else {
            // Find the movie file link
            doc.select("tbody > tr:gt(1)").forEach {
                val folderHtml = it.select("td.fb-n > a")
                if (folderHtml.isNotEmpty()) {
                    val fileName = folderHtml.text()
                    if (fileName.contains(Regex("\\.(mkv|mp4|avi)$", RegexOption.IGNORE_CASE))) {
                        link = mainUrl + folderHtml.attr("href")
                    }
                }
            }
            
            newMovieLoadResponse(name, url, TvType.Movie, link) {
                this.posterUrl = imageLink
                this.plot = plot
                this.rating = rating
                this.year = year
            }
        }
    }

    private fun parseSeasonInfo(folderName: String): Pair<Int?, String?> {
        // Try to match common season patterns
        val seasonPatterns = listOf(
            Regex("(?i)season\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(?i)s(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(?i)series\\s*(\\d+)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in seasonPatterns) {
            val match = pattern.find(folderName)
            if (match != null) {
                val seasonNum = match.groupValues[1].toIntOrNull()
                if (seasonNum != null) {
                    return Pair(seasonNum, null) // Return season number, no custom name
                }
            }
        }
        
        // If no season number found, use folder name as custom season name
        // Clean up the folder name for display and normalize common special season names
        val cleanName = folderName.replace(Regex("[%_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { name ->
                // Normalize common special season names
                when (name.lowercase()) {
                    "oav", "oavs" -> "OAV"
                    "ova", "ovas" -> "OVA"
                    "special", "specials" -> "Specials"
                    "movie", "movies" -> "Movies"
                    "extra", "extras" -> "Extras"
                    "bonus" -> "Bonus"
                    else -> name
                }
            }
        
        return Pair(null, cleanName) // Return null for season number, custom name
    }

    private suspend fun seasonExtractor(
        url: String, 
        episodesData: MutableList<Episode>, 
        seasonNum: Int,
        tmdbId: Int?,
        seasonName: String? = null
    ) = withContext(Dispatchers.IO) {
        val doc = app.get(url).document
        
        // If we have TMDB ID, fetch season details first
        val seasonDetails = if (tmdbId != null) {
            TmdbHelper.getSeasonDetails(tmdbId, seasonNum)
        } else null
        
        // Get all episode links and parse their episode numbers from filenames
        val episodes = doc.select("tbody > tr:gt(1)").mapNotNull {
            val folderHtml = it.select("td.fb-n > a")
            val name = folderHtml.text()
            val link = mainUrl + folderHtml.attr("href")
            
            if (!name.contains(Regex("\\.(jpg|jpeg|png)$", RegexOption.IGNORE_CASE))) {
                // Try to parse episode number from filename
                val episodePattern = Regex("[Ss]\\d{1,2}[Ee](\\d{1,3})")
                val episodeNum = episodePattern.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                
                // If no episode number found and this is a special season, use sequential numbering
                val finalEpisodeNum = if (episodeNum == null && seasonName != null) {
                    // For special seasons, use sequential numbering starting from 1
                    null // We'll handle this later
                } else {
                    episodeNum
                }
                
                Triple(name, link, finalEpisodeNum)
            } else null
        }
        
        // Sort episodes - those with episode numbers first, then others
        val sortedEpisodes = episodes.sortedWith(compareBy<Triple<String, String, Int?>> { it.third == null }.thenBy { it.third })
        
        // Assign episode numbers to episodes that don't have them
        val finalEpisodes = sortedEpisodes.mapIndexed { index, (name, link, epNum) ->
            Triple(name, link, epNum ?: (index + 1))
        }

        // Process episodes in parallel
        val deferredEpisodes = finalEpisodes.map { (name, link, epNum) ->
            async {
                val episodeDetails = if (tmdbId != null) {
                    // Pass filename to get correct episode details
                    TmdbHelper.getEpisodeDetails(tmdbId, seasonNum, epNum, name)
                } else null
                
                newEpisode(link) {
                    val baseName = episodeDetails?.name ?: cleanEpisodeName(name)
                    // For special seasons, use a clear naming format
                    this.name = if (seasonName != null) {
                        "🎬 $seasonName - $baseName"
                    } else {
                        baseName
                    }
                    this.season = seasonNum
                    this.episode = epNum
                    this.description = if (seasonName != null) {
                        val seasonDesc = "📁 $seasonName Collection"
                        if (episodeDetails?.overview?.isNotEmpty() == true) {
                            "$seasonDesc\n\n${episodeDetails.overview}"
                        } else {
                            seasonDesc
                        }
                    } else {
                        episodeDetails?.overview
                    }
                    episodeDetails?.rating?.let { rating ->
                        this.rating = (rating * 10).toInt()
                    }
                    episodeDetails?.stillPath?.let { still ->
                        // Use small size for episode stills initially
                        this.posterUrl = TmdbHelper.getStillUrl(still)
                    }
                }
            }
        }

        // Wait for all episodes to be processed and add them to episodesData
        episodesData.addAll(deferredEpisodes.awaitAll())
    }

    private fun extractYear(name: String): Int? {
        // Try to extract year from patterns like "(2021)" or "(TV Series 2021-2025)"
        val yearPattern1 = Regex("\\((\\d{4})\\)")
        val yearPattern2 = Regex("\\(TV Series (\\d{4})-\\d{4}\\)")
        
        return yearPattern1.find(name)?.groupValues?.get(1)?.toIntOrNull()
            ?: yearPattern2.find(name)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun cleanEpisodeName(name: String): String {
        // Remove patterns like "S01E01", "1080p", "WEBRip", etc.
        return name.replace(Regex("S\\d{2}E\\d{2}"), "")
            .replace(Regex("\\d{3,4}p"), "")
            .replace(Regex("NF WEBRip"), "")
            .replace(Regex("WEBRip"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\.(mkv|mp4|avi)"), "")
            .trim()
    }

    private fun containsAnyLoop(text: String, keyword: List<String>?): Boolean {
        if (!keyword.isNullOrEmpty()) {
            for (keyword in keyword) {
                if (text.contains(keyword, ignoreCase = true)) {
                    return true // Return immediately if a match is found
                }
            }
        }
        return false // Return false if no match is found after checking all keywords
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data, this.name, url = data, type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    data class SearchResult(
        val search: List<Search>
    )

    data class Search(
        val fetched: Boolean,
        val href: String,
        val managed: Boolean,
        val size: Long?,
        val time: Long
    )
}