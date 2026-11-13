package com.niloy.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.niloy.FLIXHUB_URL
import com.niloy.getIndexQuality
import org.jsoup.Jsoup

object FlixHub {

    private fun toSlug(title: String): String {
        return title.lowercase()
            .replace("'", "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private data class FlixHubResult(
        val streamUrl: String,
        val quality: String,
        val subtitles: List<SubtitleFile> = emptyList()
    )

    private suspend fun parseFlixHubPlayer(doc: org.jsoup.nodes.Document): FlixHubResult? {
        val player = doc.selectFirst("#theaterPlayer") ?: return null

        val rawStream = player.attr("data-stream-url").ifBlank { null }
            ?: player.selectFirst("video source[src], source[src]")?.attr("src")?.ifBlank { null }
            ?: doc.selectFirst("[data-download-url]")?.attr("data-download-url")?.substringBefore("?")?.ifBlank { null }
            ?: return null

        val streamUrl = if (rawStream.startsWith("http")) rawStream else "$FLIXHUB_URL$rawStream"

        // Collect subtitle tracks and track labels for quality resolution
        val trackLabels = mutableListOf<String>()
        val subtitles = mutableListOf<SubtitleFile>()
        player.select("track[kind=subtitles]").forEach { track ->
            val src = track.attr("src").trim()
            val label = track.attr("label").trim()
            if (label.isNotBlank()) trackLabels.add(label)
            if (src.isNotBlank()) {
                val subUrl = if (src.startsWith("http")) src else "$FLIXHUB_URL$src"
                val lang = track.attr("srclang").ifBlank { label }.ifBlank { "en" }
                subtitles.add(newSubtitleFile(lang, subUrl))
            }
        }

        val quality = resolveQuality(streamUrl, *trackLabels.toTypedArray())

        return FlixHubResult(streamUrl, quality, subtitles)
    }

    suspend fun invokeFlixHub(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        subtitleCallback: ((SubtitleFile) -> Unit)? = null,
        callback: (ExtractorLink) -> Unit
    ) {
        val isTv = season != null
        val meta = resolveMeta(rawTitle, rawYear, tmdbId, isTv) ?: return
        val slug = toSlug(normalizeTitle(meta.title)).ifBlank { toSlug(meta.title) }
        if (slug.isBlank()) return

        val segment = if (isTv) "series" else "movie"
        val headers = mapOf(
            "User-Agent" to ExtractorUtils.USER_AGENT,
            "Referer" to "$FLIXHUB_URL/",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )

        var watchHtml: String? = null
        for (candidate in listOf(slug, "$slug-1")) {
            val url = "$FLIXHUB_URL/watch/$segment/$candidate"
            val html = runCatching { app.get(url, headers = headers).text }.getOrNull()
            if (html?.contains("theaterPlayer") == true) {
                watchHtml = html
                break
            }
        }
        if (watchHtml == null) return

        val finalResult: FlixHubResult? = if (!isTv) {
            parseFlixHubPlayer(Jsoup.parse(watchHtml))
        } else {
            val doc = Jsoup.parse(watchHtml)
            val targetSeason = season
            val targetEpisode = episode ?: 1

            var targetWatchUrl: String? = null
            val cards = doc.select("article.player-sidebar-card[data-season-number]")
            for (card in cards) {
                val sNum = card.attr("data-season-number").toIntOrNull() ?: continue
                if (sNum != targetSeason) continue

                val titleText = card.selectFirst(".player-sidebar-card-title")?.text() ?: ""
                val epMatch = Regex("(?i)E(\\d+)").find(titleText) ?: Regex("(\\d+)").find(titleText)
                val epNum = epMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: continue
                if (epNum == targetEpisode) {
                    val anchor = card.selectFirst("a.player-sidebar-card-link, a[href*='/watch/episode/']")
                    val href = anchor?.attr("href")?.trim().orEmpty()
                    if (href.isNotBlank()) {
                        targetWatchUrl = if (href.startsWith("http")) href else "$FLIXHUB_URL$href"
                    }
                    break
                }
            }

            if (targetWatchUrl == null && targetSeason == 1) {
                val epLinks = doc.select("a[href*='/watch/episode/']")
                for ((idx, link) in epLinks.withIndex()) {
                    val text = link.text()
                    val epNum = Regex("(?i)E(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: Regex("(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: (idx + 1)
                    if (epNum == targetEpisode) {
                        val href = link.attr("href").trim()
                        if (href.isNotBlank()) {
                            targetWatchUrl = if (href.startsWith("http")) href else "$FLIXHUB_URL$href"
                            break
                        }
                    }
                }
            }

            if (targetWatchUrl != null) {
                val epHtml = runCatching { app.get(targetWatchUrl, headers = headers).text }.getOrNull()
                if (epHtml != null) {
                    parseFlixHubPlayer(Jsoup.parse(epHtml))
                } else null
            } else if (targetSeason == 1 && targetEpisode == 1) {
                parseFlixHubPlayer(doc)
            } else {
                null
            }
        }

        if (finalResult != null) {
            finalResult.subtitles.forEach { subtitleCallback?.invoke(it) }

            val quality = finalResult.quality
            val isM3u8 = finalResult.streamUrl.contains(".m3u8")
            callback(
                newExtractorLink(
                    "FlixHub FTP",
                    "FlixHub FTP \u2022 $quality",
                    finalResult.streamUrl,
                    if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.quality = getIndexQuality(quality)
                    this.headers = mapOf(
                        "User-Agent" to ExtractorUtils.USER_AGENT,
                        "Referer" to "$FLIXHUB_URL/"
                    )
                }
            )
        }
    }
}

