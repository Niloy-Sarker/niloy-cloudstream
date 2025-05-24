package com.niloy

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Utility class for common DhakaFlix provider functions
 * This removes duplicate code across multiple provider implementations
 */
object DhakaFlixUtils {
    
    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")
    
    private fun cleanNameForSearch(name: String): String {
        return name
            // Remove anything in parentheses
            .replace(Regex("\\([^)]*\\)"), "")
            // Remove anything in square brackets
            .replace(Regex("\\[[^\\]]*\\]"), "")
            // Remove quality indicators
            .replace(Regex("(?i)(480p|720p|1080p|2160p|4k|uhd|hdr|web-?dl|blu-?ray|dvdrip|brrip|webrip).*?(?=\\s|$)"), "")
            // Remove episode/season markers
            .replace(Regex("(?i)(s\\d{1,2}e\\d{1,2}|season\\s*\\d+|episode\\s*\\d+).*?(?=\\s|$)"), "")
            // Remove status indicators
            .replace(Regex("(?i)(complete|completed|ongoing|batch|repack|dual.audio|multi.sub|subtitle).*?(?=\\s|$)"), "")
            // Remove file extensions
            .replace(Regex("\\.(?:mp4|mkv|avi|mov|wmv)$"), "")
            // Remove multiple spaces and trim
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    fun nameFromUrl(href: String): String {
        val hrefDecoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        val name = nameRegex.find(hrefDecoded)?.groups?.get(1)?.value
        return name.toString()
    }
      /**
     * Fast search implementation optimized for performance
     * Removes poster fetching during search for faster results
     */
    suspend fun doSearch(
        query: String, 
        mainUrl: String,
        serverName: String,
        api: MainAPI,
        findPosterFunc: suspend (String) -> String?
    ): List<SearchResponse> {
        val searchResponse: MutableList<SearchResponse> = mutableListOf()
        
        // Clean the query for better searching
        val cleanQuery = query.replace(Regex("[^a-zA-Z0-9 ]"), " ").trim()
        
        // Single search request - don't do fallback searches for speed
        val body = 
            "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$cleanQuery\",\"ignorecase\":true}}".toRequestBody(
                "application/json".toMediaType()
            )
        val doc = app.post("$mainUrl/$serverName/", requestBody = body).text
        val searchJson = AppUtils.parseJson<BdixDhakaFlix14Provider.SearchResult>(doc)
        
        // Process matches quickly without poster fetching
        val matches = searchJson.search.take(40).filter { post -> post.size == null }        // Process matches with poster loading in batches for better performance
        val batchSize = 6
        val matchesWithPosters = coroutineScope {
            matches.chunked(batchSize).flatMap { batch ->
                batch.map { post ->
                    async {
                        val href = post.href
                        val url = mainUrl + href
                        val name = nameFromUrl(href)
                        val cleanName = cleanNameForSearch(name)
                        
                        // Get poster using the provided function
                        val posterUrl = findPosterFunc(url)
                        
                        api.newAnimeSearchResponse(cleanName, url, TvType.Movie) {
                            // Add poster if found
                            if (posterUrl?.isNotEmpty() == true) {
                                this.posterUrl = posterUrl
                            }
                            // Add dub status based on name content
                            addDubStatus(
                                dubExist = when {
                                    "Dual" in name -> true
                                    else -> false
                                }, 
                                subExist = when {
                                    "ESub" in name -> true
                                    else -> false
                                }
                            )
                        }
                    }
                }.map { it.await() }
            }
        }
          searchResponse.addAll(matchesWithPosters)
        
        // Skip fallback search if main search returned valid results
        // Only use fallback if we have no results at all from the main search
        if (searchResponse.isEmpty()) {
            val words = cleanQuery.split(" ").filter { it.length > 3 }
            if (words.isNotEmpty()) {
                val fallbackQuery = words.first()
                val fallbackBody =
                    "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$fallbackQuery\",\"ignorecase\":true}}".toRequestBody(
                        "application/json".toMediaType()
                    )
                val fallbackDoc = app.post("$mainUrl/$serverName/", requestBody = fallbackBody).text
                val fallbackJson = AppUtils.parseJson<BdixDhakaFlix14Provider.SearchResult>(fallbackDoc)
                  // Add relevant fallback results (limit to avoid too many results)
                val fallbackMatches = fallbackJson.search.filter { post -> 
                    post.size == null && 
                    searchResponse.none { it.url == mainUrl + post.href } // Avoid duplicates
                }.take(15).filter { post ->
                    val href = post.href
                    val name = nameFromUrl(href)
                    val cleanName = cleanNameForSearch(name)
                    
                    // Quick relevance check
                    cleanName.contains(cleanQuery, ignoreCase = true) || 
                    cleanQuery.split(" ").any { word -> cleanName.contains(word, ignoreCase = true) && word.length > 2 }
                }
                
                // Process fallback matches with poster loading
                val fallbackWithPosters = coroutineScope {
                    fallbackMatches.chunked(batchSize).flatMap { batch ->
                        batch.map { post ->
                            async {
                                val href = post.href
                                val url = mainUrl + href
                                val name = nameFromUrl(href)
                                val cleanName = cleanNameForSearch(name)
                                
                                // Get poster using the provided function
                                val posterUrl = findPosterFunc(url)
                                
                                api.newAnimeSearchResponse(cleanName, url, TvType.Movie) {
                                    // Add poster if found
                                    if (posterUrl?.isNotEmpty() == true) {
                                        this.posterUrl = posterUrl
                                    }
                                    // Add dub status based on name content
                                    addDubStatus(
                                        dubExist = when {
                                            "Dual" in name -> true
                                            else -> false
                                        }, 
                                        subExist = when {
                                            "ESub" in name -> true
                                            else -> false
                                        }
                                    )
                                }
                            }
                        }.map { it.await() }
                    }
                }
                
                searchResponse.addAll(fallbackWithPosters)
            }
        }
        
        // Simple sort by name relevance (faster than complex scoring)
        return searchResponse.sortedByDescending { result ->
            when {
                result.name.contains(cleanQuery, ignoreCase = true) -> 3.0
                cleanQuery.split(" ").count { word -> 
                    result.name.contains(word, ignoreCase = true) && word.length > 2 
                } > cleanQuery.split(" ").size / 2 -> 2.0
                else -> 1.0
            }
        }
    }
    
    /**
     * Calculate relevance score between a title and search query
     * Returns a score between 0 and 1
     */
    private fun calculateRelevance(title: String, query: String): Double {
        val cleanTitle = title.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        val cleanQuery = query.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim()
        
        // Exact title match gets highest priority
        if (cleanTitle == cleanQuery) return 1.0
        
        // Create normalized versions for comparison
        val normalizedTitle = cleanTitle.replace(Regex("\\s+"), " ")
        val normalizedQuery = cleanQuery.replace(Regex("\\s+"), " ")
        
        // Exact phrase match gets very high priority
        if (normalizedTitle.contains(normalizedQuery)) return 0.99
        
        // Split into words and remove common stop words
        val stopWords = setOf("the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with")
        val titleWords = normalizedTitle.split(" ").filter { it.length > 2 && !stopWords.contains(it) }
        val queryWords = normalizedQuery.split(" ").filter { it.length > 2 && !stopWords.contains(it) }
        
        if (queryWords.isEmpty()) return 0.0
        
        // Create a map of word positions for efficient lookup
        val titleWordPositions = mutableMapOf<String, MutableList<Int>>()
        titleWords.forEachIndexed { index, word ->
            titleWordPositions.getOrPut(word) { mutableListOf() }.add(index)
        }
        
        // Track exact word matches and their positions
        var exactMatchCount = 0
        val matchedPositions = mutableListOf<Int>()
        
        // Find exact word matches and their positions
        queryWords.forEach { queryWord ->
            titleWordPositions[queryWord]?.let { positions ->
                exactMatchCount++
                matchedPositions.addAll(positions)
            }
        }
        
        // Calculate longest sequence of matched words
        var longestSequence = 0
        var currentSequence = 0
        matchedPositions.sorted().zipWithNext { a, b ->
            if (b - a == 1) {
                currentSequence++
                longestSequence = maxOf(longestSequence, currentSequence)
            } else {
                currentSequence = 0
            }
        }
        
        // Calculate various scoring components
        val exactMatchScore = (exactMatchCount.toDouble() / queryWords.size) * 0.4  // 40% weight for exact matches
        val sequenceScore = (longestSequence.toDouble() / queryWords.size) * 0.3    // 30% weight for sequences
        
        // Position bonus: higher score if matches are at the start
        val firstMatchPosition = matchedPositions.minOrNull() ?: titleWords.size
        val positionScore = (1.0 - (firstMatchPosition.toDouble() / titleWords.size)) * 0.2
        
        // Density bonus: reward matches that are closer together
        val matchSpread = if (matchedPositions.isNotEmpty()) {
            (matchedPositions.max() - matchedPositions.min() + 1).toDouble() / matchedPositions.size
        } else {
            titleWords.size.toDouble()
        }
        val densityScore = (1.0 - (matchSpread / titleWords.size).coerceIn(0.0, 1.0)) * 0.1
        
        // Calculate final score
        val finalScore = exactMatchScore + sequenceScore + positionScore + densityScore
        
        // Apply additional bonuses
        val startBonus = if (titleWords.take(queryWords.size).containsAll(queryWords)) 0.2 else 0.0
        val exactOrderBonus = if (longestSequence == queryWords.size) 0.3 else 0.0
        
        return (finalScore + startBonus + exactOrderBonus).coerceIn(0.0, 1.0)
    }      /**
     * Enhanced poster finding implementation that prioritizes local images
     */
    suspend fun findPoster(contentUrl: String, mainUrl: String): String? {
        try {
            val doc = app.get(contentUrl).document
            
            // Priority-ordered list of poster patterns (most specific to general)
            val posterPatterns = listOf(
                "poster.jpg", "poster.jpeg", "poster.png",
                "cover.jpg", "cover.jpeg", "cover.png", 
                "a_AL_.jpg", "a_AL_.jpeg", "a_AL_.png",
                "thumbnail.jpg", "thumbnail.jpeg", "thumbnail.png",
                "fanart.jpg", "fanart.jpeg", "fanart.png"
            )
            
            val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")
            
            // First pass: Look for exact poster filename matches (highest priority)
            doc.select("tbody > tr:gt(1)").forEach { row -> 
                val filename = row.select("td.fb-n > a").text().lowercase()
                for (pattern in posterPatterns) {
                    if (filename == pattern) {
                        return mainUrl + row.select("td.fb-n > a").attr("href")
                    }
                }
            }
            
            // Second pass: Look for poster-like filenames (medium priority)
            doc.select("tbody > tr:gt(1)").forEach { row -> 
                val filename = row.select("td.fb-n > a").text().lowercase()
                if (filename.contains("poster") || filename.contains("cover")) {
                    for (ext in imageExtensions) {
                        if (filename.endsWith(ext)) {
                            return mainUrl + row.select("td.fb-n > a").attr("href")
                        }
                    }
                }
            }
            
            // Third pass: Look for any image files (lower priority)
            doc.select("tbody > tr:gt(1)").forEach { row -> 
                val filename = row.select("td.fb-n > a").text().lowercase()
                for (ext in imageExtensions) {
                    if (filename.endsWith(ext)) {
                        return mainUrl + row.select("td.fb-n > a").attr("href")
                    }
                }
            }
        } catch (e: Exception) {
            // Silent fail if we can't load the document
        }
        return null
    }
      /**
     * Lightweight poster finding optimized for search and main page
     * Only makes HTTP requests for local posters, no TMDB API calls
     */
    suspend fun findPosterLight(contentUrl: String, mainUrl: String): String? {
        return try {
            // Quick local poster check - only one HTTP request per content URL
            findPoster(contentUrl, mainUrl)
        } catch (e: Exception) {
            // Silent fail to avoid breaking search/main page performance
            null
        }
    }
}