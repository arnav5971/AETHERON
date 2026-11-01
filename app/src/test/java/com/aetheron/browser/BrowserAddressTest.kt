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
}
