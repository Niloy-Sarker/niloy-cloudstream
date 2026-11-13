import org.jetbrains.kotlin.konan.properties.Properties

version = 1
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
        buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY")}\"")
    }
}

cloudstream {
    language = "bn"
    description = "CineStream BDIX - Movies, Series & Anime from BDIX streaming sources"
    authors = listOf("niloy")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama",
        "Anime",
        "Torrent"
    )

    iconUrl = "https://github.com/niloy/CineStreamBDIX/raw/refs/heads/master/CineStreamBDIX/icon.png"
    requiresResources = false
}
