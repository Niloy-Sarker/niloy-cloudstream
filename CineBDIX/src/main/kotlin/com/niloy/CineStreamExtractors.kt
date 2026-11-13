package com.niloy

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.niloy.extractors.CircleFtp
import com.niloy.extractors.CtgMovies
import com.niloy.extractors.DhakaFlix
import com.niloy.extractors.ExtractorUtils
import com.niloy.extractors.FlixHub
import com.niloy.settings.Settings
import org.json.JSONObject

object CineStreamExtractors {

    private const val TAG = "CineStreamBDIXExtractors"
    const val USER_AGENT = ExtractorUtils.USER_AGENT

    // ── Orchestrators ──────────────────────────────────────────

    suspend fun invokeAllSources(
        res: AllLoadLinksData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "invokeAllSources: $res")

        val stremioMap = getDynamicStremioMap(res.imdbId, res.season, res.episode, subtitleCallback, callback)

        val executionList = Settings.activeProviderOrder.mapNotNull { key ->
            ProviderRegistry.builtInProviders.find { it.key == key }?.executeStandard?.let { action ->
                suspend { this.action(res, subtitleCallback, callback) }
            } ?: stremioMap[key]
        }

        runLimitedAsync(concurrency = Settings.getConcurrency(), *executionList.toTypedArray())
    }

    suspend fun invokeAllAnimeSources(
        res: AllLoadLinksData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        Log.d(TAG, "invokeAllAnimeSources: $res")

        val stremioMap = getDynamicStremioMap(res.imdbId, res.imdbSeason, res.imdbEpisode, subtitleCallback, callback)

        val executionList = Settings.activeProviderOrder.mapNotNull { key ->
            ProviderRegistry.builtInProviders.find { it.key == key }?.executeAnime?.let { action ->
                suspend { this.action(res, subtitleCallback, callback) }
            } ?: stremioMap[key]
        }

        runLimitedAsync(concurrency = Settings.getConcurrency(), *executionList.toTypedArray())
    }

    private fun getDynamicStremioMap(
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Map<String, suspend () -> Unit> {
        return Settings.getStremioAddons().associate { addon ->
            val key = Settings.stremioAddonKey(addon.name)
            key to suspend {
                when (addon.type) {
                    Settings.AddonType.SUBTITLE -> invokeStremioSubtitlesGlobal(addon.name, addon.url, imdbId, season, episode, subtitleCallback)
                    Settings.AddonType.TORRENT -> invokeStremioTorrentsGlobal(addon.name, addon.url, imdbId, season, episode, callback)
                    Settings.AddonType.HTTPS, Settings.AddonType.DEBRID -> invokeStreamioStreamsGlobal(addon.name, addon.url, imdbId, season, episode, subtitleCallback, callback)
                }
            }
        }
    }

    private suspend fun invokeStremioSubtitlesGlobal(
        name: String,
        url: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return
        val path = if (season == null) "movie/$imdbId" else "series/$imdbId:$season:$episode"
        val endpoint = "$url/subtitles/$path.json"
        runCatching {
            val json = app.get(endpoint).text
            val subs = JSONObject(json).optJSONArray("subtitles") ?: return
            for (i in 0 until subs.length()) {
                val sub = subs.getJSONObject(i)
                val subUrl = sub.optString("url")
                val lang = sub.optString("lang", "en")
                if (subUrl.isNotBlank()) {
                    subtitleCallback(SubtitleFile(lang, subUrl))
                }
            }
        }
    }

    private suspend fun invokeStremioTorrentsGlobal(
        name: String,
        url: String,
        id: String?,
        season: Int?,
        episode: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        if (id.isNullOrBlank()) return
        val path = if (season == null) "movie/$id" else "series/$id:$season:$episode"
        val endpoint = "$url/stream/$path.json"
        runCatching {
            val json = app.get(endpoint).text
            val streams = JSONObject(json).optJSONArray("streams") ?: return
            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val infoHash = stream.optString("infoHash")
                if (infoHash.isNotBlank()) {
                    val magnet = "magnet:?xt=urn:btih:$infoHash"
                    val title = stream.optString("title", name)
                    callback(
                        newExtractorLink(
                            name,
                            "$name \u2022 $title",
                            magnet,
                            ExtractorLinkType.TORRENT
                        )
                    )
                }
            }
        }
    }

    private suspend fun invokeStreamioStreamsGlobal(
        name: String,
        url: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (imdbId.isNullOrBlank()) return
        val path = if (season == null) "movie/$imdbId" else "series/$imdbId:$season:$episode"
        val endpoint = "$url/stream/$path.json"
        runCatching {
            val json = app.get(endpoint).text
            val streams = JSONObject(json).optJSONArray("streams") ?: return
            for (i in 0 until streams.length()) {
                val stream = streams.getJSONObject(i)
                val streamUrl = stream.optString("url")
                if (streamUrl.isNotBlank()) {
                    val title = stream.optString("title", name)
                    val isM3u8 = streamUrl.contains(".m3u8")
                    callback(
                        newExtractorLink(
                            name,
                            "$name \u2022 $title",
                            streamUrl,
                            if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = getIndexQuality(title)
                        }
                    )
                }
            }
        }
    }

    // ── Delegates to Extractors ────────────────────────────────

    suspend fun invokeCircleFtp(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        CircleFtp.invokeCircleFtp(rawTitle, rawYear, season, episode, tmdbId, callback)
    }

    suspend fun invokeDhakaFlix(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        DhakaFlix.invokeDhakaFlix(rawTitle, rawYear, season, episode, tmdbId, callback)
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
        FlixHub.invokeFlixHub(rawTitle, rawYear, season, episode, tmdbId, subtitleCallback, callback)
    }

    suspend fun invokeCtgMovies(
        rawTitle: String?,
        rawYear: Int?,
        season: Int?,
        episode: Int?,
        tmdbId: Int?,
        callback: (ExtractorLink) -> Unit
    ) {
        CtgMovies.invokeCtgMovies(rawTitle, rawYear, season, episode, tmdbId, callback)
    }
}
