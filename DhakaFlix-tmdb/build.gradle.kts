// use an integer for version numbers
version = 2

android {
    namespace = "com.niloy"
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.14.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "DhakaFlix BDIX Provider with TMDB support for media description"
    authors = listOf("Niloy")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime",
        "AnimeMovie",
        "OVA",
        "Cartoon",
        "AsianDrama",
        "Others",
        "Documentary",
    )
    language = "bn"

    // Removing local network icon URL as it might not be accessible
    // iconUrl = "http://172.16.50.14/images/2.png"
}