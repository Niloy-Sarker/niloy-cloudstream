package com.niloy.extractors

import com.lagradost.cloudstream3.app
import com.niloy.TMDB_API
import com.niloy.settings.Settings
import org.json.JSONObject
import java.net.URLEncoder

object ExtractorUtils {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
}

data class MediaMeta(val title: String, val year: Int?)

suspend fun resolveMeta(title: String?, year: Int?, tmdbId: Int?, isTv: Boolean): MediaMeta? {
    if (!title.isNullOrBlank()) {
        return MediaMeta(title, year)
    }
    if (tmdbId != null && tmdbId > 0) {
        val key = Settings.getTmdbApiKey()
        val endpoint = if (isTv) "tv" else "movie"
        return runCatching {
            val json = app.get("$TMDB_API/$endpoint/$tmdbId?api_key=$key").text
            val obj = JSONObject(json)
            val resolvedTitle = if (isTv) obj.optString("name") else obj.optString("title")
            val date = if (isTv) obj.optString("first_air_date") else obj.optString("release_date")
            val resolvedYear = date.take(4).toIntOrNull()
            if (resolvedTitle.isNotBlank()) MediaMeta(resolvedTitle, resolvedYear) else null
        }.getOrNull()
    }
    return null
}

fun urlEncode(str: String): String = runCatching {
    URLEncoder.encode(str, "UTF-8")
}.getOrDefault(str)

fun normalizeTitle(title: String?): String {
    return (title ?: "")
        .replace(Regex("(?i)\\b(1080p|720p|480p|2160p|4k|web[- ]?dl|webrip|bluray|hdrip|x264|x265|hevc|10bit|dual[- ]?audio|hindi[- ]?dubbed|dubbed|esub)\\b"), " ")
        .replace(Regex("\\[[^\\]]*\\]"), " ")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

fun parseYear(text: String?): Int? {
    val matches = Regex("(?:19|20)\\d{2}").findAll(text ?: "").mapNotNull { it.value.toIntOrNull() }.toList()
    return matches.firstOrNull { it in 1900..2035 }
}

fun resolveQuality(vararg parts: String?): String {
    val combined = parts.filterNotNull().joinToString(" ")
    val match = Regex("(?i)(2160p|1440p|1080p|720p|576p|540p|480p|360p|4k|uhd)").find(combined)
    val quality = match?.groupValues?.getOrNull(1)?.lowercase() ?: return "Unknown"
    return if (quality == "4k" || quality == "uhd") "2160p" else quality
}

