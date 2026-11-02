package com.aetheron.browser

import java.net.URLEncoder
import java.util.Locale

internal object BrowserAddress {
    fun resolve(rawInput: String, searchEngine: String = "Google"): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return "https://www.google.com"
        val lower = input.lowercase(Locale.ROOT)
        if (lower.startsWith("https://") || lower.startsWith("http://")) return input
        val looksLikeHost = !input.contains(' ') &&
            (input.equals("localhost", true) || input.startsWith("localhost:") ||
                input.contains('.') || input.startsWith("127.0.0.1"))
        if (looksLikeHost) return "https://$input"
        val query = URLEncoder.encode(input, Charsets.UTF_8.name())
        return when (searchEngine.lowercase(Locale.ROOT)) {
            "bing" -> "https://www.bing.com/search?q=$query"
            "duckduckgo" -> "https://duckduckgo.com/?q=$query"
            else -> "https://www.google.com/search?q=$query"
        }
    }

    fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", true) || url.startsWith("http://", true)
}
