package com.niloy


import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element


class DflixMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://dflix.discoveryftp.net"
    override var name = "(BDIX) Dflix Movies"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie
    )
    override val mainPage = mainPageOf(
        "category/Bangla" to "Bangla",
        "category/English" to "English",
        "category/Hindi" to "Hindi",
        "category/Tamil" to "Tamil",
        "category/Animation" to "Animation",
        "category/Others" to "Others"
    )

    private var loginCookie: Map<String, String>? = null
    private suspend fun login() {
        if (loginCookie?.size != 2) {
            val client =
                app.get("https://dflix.discoveryftp.net/login/demo", allowRedirects = false)
            loginCookie = client.cookies
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        login()
        val doc = app.get("$mainUrl/m/${request.data}/$page", cookies = loginCookie!!).document
        val homeResponse = doc.select("div.card")
        val home = homeResponse.mapNotNull { post ->
            toResult(post)
        }
        return newHomePageResponse(request.name, home, true)
    }

    private fun toResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("div.card > a:nth-child(1)").attr("href")
        val title = post.select("div.card > div:nth-child(2) > h3:nth-child(1)").text()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            // Try multiple selectors to find the poster
            this.posterUrl = post.selectFirst("div.poster > img")?.attr("src") 
                ?: post.selectFirst("img")?.attr("src")
                ?: post.selectFirst("div.card img")?.attr("src")
                ?: post.selectFirst("a img")?.attr("src")
            val check = post.select("div.card > a:nth-child(1) > span:nth-child(1)").text()
            this.quality = getSearchQuality(check)
            addDubStatus(
                dubExist = when {
                    "DUAL" in check -> true
                    else -> false
                },
                subExist = false
            )
        }
    }

    private fun toSearchResult(post: Element): SearchResponse {
        val url = mainUrl + post.select("a").attr("href")
        val title = post.select("div.searchtitle").text()
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            // Search results show blank poster, so don't set posterUrl - it will be fetched from movie page
            this.posterUrl = null
            // Extract quality from searchdetails if available
            val details = post.select("div.searchdetails").text()
            this.quality = getSearchQualityFromText(details)
            addDubStatus(
                dubExist = details.contains("DUAL", ignoreCase = true),
                subExist = false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        login()
        val doc = app.get("$mainUrl/m/find/$query", cookies = loginCookie!!).document
        
        // Check if search results use the new structure
        val searchItems = doc.select("div.moviesearchiteam")
        if (searchItems.isNotEmpty()) {
            return searchItems.mapNotNull { post ->
                toSearchResult(post)
            }
        }
        
        // Fallback to old structure
        val searchResponse = doc.select("div.card:not(:has(div.poster.disable))")
        return searchResponse.mapNotNull { post ->
            toResult(post)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        login()
        val doc = app.get(url, cookies = loginCookie!!).document
        val title = doc.select(".movie-detail-content > h3:nth-child(1)").text()
        val dataUrl = doc.select("div.col-md-12:nth-child(3) > div:nth-child(1) > a:nth-child(1)")
            .attr("href")
        val browseUrl = doc.select("a:contains(Browse)").attr("href")
        val size = doc.select(".badge.badge-fill").text()
        val img = doc.select(".movie-detail-banner > img:nth-child(1)").attr("src")
        
        // Create a data object that contains both dataUrl and browseUrl
        val loadData = MovieLoadData(dataUrl, browseUrl)
        
        return newMovieLoadResponse(title, url, TvType.Movie, loadData.toJson()) {
            this.posterUrl = img
            this.plot = "<b>$size</b><br><br>" + doc.select(".storyline").text()
            this.tags = doc.select(".ganre-wrapper > a").map { it.text().replace(",", "") }
            this.actors = doc.select("div.col-lg-2").map { actor(it) }
            this.recommendations = doc.select("div.badge-outline > a").map { qualityRecommendations(it,title,img) }
        }
    }
    private fun qualityRecommendations(post: Element, title:String, imageLink:String): SearchResponse{
        val movieName = title +" "+ post.text()
        val movieUrl = mainUrl + post.attr("href")
        return newMovieSearchResponse(movieName,movieUrl,TvType.Movie) {
            this.posterUrl = imageLink
        }
    }

    private fun actor(post: Element): ActorData {
        val html = post.select("div.col-lg-2 > a:nth-child(1) > img:nth-child(1)")
        val img = html.attr("src")
        val name = html.attr("alt")
        return ActorData(
            actor = Actor(
                name,
                img
            ), roleString = post.select("div.col-lg-2 > p.text-center.text-white").text()
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        login()
        
        // If data is a simple string URL (backwards compatibility)
        if (data.startsWith("http")) {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = data,
                    type = ExtractorLinkType.VIDEO
                )
            )
            return true
        }
        
        // Parse the new data format
        val loadData = try {
            parseJson<MovieLoadData>(data)
        } catch (e: Exception) {
            // Fallback to old method if parsing fails
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = data,
                    type = ExtractorLinkType.VIDEO
                )
            )
            return true
        }
        
        // First, add the main stream link
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = loadData.dataUrl,
                type = ExtractorLinkType.VIDEO
            )
        )
        
        // Now fetch mirror links from browse directory
        if (loadData.browseUrl.isNotEmpty()) {
            try {
                val browseDoc = app.get(loadData.browseUrl, cookies = loginCookie!!).document
                val processedFiles = mutableSetOf<String>()
                val docText = browseDoc.text()
                
                // Specific pattern for this file server format
                // Looking for lines like: |   | filename.mkv | Preview | size | date |
                val specificFilePattern = Regex("""\|\s*\|\s*([^|]*\.mkv)\s*\|\s*[^|]*\s*\|\s*([^|]*?(?:KB|MB|GB|TB))\s*\|""")
                specificFilePattern.findAll(docText).forEach { match ->
                    val fileName = match.groupValues[1].trim()
                    val fileSize = match.groupValues[2].trim()
                    
                    if (!processedFiles.contains(fileName)) {
                        processedFiles.add(fileName)
                        val fileUrl = loadData.browseUrl.trimEnd('/') + "/" + fileName
                        val quality = getQualityFromFileName(fileName)
                        
                        callback.invoke(
                            newExtractorLink(
                                "$name [Mirror]",
                                "$fileName - $fileSize",
                                url = fileUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.quality = quality
                            }
                        )
                    }
                }
                
                // Fallback: General pattern for any .mkv file with size info
                if (processedFiles.isEmpty()) {
                    val generalPattern = Regex("""([^|\s]+\.mkv)[^|]*\|[^|]*\|[^|]*([0-9.]+\s*(?:KB|MB|GB|TB))""")
                    generalPattern.findAll(docText).forEach { match ->
                        val fileName = match.groupValues[1].trim()
                        val fileSize = match.groupValues[2].trim()
                        
                        if (!processedFiles.contains(fileName)) {
                            processedFiles.add(fileName)
                            val fileUrl = loadData.browseUrl.trimEnd('/') + "/" + fileName
                            val quality = getQualityFromFileName(fileName)
                            
                            callback.invoke(
                                newExtractorLink(
                                    "$name [Mirror]",
                                    "$fileName - $fileSize",
                                    url = fileUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = quality
                                }
                            )
                        }
                    }
                }
                
                // Last resort: Just find any .mkv files mentioned
                if (processedFiles.isEmpty()) {
                    val simplePattern = Regex("""([^\s|]+\.mkv)""")
                    simplePattern.findAll(docText).forEach { match ->
                        val fileName = match.value.trim()
                        
                        if (!processedFiles.contains(fileName)) {
                            processedFiles.add(fileName)
                            val fileUrl = loadData.browseUrl.trimEnd('/') + "/" + fileName
                            val quality = getQualityFromFileName(fileName)
                            
                            callback.invoke(
                                newExtractorLink(
                                    "$name [Mirror]",
                                    "$fileName - Unknown size",
                                    url = fileUrl,
                                    type = ExtractorLinkType.VIDEO
                                ) {
                                    this.quality = quality
                                }
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                // If browsing fails, we still have the main link
                println("Failed to fetch mirror links: ${e.message}")
                e.printStackTrace()
            }
        }
        
        return true
    }

    /**
     * Gets secure download URL for a specific file by searching the movie page for download links
     */
    private suspend fun getSecureDownloadUrl(fileName: String, browseUrl: String): String {
        return try {
            // Extract the movie path from browse URL to construct the direct file URL
            // browseUrl format: http://cds1.discoveryftp.net/Movies/Hindi/2025/Sitaare%20Zameen%20Par
            // We want: http://cds1.discoveryftp.net/Movies/Hindi/2025/Sitaare%20Zameen%20Par/filename.mkv
            val baseUrl = browseUrl.trimEnd('/')
            val directUrl = "$baseUrl/$fileName"
            
            // Method 1: Try to get the secure URL by making a request to the direct file URL
            // The server should redirect us to the secure URL with MD5 and expires parameters
            val response = app.get(directUrl, cookies = loginCookie!!, allowRedirects = false)
            
            // Check if we got a redirect with the secure URL
            val locationHeader = response.headers["location"] 
            if (locationHeader?.contains("md5=") == true && locationHeader.contains("expires=")) {
                return locationHeader
            }
            
            // Method 2: Try following the redirect manually
            val followResponse = app.get(directUrl, cookies = loginCookie!!, allowRedirects = true)
            val finalUrl = followResponse.url
            
            if (finalUrl.contains("md5=") && finalUrl.contains("expires=")) {
                return finalUrl
            }
            
            // Method 3: Try accessing with different request headers
            val headerResponse = app.get(directUrl, cookies = loginCookie!!, 
                headers = mapOf(
                    "Accept" to "video/mp4,video/*,*/*",
                    "Range" to "bytes=0-1"
                ), 
                allowRedirects = false
            )
            
            val headerLocation = headerResponse.headers["location"]
            if (headerLocation?.contains("md5=") == true && headerLocation.contains("expires=")) {
                return headerLocation
            }
            
            // Method 4: Try HEAD request to trigger auth
            try {
                val headResponse = app.head(directUrl, cookies = loginCookie!!, allowRedirects = false)
                val headLocation = headResponse.headers["location"]
                if (headLocation?.contains("md5=") == true && headLocation.contains("expires=")) {
                    return headLocation
                }
            } catch (e: Exception) {
                // Continue to fallback
            }
            
            // Fallback: return direct URL (may still work for some cases)
            directUrl
            
        } catch (e: Exception) {
            // Fallback to direct URL construction if anything fails
            val baseUrl = browseUrl.trimEnd('/')
            "$baseUrl/$fileName"
        }
    }

    /**
     * Determines the search quality based on the presence of specific keywords in the input string.
     *
     * @param check The string to check for keywords.
     * @return The corresponding `SearchQuality` enum value, or `null` if no match is found.
     */
    private fun getSearchQuality(check: String?): SearchQuality? {
        val lowercaseCheck = check?.lowercase()
        if (lowercaseCheck != null) {
            return when {
                lowercaseCheck.contains("4k") -> SearchQuality.FourK
                lowercaseCheck.contains("web-r") || lowercaseCheck.contains("web-dl") -> SearchQuality.WebRip
                lowercaseCheck.contains("br") -> SearchQuality.BlueRay
                lowercaseCheck.contains("hdts") || lowercaseCheck.contains("hdcam") || lowercaseCheck.contains(
                    "hdtc"
                ) -> SearchQuality.HdCam

                lowercaseCheck.contains("cam") -> SearchQuality.Cam
                lowercaseCheck.contains("hd") || lowercaseCheck.contains("1080p") -> SearchQuality.HD
                else -> null
            }
        }
        return null
    }
    
    /**
     * Gets search quality from search details text like "Year : 2021 Category:Hindi Quality: 1080P WEB-DL"
     */
    private fun getSearchQualityFromText(details: String?): SearchQuality? {
        if (details == null) return null
        val lowercaseDetails = details.lowercase()
        return when {
            lowercaseDetails.contains("4k") -> SearchQuality.FourK
            lowercaseDetails.contains("web-dl") || lowercaseDetails.contains("webrip") -> SearchQuality.WebRip
            lowercaseDetails.contains("bluray") || lowercaseDetails.contains("br-rip") -> SearchQuality.BlueRay
            lowercaseDetails.contains("hdts") || lowercaseDetails.contains("hdcam") || lowercaseDetails.contains("hdtc") -> SearchQuality.HdCam
            lowercaseDetails.contains("cam") -> SearchQuality.Cam
            lowercaseDetails.contains("1080p") || lowercaseDetails.contains("hd") -> SearchQuality.HD
            lowercaseDetails.contains("720p") -> SearchQuality.HD
            else -> null
        }
    }
    
    /**
     * Gets quality value from filename for video links
     */
    private fun getQualityFromFileName(fileName: String): Int {
        val lowercaseFileName = fileName.lowercase()
        return when {
            lowercaseFileName.contains("4k") || lowercaseFileName.contains("2160p") -> 2160
            lowercaseFileName.contains("1080p") -> 1080
            lowercaseFileName.contains("720p") -> 720
            lowercaseFileName.contains("480p") -> 480
            // Special case for DS4K which indicates 1080p content upscaled for 4K display
            lowercaseFileName.contains("ds4k") -> 1080
            else -> 1080 // default to 1080p if unclear
        }
    }
    
    /**
     * Gets quality label for display purposes
     */
    private fun getQualityLabel(fileName: String): String {
        val lowercaseFileName = fileName.lowercase()
        return when {
            lowercaseFileName.contains("4k") && lowercaseFileName.contains("2160p") -> "4K UHD"
            lowercaseFileName.contains("4k") -> "4K"
            lowercaseFileName.contains("2160p") -> "4K UHD"
            lowercaseFileName.contains("1080p") && lowercaseFileName.contains("ds4k") -> "1080p DS4K"
            lowercaseFileName.contains("1080p") -> "1080p HD"
            lowercaseFileName.contains("720p") -> "720p HD" 
            lowercaseFileName.contains("480p") -> "480p"
            lowercaseFileName.contains("ds4k") -> "1080p DS4K"
            else -> "HD"
        }
    }
}

data class MovieLoadData(
    val dataUrl: String,
    val browseUrl: String
)