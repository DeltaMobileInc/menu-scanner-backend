package com.menu.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the application-level configuration and health check endpoint.
 *
 * Verifies that:
 * - The server starts and responds correctly
 * - The /health endpoint returns the expected shape
 * - Default response headers from DefaultHeaders plugin are present
 * - Requests to unknown routes return 404
 */
class ApplicationTest {

    // ── Health check ──────────────────────────────────────────────────────

    @Test
    fun `GET health returns 200 OK`() = testApplication {

        

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET health response body contains status ok`() = testApplication {

        

        val response = client.get("/health")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET health response body contains timestamp`() = testApplication {

        

        val response = client.get("/health")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val timestamp = body["timestamp"]?.jsonPrimitive?.long
        assertNotNull(timestamp)
        assertTrue(timestamp > 0, "Timestamp must be a positive Unix epoch value")
    }

    @Test
    fun `GET health timestamp is recent`() = testApplication {

        

        val beforeMs = System.currentTimeMillis()
        val response = client.get("/health")
        val afterMs = System.currentTimeMillis()

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val timestamp = body["timestamp"]?.jsonPrimitive?.long ?: 0L

        assertTrue(timestamp in beforeMs..afterMs, "Timestamp should be within the test execution window")
    }

    // ── Default headers ───────────────────────────────────────────────────

    @Test
    fun `response includes X-Engine header`() = testApplication {

        

        val response = client.get("/health")

        assertEquals("Ktor", response.headers["X-Engine"])
    }

    @Test
    fun `response includes X-Version header`() = testApplication {

        

        val response = client.get("/health")

        assertEquals("1.0.0", response.headers["X-Version"])
    }

    // ── 404 for unknown routes ─────────────────────────────────────────────

    @Test
    fun `GET unknown route returns 404`() = testApplication {

        

        val response = client.get("/non-existent-path")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET unknown api route returns 404`() = testApplication {

        

        val response = client.get("/api/v1/unknown")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
