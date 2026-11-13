package com.niloy

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/**
 * Defines a provider and its execution logic for Standard, Anime, and MALSync data.
 * The `CineStreamExtractors.` receiver allows direct access to internal scraping functions.
 */
data class ProviderDef(
    val key: String,
    val displayName: String,
    val isTorrent: Boolean = false,
    val executeStandard: (suspend CineStreamExtractors.(res: AllLoadLinksData, subCb: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) -> Unit)? = null,
    val executeAnime: (suspend CineStreamExtractors.(res: AllLoadLinksData, subCb: (SubtitleFile) -> Unit, cb: (ExtractorLink) -> Unit) -> Unit)? = null,
)

object ProviderRegistry {

    val builtInProviders = listOf(
        ProviderDef(
            key = "p_circleftp",
            displayName = "CircleFTP",
            executeStandard = { res, _, cb ->
                invokeCircleFtp(res.title ?: res.imdbTitle, res.year ?: res.imdbYear, res.season, res.episode, res.tmdbId, cb)
            },
            executeAnime = { res, _, cb ->
                invokeCircleFtp(res.imdbTitle ?: res.title, res.imdbYear ?: res.year, res.imdbSeason ?: res.season, res.imdbEpisode ?: res.episode, res.tmdbId, cb)
            }
        ),
        ProviderDef(
            key = "p_dhakaflix",
            displayName = "DhakaFlix",
            executeStandard = { res, _, cb ->
                invokeDhakaFlix(res.title ?: res.imdbTitle, res.year ?: res.imdbYear, res.season, res.episode, res.tmdbId, cb)
            },
            executeAnime = { res, _, cb ->
                invokeDhakaFlix(res.imdbTitle ?: res.title, res.imdbYear ?: res.year, res.imdbSeason ?: res.season, res.imdbEpisode ?: res.episode, res.tmdbId, cb)
            }
        ),
        ProviderDef(
            key = "p_flixhub",
            displayName = "FlixHub",
            executeStandard = { res, subCb, cb ->
                invokeFlixHub(res.title ?: res.imdbTitle, res.year ?: res.imdbYear, res.season, res.episode, res.tmdbId, subCb, cb)
            },
            executeAnime = { res, subCb, cb ->
                invokeFlixHub(res.imdbTitle ?: res.title, res.imdbYear ?: res.year, res.imdbSeason ?: res.season, res.imdbEpisode ?: res.episode, res.tmdbId, subCb, cb)
            }
        ),
        ProviderDef(
            key = "p_ctgmovies",
            displayName = "CTGMovies",
            executeStandard = { res, _, cb ->
                invokeCtgMovies(res.title ?: res.imdbTitle, res.year ?: res.imdbYear, res.season, res.episode, res.tmdbId, cb)
            },
            executeAnime = { res, _, cb ->
                invokeCtgMovies(res.imdbTitle ?: res.title, res.imdbYear ?: res.year, res.imdbSeason ?: res.season, res.imdbEpisode ?: res.episode, res.tmdbId, cb)
            }
        ),
    )

    // Dynamically provided to Settings.kt
    val keys get() = builtInProviders.map { it.key }
    val namesMap get() = builtInProviders.associate { it.key to it.displayName }
    val torrentKeys get() = builtInProviders.filter { it.isTorrent }.map { it.key }.toSet()
}
