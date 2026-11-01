package com.aetheron.backend

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class ApplicationTest {
    @Test
    fun healthEndpointReturnsServiceStatus() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(200, response.status.value)
        assertContains(response.bodyAsText(), "\"service\":\"aetheron-api\"")
    }
}
