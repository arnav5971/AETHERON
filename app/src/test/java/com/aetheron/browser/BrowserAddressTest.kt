package com.aetheron.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAddressTest {
    @Test fun blankInputOpensGoogleHome() {
        assertEquals("https://www.google.com", BrowserAddress.resolve("  "))
    }

    @Test fun explicitHttpsUrlIsPreserved() {
        assertEquals("https://example.com/path", BrowserAddress.resolve("https://example.com/path"))
    }

    @Test fun bareDomainUsesHttps() {
        assertEquals("https://example.com", BrowserAddress.resolve("example.com"))
    }

    @Test fun wordsBecomeGoogleSearch() {
        assertEquals(
            "https://www.google.com/search?q=hello+world",
            BrowserAddress.resolve("hello world")
        )
    }

    @Test fun onlyHttpAndHttpsAreWebNavigationTargets() {
        assertTrue(BrowserAddress.isWebUrl("https://example.com"))
        assertTrue(BrowserAddress.isWebUrl("http://example.com"))
        assertFalse(BrowserAddress.isWebUrl("file:///sdcard/test.html"))
        assertFalse(BrowserAddress.isWebUrl("javascript:alert(1)"))
    }

    @Test fun localhostAndExplicitHttpAreHandled() {
        assertEquals("https://localhost:3000", BrowserAddress.resolve("localhost:3000"))
        assertEquals("http://localhost:8080", BrowserAddress.resolve("http://localhost:8080"))
    }

    @Test fun unsupportedSchemesBecomeSearchQueries() {
        val resolved = BrowserAddress.resolve("javascript:alert(1)")
        assertTrue(resolved.startsWith("https://www.google.com/search?q="))
        assertFalse(resolved.startsWith("javascript:", ignoreCase = true))
    }
}
