package com.niloy.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.niloy.CIRCLE_FTP_API
import com.niloy.CIRCLE_FTP_FALLBACK
import com.niloy.CIRCLE_FTP_URL
import com.niloy.getIndexQuality
import org.json.JSONArray
import org.json.JSONObject

object CircleFtp {

    private data class CirclePost(
        val id: String,
        val type: String,
        val title: String,
        val name: String
    )

    private data class CircleGroup(
        val cleanedTitle: String,
        val year: Int?,
        val posts: MutableList<CirclePost>
    )

    private suspend fun circleGet(path: String): String? {
        val p = if (path.startsWith("/")) path else "/$path"
        val headers = mapOf(
            "User-Agent" to ExtractorUtils.USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "$CIRCLE_FTP_URL/"
        )
        val primary = runCatching { app.get("$CIRCLE_FTP_API$p", headers = headers).text }.getOrNull()
        if (!primary.isNullOrBlank()) return primary
        return runCatching { app.get("$CIRCLE_FTP_FALLBACK$p", headers = headers).text }.getOrNull()
    }

    suspend fun invokeCircleFtp(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val isTv = season != null
        val meta = resolveMeta(rawTitle, rawYear, tmdbId, isTv) ?: return
        val targetTitle = meta.title
        val targetYear = meta.year

        runCatching {
            val text = circleGet("/api/posts?searchTerm=${urlEncode(targetTitle)}&order=desc") ?: return
            val posts = mutableListOf<CirclePost>()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            val rawArr = parsed?.optJSONArray("posts") ?: parsed?.optJSONArray("data")
                ?: runCatching { JSONArray(text) }.getOrNull() ?: return

            for (i in 0 until rawArr.length()) {
                val item = rawArr.optJSONObject(i) ?: continue
                val type = item.optString("type")
                if (type == "singleVideo" || type == "series") {
                    posts.add(
                        CirclePost(
                            id = item.optString("id"),
                            type = type,
                            title = item.optString("title"),
                            name = item.optString("name")
                        )
                    )
                }
            }
            if (posts.isEmpty()) return

            // Group posts by cleaned title + year
            val groups = mutableMapOf<String, CircleGroup>()
            for (post in posts) {
                var cleaned = post.name.trim()
                if (cleaned.isBlank() || cleaned.lowercase() == "null") {
                    cleaned = post.title
                        .replace(Regex("\\.[a-zA-Z0-9]{2,4}$"), "")
                        .replace(Regex("[._-]"), " ")
                        .replace(Regex("\\b(19|20)\\d{2}\\b"), "")
                        .trim()
                }
                val year = parseYear(post.title) ?: parseYear(post.name)
                val key = normalizeTitle(cleaned) + (if (year != null) "|$year" else "")
                val group = groups.getOrPut(key) { CircleGroup(cleaned, year, mutableListOf()) }
                group.posts.add(post)
            }

            // Score groups
            val targetNorm = normalizeTitle(targetTitle)
            var bestGroup: CircleGroup? = null
            var bestScore = -1

            for (g in groups.values) {
                val itemNorm = normalizeTitle(g.cleanedTitle)
                var score = -1
                if (itemNorm == targetNorm) {
                    score = 100
                    if (targetYear != null && g.year == targetYear) score += 50
                } else if (targetNorm.length >= 4 && itemNorm.contains(targetNorm)) {
                    score = 60
                    if (targetYear != null && g.year == targetYear) score += 30
                } else if (itemNorm.length >= 4 && targetNorm.contains(itemNorm)) {
                    score = 40
                }

                val mainType = g.posts.firstOrNull()?.type
                if (!isTv && mainType == "singleVideo") score += 10
                if (isTv && mainType == "series") score += 10

                if (score > bestScore) {
                    bestScore = score
                    bestGroup = g
                }
            }

            if (bestScore < 30 || bestGroup == null) return

            // Hydrate group posts
            val links = mutableListOf<Pair<String, String>>() // url to title
            for (p in bestGroup.posts) {
                val postJson = circleGet("/api/posts/${p.id}") ?: continue
                val postObj = runCatching {
                    val root = JSONObject(postJson)
                    root.optJSONObject("post") ?: root.optJSONObject("data") ?: root
                }.getOrNull() ?: continue

                if (isTv) {
                    val contentArr = postObj.optJSONArray("content") ?: continue
                    val targetSeason = season
                    val targetEpisode = episode ?: 1

                    // Try index-based lookup first
                    val byIndex = contentArr.optJSONObject(targetSeason - 1)
                    val episodesByIndex = byIndex?.optJSONArray("episodes")
                    val epByIndex = episodesByIndex?.optJSONObject(targetEpisode - 1)
                    val urlByIndex = epByIndex?.optString("link")?.ifBlank { epByIndex.optString("url") }
                    if (!urlByIndex.isNullOrBlank()) {
                        links.add(urlByIndex to (epByIndex.optString("title").ifBlank { postObj.optString("title") }))
                        continue
                    }

                    // Traverse seasons
                    for (si in 0 until contentArr.length()) {
                        val sObj = contentArr.optJSONObject(si) ?: continue
                        val sName = sObj.optString("seasonName").ifBlank { sObj.optString("name") }
                        val sNum = Regex("\\d+").find(sName)?.value?.toIntOrNull() ?: (si + 1)
                        if (sNum != targetSeason) continue

                        val epsArr = sObj.optJSONArray("episodes") ?: continue
                        for (ei in 0 until epsArr.length()) {
                            val epObj = epsArr.optJSONObject(ei) ?: continue
                            val eVal = epObj.optString("episode").ifBlank {
                                epObj.optString("number").ifBlank { epObj.optString("ep") }
                            }
                            val eNum = Regex("\\d+").find(eVal)?.value?.toIntOrNull() ?: (ei + 1)
                            if (eNum != targetEpisode) continue

                            val epUrl = epObj.optString("link").ifBlank { epObj.optString("url") }
                            if (epUrl.isNotBlank()) {
                                links.add(epUrl to (epObj.optString("title").ifBlank { postObj.optString("title") }))
                            }
                        }
                    }
                } else {
                    val content = postObj.optString("content")
                    if (content.isNotBlank() && content.startsWith("http")) {
                        links.add(content.trim() to postObj.optString("title"))
                    }
                }
            }

            // Emit unique streams
            val seen = mutableSetOf<String>()
            for ((url, title) in links) {
                if (!seen.add(url)) continue
                val quality = resolveQuality(url, title)
                val isM3u8 = url.contains(".m3u8")
                callback(
                    newExtractorLink(
                        "Circle FTP",
                        "Circle FTP \u2022 $quality",
                        url,
                        if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getIndexQuality(quality)
                        this.headers = mapOf(
                            "User-Agent" to ExtractorUtils.USER_AGENT,
                            "Referer" to "$CIRCLE_FTP_URL/"
                        )
                    }
                )
            }
        }
    }
}

