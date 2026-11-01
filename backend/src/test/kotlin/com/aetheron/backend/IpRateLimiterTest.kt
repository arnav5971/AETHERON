package com.aetheron.backend

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpRateLimiterTest {
    @Test
    fun permitsOnlyConfiguredRequestsInWindow() {
        var now = 1_000L
        val limiter = IpRateLimiter(maxRequests = 2, windowMillis = 60_000) { now }
        assertTrue(limiter.allow("device-a"))
        assertTrue(limiter.allow("device-a"))
        assertFalse(limiter.allow("device-a"))
        assertTrue(limiter.allow("device-b"))
        now += 60_001
        assertTrue(limiter.allow("device-a"))
    }
    @Test
    fun expiredEntriesArePrunedAndKeyStorageIsBounded() {
        var now = 1_000L
        val limiter = IpRateLimiter(maxRequests = 2, windowMillis = 100, clock = { now }, maxKeys = 2)
        assertTrue(limiter.allow("a"))
        assertTrue(limiter.allow("b"))
        assertFalse(limiter.allow("c"))
        now += 101
        repeat(128) { limiter.allow("a") }
        assertTrue(limiter.trackedKeyCount() <= 2)
        assertTrue(limiter.allow("c"))
    }

    @Test
    fun blankKeysAreRejected() {
        val limiter = IpRateLimiter()
        assertFalse(limiter.allow(" "))
    }

}
