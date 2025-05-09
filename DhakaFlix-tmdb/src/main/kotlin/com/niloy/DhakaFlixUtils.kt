package com.niloy

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
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
     * Common search implementation that can be used by all DhakaFlix providers
     * @param api The MainAPI instance that will create the search responses
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
        
        // First try exact match search
        val body = 
            "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$cleanQuery\",\"ignorecase\":true}}".toRequestBody(
                "application/json".toMediaType()
            )
        val doc = app.post("$mainUrl/$serverName/", requestBody = body).text
        val searchJson = AppUtils.parseJson<BdixDhakaFlix14Provider.SearchResult>(doc)
        
        // Process direct matches
        val directMatches = searchJson.search.take(30).filter { post -> post.size == null }
        
        if (directMatches.isNotEmpty()) {
            directMatches.forEach { post -> 
                val href = post.href
                val url = mainUrl + href
                val name = nameFromUrl(href)
                val cleanName = cleanNameForSearch(name)
                
                // Try to find the poster
                val posterUrl = findPosterFunc(url)
                
                // Create search response directly
                val searchResult = api.newMovieSearchResponse(cleanName, url, TvType.Movie) {
                    if (posterUrl?.isNotEmpty() == true) {
                        this.posterUrl = posterUrl
                    }
                }
                searchResponse.add(searchResult)
            }
        }
        
        // If insufficient results, try different search techniques
        if (searchResponse.isEmpty() || searchResponse.size < 5) {
            // Try searching with individual words for better results
            val words = cleanQuery.split(" ").filter { it.length > 3 } // Only use words with more than 3 characters
            
            if (words.isNotEmpty()) {
                // Try each significant word
                for (word in words) {
                    // Skip if we already have enough results
                    if (searchResponse.size >= 15) break
                    
                    val wordBody =
                        "{\"action\":\"get\",\"search\":{\"href\":\"/$serverName/\",\"pattern\":\"$word\",\"ignorecase\":true}}".toRequestBody(
                            "application/json".toMediaType()
                        )
                    val wordDoc = app.post("$mainUrl/$serverName/", requestBody = wordBody).text
                    val wordSearchJson = AppUtils.parseJson<BdixDhakaFlix14Provider.SearchResult>(wordDoc)
                    
                    // Calculate relevance by checking how many words from the query appear in the result
                    wordSearchJson.search.filter { post -> 
                        post.size == null && 
                        searchResponse.none { it.url == mainUrl + post.href } // Avoid duplicates
                    }.take(10).forEach { post -> 
                        val href = post.href
                        val url = mainUrl + href
                        val name = nameFromUrl(href)
                        val cleanName = cleanNameForSearch(name)
                        
                        // Only add if there's some relevant match
                        val relevanceScore = calculateRelevance(cleanName, query)
                        if (relevanceScore > 0.3) { // At least 30% relevance
                            val posterUrl = findPosterFunc(url)
                            
                            // Create search response directly
                            val searchResult = api.newMovieSearchResponse(cleanName, url, TvType.Movie) {
                                if (posterUrl?.isNotEmpty() == true) {
                                    this.posterUrl = posterUrl
                                }
                            }
                            searchResponse.add(searchResult)
                        }
                    }
                }
            }
        }
        
        // Sort results by relevance to the query
        return searchResponse.sortedByDescending { 
            calculateRelevance(cleanNameForSearch(it.name), query) 
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
    }
    
    /**
     * Common poster finding implementation
     */
    suspend fun findPoster(contentUrl: String, mainUrl: String): String? {
        try {
            val doc = app.get(contentUrl).document
            // Look for common poster file names
            val posterPatterns = listOf("a_AL_.jpg", "poster.jpg", "cover.jpg", ".jpg", ".png", ".jpeg")
            
            // First check for specific poster filenames
            doc.select("tbody > tr:gt(1)").forEach { row -> 
                val filename = row.select("td.fb-n > a").text()
                if (posterPatterns.any { pattern -> filename.contains(pattern, ignoreCase = true) }) {
                    return mainUrl + row.select("td.fb-n > a").attr("href")
                }
            }
        } catch (e: Exception) {
            // Silent fail if we can't load the document
        }
        return null
    }
}