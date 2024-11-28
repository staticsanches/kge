package dev.staticsanches.kge.test

import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

/**
 * Creates an [URL] that points to the informed content.
 */
fun createInMemoryURL(
    name: String,
    contentSupplier: () -> InputStream,
): URL = InMemoryURLStreamHandler(name, contentSupplier).url

private class InMemoryURLStreamHandler(
    name: String,
    val contentSupplier: () -> InputStream,
) : URLStreamHandler() {
    val url: URL by lazy(LazyThreadSafetyMode.NONE) { URL("in-memory", null, -1, name, this) }

    override fun openConnection(u: URL?): URLConnection? =
        if (u === url) {
            Connection()
        } else {
            throw IOException("Invalid url: $u")
        }

    private inner class Connection : URLConnection(url) {
        private val inputStream = contentSupplier()

        override fun connect() {}

        override fun getInputStream(): InputStream = inputStream
    }
}
