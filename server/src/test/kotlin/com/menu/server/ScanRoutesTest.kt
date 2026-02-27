package com.menu.server

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for scan-related API endpoints.
 *
 * Covers:
 * - POST /api/v1/scans  — submit a menu scan
 * - GET  /api/v1/scans/{scanId} — retrieve a scan by ID
 */
class ScanRoutesTest {

    /** Builds a test client configured with JSON content negotiation. */
    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) { json() }
        }

    // ── POST /api/v1/scans ────────────────────────────────────────────────

    @Test
    fun `POST scans returns 200 OK`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Pizza Hut","imageQualityScore":0.9,"deviceProcessingTime":300}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST scans response contains scanId`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Pizza Hut","imageQualityScore":0.9,"deviceProcessingTime":300}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val scanId = body["scanId"]?.jsonPrimitive?.content
        assertNotNull(scanId)
        assertTrue(scanId.startsWith("scan_"), "scanId should start with 'scan_', got: $scanId")
    }

    @Test
    fun `POST scans uses restaurantName from request`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"The Italian Kitchen","imageQualityScore":0.85,"deviceProcessingTime":250}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val restaurantName = body["restaurant"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        assertEquals("The Italian Kitchen", restaurantName)
    }

    @Test
    fun `POST scans uses menuName from request`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Pizza Hut","menuName":"Wine Menu","imageQualityScore":0.9,"deviceProcessingTime":300}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val menuName = body["menuName"]?.jsonPrimitive?.content
        assertEquals("Wine Menu", menuName)
    }

    @Test
    fun `POST scans defaults restaurantName when not provided`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"imageQualityScore":0.5,"deviceProcessingTime":200}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val restaurantName = body["restaurant"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        assertEquals("Unknown Restaurant", restaurantName)
    }

    @Test
    fun `POST scans echoes deviceProcessingTime in processingTime`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Burger Place","imageQualityScore":0.8,"deviceProcessingTime":450}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val deviceMs = body["processingTime"]?.jsonObject?.get("deviceMs")?.jsonPrimitive?.long
        assertEquals(450L, deviceMs)
    }

    @Test
    fun `POST scans echoes imageQualityScore as qualityScore`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Test","imageQualityScore":0.75,"deviceProcessingTime":100}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val qualityScore = body["qualityScore"]?.jsonPrimitive?.float
        assertEquals(0.75f, qualityScore)
    }

    @Test
    fun `POST scans response includes images list`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Pizza Hut","imageQualityScore":0.9,"deviceProcessingTime":300}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val images = body["images"]?.jsonArray
        assertNotNull(images)
        assertTrue(images.size > 0, "Images list should not be empty")
    }

    @Test
    fun `POST scans response includes latitude and longitude when provided`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("""{"restaurantName":"Sushi Place","latitude":37.7749,"longitude":-122.4194,"imageQualityScore":0.9,"deviceProcessingTime":200}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val lat = body["restaurant"]?.jsonObject?.get("latitude")?.jsonPrimitive?.double
        val lng = body["restaurant"]?.jsonObject?.get("longitude")?.jsonPrimitive?.double
        assertEquals(37.7749, lat)
        assertEquals(-122.4194, lng)
    }

    @Test
    fun `POST scans with empty body returns 200 with defaults`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/scans") {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── GET /api/v1/scans/{scanId} ────────────────────────────────────────

    @Test
    fun `GET scan by id returns 200 OK`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_123456")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET scan by id returns correct scanId`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_abc789")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val scanId = body["scanId"]?.jsonPrimitive?.content
        assertEquals("scan_abc789", scanId)
    }

    @Test
    fun `GET scan by id response includes restaurant`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_111")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["restaurant"], "Response should contain a restaurant object")
    }

    @Test
    fun `GET scan by id response includes sections`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_222")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sections = body["sections"]?.jsonArray
        assertNotNull(sections)
        assertTrue(sections.size >= 2, "Mock scan should have at least 2 sections")
    }

    @Test
    fun `GET scan by id response includes processingTime`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_333")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val processingTime = body["processingTime"]?.jsonObject
        assertNotNull(processingTime)
        assertNotNull(processingTime["deviceMs"])
        assertNotNull(processingTime["serverMs"])
        assertNotNull(processingTime["totalMs"])
    }

    @Test
    fun `GET scan by id response totalMs equals deviceMs plus serverMs`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_timing_test")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val pt = body["processingTime"]?.jsonObject!!
        val deviceMs = pt["deviceMs"]?.jsonPrimitive?.long ?: 0L
        val serverMs = pt["serverMs"]?.jsonPrimitive?.long ?: 0L
        val totalMs  = pt["totalMs"]?.jsonPrimitive?.long  ?: 0L
        assertEquals(deviceMs + serverMs, totalMs)
    }

    @Test
    fun `GET scan by id sections contain items`() = testApplication {

        

        val response = client.get("/api/v1/scans/scan_999")

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sections = body["sections"]?.jsonArray ?: emptyList()
        assertTrue(sections.all { section ->
            section.jsonObject["items"]?.jsonArray?.isNotEmpty() == true
        }, "Each section should have at least one item")
    }
}
