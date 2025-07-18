// Test the season parsing logic
fun main() {
    // Test cases
    val testCases = listOf(
        "Season 1",
        "Season 2",
        "Season 10",
        "S1",
        "S2",
        "S10",
        "Series 1",
        "Series 2",
        "OVA",
        "Specials",
        "Movies",
        "Season1",
        "season 1",
        "SEASON 1",
        "Extras",
        "Season 1 - The Beginning",
        "S01",
        "S02",
        "S10"
    )
    
    for (testCase in testCases) {
        val result = parseSeasonInfo(testCase)
        println("'$testCase' -> Season: ${result.first}, Name: ${result.second}")
    }
}

fun parseSeasonInfo(folderName: String): Pair<Int?, String?> {
    // Try to match common season patterns
    val seasonPatterns = listOf(
        Regex("(?i)season\\s*(\\d+)", RegexOption.IGNORE_CASE),
        Regex("(?i)s(\\d+)", RegexOption.IGNORE_CASE),
        Regex("(?i)series\\s*(\\d+)", RegexOption.IGNORE_CASE)
    )
    
    for (pattern in seasonPatterns) {
        val match = pattern.find(folderName)
        if (match != null) {
            val seasonNum = match.groupValues[1].toIntOrNull()
            if (seasonNum != null) {
                return Pair(seasonNum, null) // Return season number, no custom name
            }
        }
    }
    
    // If no season number found, use folder name as custom season name
    // Clean up the folder name for display
    val cleanName = folderName.replace(Regex("[%_]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    
    return Pair(null, cleanName) // Return null for season number, custom name
}
