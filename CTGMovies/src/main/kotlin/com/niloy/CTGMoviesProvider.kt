package com.niloy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.net.URLEncoder

class CTGMoviesProvider : MainAPI() {
    override var mainUrl = "https://ctgmovies.com"
    override var name = "CTGMovies"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override var lang = "en"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies?page=" to "Movies",
        "$mainUrl/tv?page=" to "TV Shows",
        "$mainUrl/anime?page=" to "Anime"
    )

    companion object {
        private val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    // Data models for parsing JSON embedded inside Next.js RSC payload
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LinkTrack(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("label") val label: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MediaLink(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("quality") val quality: String? = null,
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("size_bytes") val sizeBytes: Long? = null,
        @JsonProperty("subtitle_tracks") val subtitleTracks: List<LinkTrack>? = null,
        @JsonProperty("audio_tracks") val audioTracks: List<LinkTrack>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class EpisodeItem(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("still_url") val stillUrl: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("links") val links: List<MediaLink>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MediaDetails(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("poster_url") val posterUrl: String? = null,
        @JsonProperty("backdrop_url") val backdropUrl: String? = null,
        @JsonProperty("rating") val rating: Float? = null,
        @JsonProperty("imdb_rating") val imdbRating: Float? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("genres") val genres: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("links") val links: List<MediaLink>? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MediaContainer(
        @JsonProperty("kind") val kind: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("movie") val movie: MediaDetails? = null,
        @JsonProperty("series") val series: MediaDetails? = null,
        @JsonProperty("links") val links: List<MediaLink>? = null
    )

    // Helper to decode Next.js image proxy URLs: /_next/image?url=https%3A%2F%2F...
    private fun fixImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            if (url.contains("/_next/image?url=")) {
                val encoded = url.substringAfter("/_next/image?url=").substringBefore("&")
                URLDecoder.decode(encoded, "UTF-8")
            } else if (url.startsWith("http")) {
                url
            } else {
                fixUrl(url)
            }
        } catch (e: Exception) {
            url
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val requestUrl = "${request.data}$page"
        val response = app.get(requestUrl)
        val document = Jsoup.parse(response.text, mainUrl)

        val items = parseListingCards(document)
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=${URLEncoder.encode(query, "UTF-8")}"
        val response = app.get(searchUrl)
        val document = Jsoup.parse(response.text, mainUrl)
        return parseListingCards(document)
    }

    private fun parseListingCards(document: Document): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()

        // Select all anchor tags pointing to movies, tv shows, or anime
        val cardElements = document.select("a[href^=/movies/], a[href^=/tv/], a[href^=/anime/], a[href*='ctgmovies.com/movies/'], a[href*='ctgmovies.com/tv/'], a[href*='ctgmovies.com/anime/']")

        for (el in cardElements) {
            val rawHref = el.attr("href")
            // Exclude games or other non-video content
            if (rawHref.contains("/games/")) continue

            val fullUrl = fixUrl(rawHref)
            if (seen.contains(fullUrl)) continue
            seen.add(fullUrl)

            val img = el.selectFirst("img")
            var title = img?.attr("alt")?.trim()
            if (title.isNullOrBlank()) {
                title = el.selectFirst(".font-display, [class*='font-display']")?.text()?.trim()
            }
            if (title.isNullOrBlank()) {
                title = el.text().trim()
            }
            if (title.isBlank()) continue

            val posterRaw = img?.attr("src") ?: img?.attr("data-src")
            val posterUrl = fixImageUrl(posterRaw)

            // Extract Year
            val yearText = el.selectFirst(".font-mono, [class*='font-mono']")?.text() ?: el.text()
            val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(yearText)
            val year = yearMatch?.value?.toIntOrNull()

            // Extract Quality
            val qualityBadge = el.selectFirst("span")?.text()?.trim()
            val quality = when {
                qualityBadge?.contains("1080", true) == true -> SearchQuality.HD
                qualityBadge?.contains("720", true) == true -> SearchQuality.HD
                qualityBadge?.contains("480", true) == true -> SearchQuality.SD
                qualityBadge?.contains("4K", true) == true || qualityBadge?.contains("2160", true) == true -> SearchQuality.UHD
                else -> null
            }

            val tvType = when {
                rawHref.contains("/anime/") -> TvType.Anime
                rawHref.contains("/tv/") -> TvType.TvSeries
                else -> TvType.Movie
            }

            if (tvType == TvType.Movie) {
                results.add(
                    newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                        this.posterUrl = posterUrl
                        this.year = year
                        this.quality = quality
                    }
                )
            } else {
                results.add(
                    newTvSeriesSearchResponse(title, fullUrl, tvType) {
                        this.posterUrl = posterUrl
                        this.year = year
                        this.quality = quality
                    }
                )
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val html = response.text
        val document = Jsoup.parse(html, mainUrl)

        val isAnime = url.contains("/anime/")
        val isTv = url.contains("/tv/")
        val isSeries = isAnime || isTv

        // Parse Next.js App Router RSC stream data
        val (mediaDetails, episodesList, directLinks) = extractRscMediaData(html)

        // Fallbacks from DOM / Meta tags
        val title = mediaDetails?.title
            ?: mediaDetails?.name
            ?: document.selectFirst("h1")?.text()?.substringBefore("(")?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.substringBefore("(")?.trim()
            ?: "Unknown"

        val posterUrl = fixImageUrl(
            mediaDetails?.posterUrl
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("div[class*='aspect-[2/3]'] img, .aspect-\\[2\\/3\\] img")?.let { it.attr("src").ifBlank { it.attr("data-src") } }
        )

        val domBackdrop = document.selectFirst(
            "div.absolute.inset-0.overflow-hidden img, " +
            "div[class*='absolute'][class*='inset-0'] img, " +
            "div[class*='overflow-hidden'][class*='pointer-events-none'] img, " +
            "img[class*='opacity-30'], " +
            "img[class*='brightness-'], " +
            "img[class*='saturate-'], " +
            "img[src*='/w1280/'], " +
            "img[data-src*='/w1280/']"
        )?.let { img ->
            img.attr("src").ifBlank { null } ?: img.attr("data-src").ifBlank { null }
        }

        val backdropUrl = fixImageUrl(
            mediaDetails?.backdropUrl
                ?: domBackdrop
                ?: document.selectFirst("meta[property=og:image:secure_url]")?.attr("content")
        )

        val domPlot = document.selectFirst(
            "section:has(h2:matchesWholeText((?i).*synopsis.*)) p, " +
            "section:has(h3:matchesWholeText((?i).*synopsis.*)) p, " +
            "section:has(h2:contains(Synopsis)) p, " +
            "section:has(h3:contains(Synopsis)) p, " +
            "h2:contains(Synopsis) ~ p, " +
            "h3:contains(Synopsis) ~ p, " +
            "div:has(> h2:contains(Synopsis)) p, " +
            "section.mt-8 p"
        )?.text()?.trim()?.ifBlank { null }

        val plot = mediaDetails?.overview?.ifBlank { null }
            ?: domPlot
            ?: document.selectFirst("meta[name=description]")?.attr("content")
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val year = mediaDetails?.year
            ?: Regex("""\b(19\d\d|20\d\d)\b""").find(document.selectFirst("h1")?.text() ?: "")?.value?.toIntOrNull()

        val rating = mediaDetails?.rating
            ?: mediaDetails?.imdbRating

        val tags = mediaDetails?.genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }

        if (isSeries) {
            val tvType = if (isAnime) TvType.Anime else TvType.TvSeries

            val episodes = mutableListOf<Episode>()
            for (ep in episodesList) {
                val sNum = ep.seasonNumber ?: 1
                val eNum = ep.episodeNumber ?: 1
                val epName = ep.name ?: "Episode $eNum"
                val epStill = fixImageUrl(ep.stillUrl)
                val epPlot = ep.overview

                // Serialize links if available in this episode object
                val epDataJson = if (!ep.links.isNullOrEmpty()) {
                    mapper.writeValueAsString(ep.links)
                } else {
                    // Fallback to media URL with season & episode indicators
                    "$url#S${sNum}E${eNum}"
                }

                episodes.add(
                    newEpisode(epDataJson) {
                        this.name = epName
                        this.season = sNum
                        this.episode = eNum
                        this.description = epPlot
                        this.posterUrl = epStill
                    }
                )
            }

            return newTvSeriesLoadResponse(title, url, tvType, episodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl
                this.plot = plot
                this.year = year
                this.score = rating?.let { Score.from10(it.toDouble()) }
                this.tags = tags
                this.duration = mediaDetails?.runtime
            }
        } else {
            // Movie Load Response - combine RSC, DOM, and Regex extracted links
            val movieLinks = mutableListOf<MediaLink>()
            movieLinks.addAll(directLinks)
            if (movieLinks.isEmpty()) {
                movieLinks.addAll(extractDomAndRegexMovieLinks(document, html))
            }

            val movieDataJson = if (movieLinks.isNotEmpty()) {
                mapper.writeValueAsString(movieLinks)
            } else {
                url
            }

            return newMovieLoadResponse(title, url, TvType.Movie, movieDataJson) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl
                this.plot = plot
                this.year = year
                this.score = rating?.let { Score.from10(it.toDouble()) }
                this.tags = tags
                this.duration = mediaDetails?.runtime
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var links = mutableListOf<MediaLink>()

        if (data.startsWith("[") && data.endsWith("]")) {
            // Pre-extracted links JSON
            try {
                val parsed = mapper.readValue<List<MediaLink>>(data)
                links.addAll(parsed)
            } catch (e: Exception) {
                // Ignore parse errors and fallback
            }
        }

        // If links were not in data or data is a URL, fetch and extract from page
        if (links.isEmpty() && data.startsWith("http")) {
            val pageUrl = data.substringBefore("#")
            try {
                val response = app.get(pageUrl)
                val doc = Jsoup.parse(response.text, mainUrl)
                val (_, episodesList, directLinks) = extractRscMediaData(response.text)

                if (data.contains("#S") && data.contains("E")) {
                    val sNum = Regex("""#S(\d+)""").find(data)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                    val eNum = Regex("""E(\d+)""").find(data)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                    val matchingEp = episodesList.find { (it.seasonNumber ?: 1) == sNum && (it.episodeNumber ?: 1) == eNum }
                    if (!matchingEp?.links.isNullOrEmpty()) {
                        links.addAll(matchingEp!!.links!!)
                    }
                } else {
                    links.addAll(directLinks)
                    if (links.isEmpty()) {
                        links.addAll(extractDomAndRegexMovieLinks(doc, response.text))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (links.isEmpty()) return false

        var emitted = false
        for (link in links) {
            val streamUrl = link.url ?: continue
            if (streamUrl.isBlank()) continue

            val qualityStr = link.quality ?: "Direct"
            val serverName = link.source ?: "Server"
            val qualityInt = when {
                qualityStr.contains("4K", true) || qualityStr.contains("2160", true) -> Qualities.P2160.value
                qualityStr.contains("1080", true) -> Qualities.P1080.value
                qualityStr.contains("720", true) -> Qualities.P720.value
                qualityStr.contains("480", true) -> Qualities.P480.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                newExtractorLink(
                    name = this.name,
                    source = "${this.name} - $qualityStr ($serverName)",
                    url = streamUrl
                ) {
                    this.quality = qualityInt
                    this.referer = "$mainUrl/"
                }
            )
            emitted = true

            // Send Subtitle Tracks
            link.subtitleTracks?.forEach { sub ->
                val subUrl = sub.url?.let { fixUrl(it) }
                if (!subUrl.isNullOrBlank()) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            lang = sub.label ?: sub.language ?: "English",
                            url = subUrl
                        )
                    )
                }
            }
        }

        return emitted
    }

    private fun extractDomAndRegexMovieLinks(document: Document, html: String): List<MediaLink> {
        val result = mutableListOf<MediaLink>()
        val seenUrls = mutableSetOf<String>()

        // 1. Subtitles
        val subtitles = mutableListOf<LinkTrack>()
        document.select("a[href*='.srt'], a[href*='.vtt'], a[href*='/subtitles/']").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isNotBlank()) {
                val lang = if (href.contains("hin", true) || href.contains("hindi", true)) "Hindi" else "English"
                subtitles.add(LinkTrack(url = fixUrl(href), language = lang, label = lang))
            }
        }
        Regex("""https?://[^\s"'\\]+\.(?:srt|vtt)""").findAll(html.replace("\\\"", "\"")).forEach { m ->
            val href = m.value
            val lang = if (href.contains("hin", true) || href.contains("hindi", true)) "Hindi" else "English"
            val fullHref = fixUrl(href)
            if (subtitles.none { it.url == fullHref }) {
                subtitles.add(LinkTrack(url = fullHref, language = lang, label = lang))
            }
        }

        // 2. DOM anchor links
        val domLinks = document.select("a[href*='.mp4'], a[href*='.mkv'], a[href*='.m3u8'], a[href*='ctgfun.com'], a[href*='ftp.']")
        for (a in domLinks) {
            val href = a.attr("href").trim()
            if (href.isBlank() || seenUrls.contains(href)) continue
            if (isAudioTrackOrSub(href)) continue

            seenUrls.add(href)
            val quality = detectQualityFromUrlOrText(href, a.text())
            val server = if (href.contains("ftp.", true)) "Server A" else if (href.contains("data.", true)) "Server B" else "Server"

            result.add(
                MediaLink(
                    url = href,
                    quality = quality,
                    source = server,
                    subtitleTracks = subtitles
                )
            )
        }

        // 3. Regex scan
        val regex = Regex("""https?://[^\s"'\\]+(?:\.mp4|\.mkv|\.m3u8)""")
        val cleanHtml = html.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\/", "/")
        for (match in regex.findAll(cleanHtml)) {
            val url = match.value
            if (seenUrls.contains(url)) continue
            if (isAudioTrackOrSub(url)) continue

            seenUrls.add(url)
            val quality = detectQualityFromUrlOrText(url, "")
            val server = if (url.contains("ftp.", true)) "Server A" else if (url.contains("data.", true)) "Server B" else "Server"

            result.add(
                MediaLink(
                    url = url,
                    quality = quality,
                    source = server,
                    subtitleTracks = subtitles
                )
            )
        }

        return result
    }

    private fun isAudioTrackOrSub(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".srt") ||
                lower.endsWith(".vtt") ||
                lower.contains(".audio.") ||
                lower.endsWith(".audio.hin.mp4") ||
                lower.endsWith(".audio.eng.mp4") ||
                lower.endsWith(".audio.jpn.mp4") ||
                lower.endsWith(".audio.tam.mp4") ||
                lower.endsWith(".audio.tel.mp4") ||
                lower.endsWith(".audio.mal.mp4")
    }

    private fun detectQualityFromUrlOrText(url: String, text: String): String {
        val combined = "$url $text".lowercase()
        return when {
            combined.contains("2160p") || combined.contains("4k") -> "4K"
            combined.contains("1080p") -> "1080p WebRip"
            combined.contains("720p") -> "720p WebRip"
            combined.contains("480p") -> "480p"
            combined.contains("hdts") -> "HDTS"
            combined.contains("bluray") -> "BluRay"
            else -> "Direct"
        }
    }

    // Helper method to extract media details, episodes, and direct links from RSC stream
    private fun extractRscMediaData(html: String): Triple<MediaDetails?, List<EpisodeItem>, List<MediaLink>> {
        var mediaDetails: MediaDetails? = null
        val episodesMap = mutableMapOf<Pair<Int, Int>, EpisodeItem>()
        val directLinks = mutableListOf<MediaLink>()

        try {
            // Find all script tags containing self.__next_f.push
            val pushRegex = Regex("""self\.__next_f\.push\(\[1,"(.*?)"]\)""", RegexOption.DOT_MATCHES_ALL)
            val combinedBuilder = StringBuilder()
            for (match in pushRegex.findAll(html)) {
                combinedBuilder.append(match.groupValues[1])
            }
            val rawCombined = combinedBuilder.toString()
            if (rawCombined.isBlank()) return Triple(null, emptyList(), emptyList())

            // Unescape Unicode and JS escapes
            val unescaped = unescapeNextString(rawCombined)

            val lines = unescaped.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // Strip Next.js RSC chunk ID prefix e.g. "47:"
                val jsonPayload = trimmed.replaceFirst(Regex("""^[0-9a-fA-F]+:"""), "")
                if (!jsonPayload.startsWith("{") && !jsonPayload.startsWith("[")) continue

                try {
                    val rootNode = mapper.readTree(jsonPayload)
                    // Traverse AST to locate container objects, episodes, links
                    val nodesToVisit = ArrayDeque<com.fasterxml.jackson.databind.JsonNode>()
                    nodesToVisit.add(rootNode)

                    while (nodesToVisit.isNotEmpty()) {
                        val node = nodesToVisit.removeFirst()

                        if (node.isObject) {
                            // Check for container with "data" property
                            val dataNode = node.get("data")
                            if (dataNode != null && dataNode.isObject) {
                                val container = mapper.treeToValue(dataNode, MediaContainer::class.java)
                                if (container != null) {
                                    val details = container.movie ?: container.series
                                    if (details != null && mediaDetails == null) {
                                        mediaDetails = details
                                    }
                                    if (!container.links.isNullOrEmpty()) {
                                        directLinks.addAll(container.links)
                                    }
                                }
                            }

                            // Check for direct "movie" or "series" details
                            val movieNode = node.get("movie")
                            if (movieNode != null && movieNode.isObject && mediaDetails == null) {
                                mediaDetails = mapper.treeToValue(movieNode, MediaDetails::class.java)
                            }
                            val seriesNode = node.get("series")
                            if (seriesNode != null && seriesNode.isObject && mediaDetails == null) {
                                mediaDetails = mapper.treeToValue(seriesNode, MediaDetails::class.java)
                            }

                            // Check for "links"
                            val linksNode = node.get("links")
                            if (linksNode != null && linksNode.isArray) {
                                try {
                                    val linksList = mapper.treeToValue(linksNode, Array<MediaLink>::class.java)?.toList()
                                    if (!linksList.isNullOrEmpty() && linksList.any { !it.url.isNullOrBlank() }) {
                                        for (l in linksList) {
                                            if (!l.url.isNullOrBlank() && directLinks.none { it.url == l.url }) {
                                                directLinks.add(l)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }

                            // Check for "episodes"
                            val episodesNode = node.get("episodes")
                            if (episodesNode != null && episodesNode.isArray) {
                                try {
                                    val epList = mapper.treeToValue(episodesNode, Array<EpisodeItem>::class.java)?.toList()
                                    if (!epList.isNullOrEmpty()) {
                                        for (ep in epList) {
                                            val key = Pair(ep.seasonNumber ?: 1, ep.episodeNumber ?: 1)
                                            val existing = episodesMap[key]
                                            if (existing == null || (!ep.links.isNullOrEmpty() && existing.links.isNullOrEmpty())) {
                                                episodesMap[key] = ep
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }

                            // Enqueue child fields
                            node.elements().forEach { nodesToVisit.add(it) }
                        } else if (node.isArray) {
                            node.elements().forEach { nodesToVisit.add(it) }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore line-level JSON parse failures
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sortedEpisodes = episodesMap.values.sortedWith(
            compareBy({ it.seasonNumber ?: 1 }, { it.episodeNumber ?: 1 })
        )

        return Triple(mediaDetails, sortedEpisodes, directLinks)
    }

    private fun unescapeNextString(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        val len = input.length
        while (i < len) {
            val c = input[i]
            if (c == '\\' && i + 1 < len) {
                val next = input[i + 1]
                when (next) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'u' -> {
                        if (i + 5 < len) {
                            val hex = input.substring(i + 2, i + 6)
                            try {
                                val code = hex.toInt(16)
                                sb.append(code.toChar())
                                i += 6
                            } catch (e: Exception) {
                                sb.append("\\u")
                                i += 2
                            }
                        } else {
                            sb.append("\\u")
                            i += 2
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
