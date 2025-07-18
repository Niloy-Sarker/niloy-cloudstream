import com.niloy.FTPBDProvider
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val provider = FTPBDProvider()
        
        println("Testing FTPBD search functionality...")
        
        // Test search with a simple query
        val results = provider.search("test")
        
        println("Search results count: ${results.size}")
        
        results.forEach { result ->
            println("Result: ${result.name} - ${result.url}")
        }
        
        // Test main page
        val mainPage = provider.getMainPage(1, null)
        println("Main page lists: ${mainPage.list.size}")
        
        mainPage.list.forEach { list ->
            println("List: ${list.name} - ${list.list.size} items")
        }
    }
}
