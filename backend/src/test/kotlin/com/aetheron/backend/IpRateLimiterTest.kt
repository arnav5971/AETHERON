package com.aetheron.backend

import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpRateLimiterTest {
    @Test
    fun permitsOnlyConfiguredRequestsInWindow() {
        var now = 1_000L
        val limiter = IpRateLimiter(maxRequests = 2, windowMillis = 60_000, clock = { now })
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
        assertTrue(limiter.allow("c"))
        assertTrue(limiter.trackedKeyCount() <= 2)
    }

    @Test
    fun concurrentNewKeysNeverExceedConfiguredCapacity() {
        val limiter = IpRateLimiter(maxRequests = 1, maxKeys = 12)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val tasks = (0 until 500).map { index ->
                executor.submit<Boolean> { limiter.allow("client-$index") }
            }
            tasks.forEach { it.get() }
            assertTrue(limiter.trackedKeyCount() <= 12)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun blankKeysAreRejected() {
        val limiter = IpRateLimiter()
        assertFalse(limiter.allow(" "))
    }
}
