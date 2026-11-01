package com.aetheron.browser

import java.net.URLEncoder
import java.util.Locale

internal object BrowserAddress {
    fun resolve(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return "https://www.google.com"
        val lower = input.lowercase(Locale.ROOT)
        if (lower.startsWith("https://") || lower.startsWith("http://")) return input
        val looksLikeHost = !input.contains(' ') &&
            (input.equals("localhost", true) || input.startsWith("localhost:") ||
                input.contains('.') || input.startsWith("127.0.0.1"))
        return if (looksLikeHost) "https://$input"
        else "https://www.google.com/search?q=" + URLEncoder.encode(input, Charsets.UTF_8.name())
    }

    fun isWebUrl(url: String): Boolean =
        url.startsWith("https://", true) || url.startsWith("http://", true)
}
