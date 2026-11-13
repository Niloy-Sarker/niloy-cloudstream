package com.niloy.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.niloy.CTGMOVIES_API
import com.niloy.CTGMOVIES_BASE
import com.niloy.getIndexQuality
import org.json.JSONArray
import org.json.JSONObject

object CtgMovies {

    private data class CtgSearchItem(
        val title: String,
        val id: String,
        val kind: String,
        val type: String,
        val year: Int?
    )

    private suspend fun ctgApiGet(path: String, queryParams: Map<String, String>): String? {
        val queryStr = queryParams.entries.joinToString("&", prefix = "?") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }
        val p = if (path.startsWith("/")) path else "/$path"
        val headers = mapOf(
            "User-Agent" to ExtractorUtils.USER_AGENT,
            "Accept" to "application/json",
            "Referer" to "$CTGMOVIES_BASE/",
            "Origin" to CTGMOVIES_BASE
        )

        suspend fun tryUrl(url: String): String? {
            return runCatching {
                var res = app.get(url, headers = headers)
                if (res.code in 500..599) {
                    res = app.get(url, headers = headers)
                }
                if (res.isSuccessful) res.text else null
            }.getOrNull()
        }

        val primary = tryUrl("$CTGMOVIES_API$p$queryStr")
        if (primary != null) return primary
        return tryUrl("$CTGMOVIES_BASE/api/v1$p$queryStr")
    }

    private fun parseCtgSearchItems(raw: String?, kind: String): List<CtgSearchItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val list = mutableListOf<CtgSearchItem>()
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            val arr = parsed?.optJSONArray("movies") ?: parsed?.optJSONArray("results")
                ?: parsed?.optJSONArray("data")
                ?: runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title").ifBlank {
                    obj.optString("name").ifBlank { obj.optString("english_title") }
                }
                if (title.isBlank()) continue
                val id = obj.optString("slug").ifBlank {
                    obj.optString("id").ifBlank { obj.optString("_id") }
                }
                if (id.isBlank()) continue

                val year = obj.optInt("year").takeIf { it > 0 }
                    ?: parseYear(obj.optString("release_date"))
                    ?: parseYear(obj.optString("first_air_date"))

                val isAnime = kind == "anime" || (obj.optBoolean("is_anime") && kind != "tv")
                val type = when {
                    kind == "movies" -> "movie"
                    isAnime -> "anime"
                    else -> "tv"
                }

                list.add(CtgSearchItem(title, id, kind, type, year))
            }
            list
        }.getOrDefault(emptyList())
    }

    suspend fun invokeCtgMovies(
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
            val params = mapOf("search" to targetTitle)
            val items = mutableListOf<CtgSearchItem>()
            items.addAll(parseCtgSearchItems(ctgApiGet("/movies", params), "movies"))
            items.addAll(parseCtgSearchItems(ctgApiGet("/tv", params), "tv"))
            items.addAll(parseCtgSearchItems(ctgApiGet("/anime", params), "anime"))
            if (items.isEmpty()) return

            val targetNorm = normalizeTitle(targetTitle)
            var bestItem: CtgSearchItem? = null
            var bestScore = -1

            for (item in items) {
                val itemNorm = normalizeTitle(item.title)
                var score = -1
                if (itemNorm == targetNorm) {
                    score = 100
                    if (targetYear != null && item.year == targetYear) score += 50
                } else if (itemNorm.contains(targetNorm) && targetNorm.length >= 4) {
                    score = 60
                    if (targetYear != null && item.year == targetYear) score += 30
                } else if (targetNorm.contains(itemNorm) && itemNorm.length >= 4) {
                    score = 40
                }

                if (isTv && (item.type == "tv" || item.type == "anime")) score += 10
                if (!isTv && (item.type == "movie" || item.type == "anime")) score += 10

                if (score > bestScore) {
                    bestScore = score
                    bestItem = item
                }
            }

            if (bestScore < 30 || bestItem == null) return

            val detailRaw = ctgApiGet("/${bestItem.kind}/${urlEncode(bestItem.id)}", emptyMap()) ?: return
            val detailObj = JSONObject(detailRaw)

            val linksArr = JSONArray()
            if (!isTv) {
                val rawLinks = detailObj.optJSONArray("links")
                if (rawLinks != null) {
                    for (i in 0 until rawLinks.length()) linksArr.put(rawLinks.getJSONObject(i))
                }
            } else {
                val targetSeason = season
                val targetEpisode = episode ?: 1
                val episodesArr = detailObj.optJSONArray("episodes") ?: return
                for (i in 0 until episodesArr.length()) {
                    val epObj = episodesArr.optJSONObject(i) ?: continue
                    val epNum = epObj.optInt("episode_number").takeIf { it > 0 }
                        ?: epObj.optInt("absolute_number").takeIf { it > 0 } ?: (i + 1)
                    val sNum = epObj.optInt("season_number").takeIf { it > 0 } ?: 1
                    if (sNum == targetSeason && epNum == targetEpisode) {
                        val epLinks = epObj.optJSONArray("links") ?: continue
                        for (j in 0 until epLinks.length()) linksArr.put(epLinks.getJSONObject(j))
                    }
                }
            }

            val seen = mutableSetOf<String>()
            for (i in 0 until linksArr.length()) {
                val link = linksArr.optJSONObject(i) ?: continue
                if (link.optBoolean("broken")) continue

                var rawUrl = link.optString("url").ifBlank {
                    link.optString("file").ifBlank {
                        link.optString("src").ifBlank { link.optString("link") }
                    }
                }
                if (rawUrl.isBlank()) continue

                val finalUrl = when {
                    rawUrl.startsWith("//") -> "https:$rawUrl"
                    rawUrl.startsWith("/") -> "$CTGMOVIES_BASE$rawUrl"
                    else -> rawUrl
                }
                if (!seen.add(finalUrl)) continue

                var quality = resolveQuality(finalUrl)
                if (quality == "Unknown") {
                    val qHint = link.optString("quality")
                    val qMatch = Regex("(?i)(2160|1440|1080|720|480|360)p?").find(qHint)
                    if (qMatch != null) {
                        quality = "${qMatch.groupValues[1]}p"
                    }
                }

                val lang = link.optString("language").ifBlank { link.optString("lang", "English") }
                val isM3u8 = finalUrl.contains(".m3u8")

                callback(
                    newExtractorLink(
                        "CTGMovies",
                        "CTGMovies \u2022 $quality ($lang)",
                        finalUrl,
                        if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) {
                        this.quality = getIndexQuality(quality)
                        this.headers = mapOf(
                            "User-Agent" to ExtractorUtils.USER_AGENT,
                            "Referer" to "$CTGMOVIES_BASE/",
                            "Accept" to "video/webm,video/ogg,video/*;q=0.9,application/ogg;q=0.7,audio/*;q=0.6,*/*;q=0.5"
                        )
                    }
                )
            }
        }
    }
}

