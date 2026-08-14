// use an integer for version numbers
version = 1

android {
    namespace = "com.niloy"
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
}

cloudstream {
    description = "CTGMovies Provider - Stream Movies, TV Shows, and Anime from ctgmovies.com"
    authors = listOf("Niloy")

    status = 1 // 1: Ok

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Anime"
    )
    language = "en"
}
