package nl.davidengelen.redsquare

import java.io.File
import nl.davidengelen.redsquare.saito.SaitoStack

/** Read-only native decoder observation over explicitly supplied original public Archive responses. */
object HostStackReader {
    @JvmStatic fun main(arguments: Array<String>) {
        require(arguments.size == 2)
        for (path in arguments) {
            val page = SaitoStack.readArchive(File(path).readText())
            check(page.rowCount == 0 || page.articles.isNotEmpty())
            println("${File(path).name}: ${page.rowCount} rows, ${page.articles.size} authenticated articles, ${page.ignored} ignored")
            page.articles.forEach { println("${it.title}: ${it.author}, cover=${it.cover != null}") }
        }
    }
}
