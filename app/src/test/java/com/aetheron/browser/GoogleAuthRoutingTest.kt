package com.aetheron.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthRoutingTest {
    @Test
    fun googleSearchAndAiModeUseTrustedBrowser() {
        assertTrue(shouldOpenInTrustedBrowser("https://www.google.com/"))
        assertTrue(shouldOpenInTrustedBrowser("https://www.google.com/ai"))
        assertTrue(shouldOpenInTrustedBrowser("https://google.com/search?q=aetheron"))
    }

    @Test
    fun googleAccountAndConsentUseTrustedBrowser() {
        assertTrue(shouldOpenInTrustedBrowser("https://accounts.google.com/signin"))
        assertTrue(shouldOpenInTrustedBrowser("https://consent.google.com/"))
        assertTrue(shouldOpenInTrustedBrowser("https://myaccount.google.com/"))
    }

    @Test
    fun unrelatedSitesStayInsideAetheronWebView() {
        assertFalse(shouldOpenInTrustedBrowser("https://example.com/"))
        assertFalse(shouldOpenInTrustedBrowser("https://youtube.com/"))
        assertFalse(shouldOpenInTrustedBrowser("https://notgoogle.com/"))
        assertFalse(shouldOpenInTrustedBrowser("http://www.google.com/"))
    }

    @Test
    fun trustedGoogleHostsAllowOnlyDefaultHttpsPort() {
        assertTrue(shouldOpenInTrustedBrowser("https://accounts.google.com/signin"))
        assertTrue(shouldOpenInTrustedBrowser("https://accounts.google.com:443/signin"))
        assertFalse(shouldOpenInTrustedBrowser("https://accounts.google.com:8443/signin"))
    }

    @Test
    fun deceptiveGoogleSubdomainsAreNotTrusted() {
        assertFalse(shouldOpenInTrustedBrowser("https://google.com.evil.example/"))
        assertFalse(shouldOpenInTrustedBrowser("https://accounts.google.com.evil.example/"))
        assertFalse(shouldOpenInTrustedBrowser("https://google.com@evil.example/"))
        assertFalse(shouldOpenInTrustedBrowser("https://google.com.evil/"))
    }

    @Test
    fun trustedHostMatchingIsCaseInsensitiveAndRejectsMalformedUrls() {
        assertTrue(shouldOpenInTrustedBrowser("HTTPS://ACCOUNTS.GOOGLE.COM/signin"))
        assertFalse(shouldOpenInTrustedBrowser("https://accounts.google.com:invalid/signin"))
        assertFalse(shouldOpenInTrustedBrowser("javascript://accounts.google.com/"))
        assertFalse(shouldOpenInTrustedBrowser("https:///accounts.google.com/"))
    }
}
