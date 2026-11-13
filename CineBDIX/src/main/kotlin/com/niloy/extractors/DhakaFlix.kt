package com.niloy.extractors

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.niloy.DHAKAFLIX_SERVERS
import com.niloy.getIndexQuality
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object DhakaFlix {

    private fun parseDhakaQuality(filename: String): String {
        val s = filename.lowercase()
        return when {
            s.contains("2160p") || s.contains("4k") -> "2160p"
            s.contains("1080p") -> "1080p"
            s.contains("720p") -> "720p"
            s.contains("480p") -> "480p"
            else -> "hd"
        }
    }

    suspend fun invokeDhakaFlix(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        val isTv = season != null
        val meta = resolveMeta(rawTitle, rawYear, tmdbId, isTv) ?: return
        val clean = meta.title.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
        val variants = listOf(
            clean.replace(" ", "\\ "),
            clean.replace(" ", ".")
        )

        val seen = mutableSetOf<String>()

        for (query in variants) {
            var foundAny = false
            for (server in DHAKAFLIX_SERVERS) {
                runCatching {
                    val body = JSONObject().apply {
                        put("action", "get")
                        put("search", JSONObject().apply {
                            put("href", server.root)
                            put("ignorecase", true)
                            put("pattern", query)
                        })
                    }
                    val bodyStr = body.toString()
                    val res = app.post(
                        "${server.url}${server.root}",
                        requestBody = bodyStr.toRequestBody("application/json".toMediaTypeOrNull()),
                        headers = mapOf(
                            "User-Agent" to ExtractorUtils.USER_AGENT,
                            "Content-Type" to "application/json"
                        )
                    ).text
                    val root = JSONObject(res)
                    val searchArr = root.optJSONArray("search") ?: return@runCatching

                    for (i in 0 until searchArr.length()) {
                        val item = searchArr.optJSONObject(i) ?: continue
                        val href = item.optString("href")
                        val lowerHref = href.lowercase()
                        if (!lowerHref.endsWith(".mkv") && !lowerHref.endsWith(".mp4") && !lowerHref.endsWith(".avi")) continue

                        // Media type filter
                        if (!isTv) {
                            if (lowerHref.contains("tv-web-series") || lowerHref.contains("tv series")) continue
                            if (meta.year != null && !lowerHref.contains(meta.year.toString())) continue
                        } else {
                            if (lowerHref.contains("english movies") || lowerHref.contains("hindi movies") || lowerHref.contains("movies")) continue
                            val sStr = season.toString().padStart(2, '0')
                            val eStr = (episode ?: 1).toString().padStart(2, '0')
                            val epRegex = Regex("(?i)s${sStr}[. _-]?e${eStr}")
                            if (!epRegex.containsMatchIn(lowerHref)) continue
                        }

                        val streamUrl = "${server.url}$href"
                        if (!seen.add(streamUrl)) continue
                        foundAny = true

                        val filename = href.substringAfterLast('/')
                        val quality = parseDhakaQuality(filename)

                        // Rich tag extraction
                        val combined = "$filename $streamUrl".lowercase()
                        val source = when {
                            combined.contains("bluray") -> "Blu-ray"
                            combined.contains("webrip") || combined.contains("hdrip") -> "WEB-Rip"
                            else -> "WEB-DL"
                        }
                        val hdrTag = when {
                            combined.contains("hdr10+") || combined.contains("hdr10p") -> "HDR10+"
                            combined.contains("hdr10") -> "HDR10"
                            combined.contains("hdr") -> "HDR"
                            combined.contains("sdr") -> "SDR"
                            else -> ""
                        }
                        val codec = if (combined.contains("hevc") || combined.contains("x265") || combined.contains("265")) "H.265" else "H.264"

                        val tags = listOfNotNull(source, hdrTag.ifBlank { null }, codec).joinToString(" \u2022 ")
                        val qualityUpper = quality.uppercase()
                        val isM3u8 = streamUrl.contains(".m3u8")

                        callback(
                            newExtractorLink(
                                "DhakaFlix FTP",
                                "DhakaFlix FTP \u2022 $qualityUpper \u2022 $tags",
                                streamUrl,
                                if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                            ) {
                                this.quality = getIndexQuality(quality)
                                this.headers = mapOf("User-Agent" to ExtractorUtils.USER_AGENT)
                            }
                        )
                    }
                }
            }
            if (foundAny) break
        }
    }
}

