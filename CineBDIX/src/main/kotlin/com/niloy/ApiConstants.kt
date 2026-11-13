package com.niloy

// ── Static APIs for Ported Scrapers ──────────────────────────

const val CIRCLE_FTP_URL = "http://new.circleftp.net"
const val CIRCLE_FTP_API = "http://new.circleftp.net:5000"
const val CIRCLE_FTP_FALLBACK = "http://15.1.1.50:5000"

const val FLIXHUB_URL = "https://flixhub.net"

const val CTGMOVIES_BASE = "https://ctgmovies.com"
const val CTGMOVIES_API = "https://cockpit.103.109.92.178.nip.io/api/v1"

const val TMDB_API = "https://api.themoviedb.org/3"

data class FtpServer(val url: String, val root: String)

val DHAKAFLIX_SERVERS = listOf(
    FtpServer("http://172.16.50.12", "/DHAKA-FLIX-12/"),
    FtpServer("http://172.16.50.14", "/DHAKA-FLIX-14/"),
    FtpServer("http://172.16.50.10", "/DHAKA-FLIX-10/"),
    FtpServer("http://172.16.50.7", "/DHAKA-FLIX-7/")
)

// Maintained for CineStreamBDIX.kt startup initialization
suspend fun init() {
    // Dynamic endpoints initialization if needed
}
