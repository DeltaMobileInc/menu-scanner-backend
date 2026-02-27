package com.menu.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for restaurant-related API endpoints.
 *
 * Covers:
 * - GET /api/v1/restaurants/search?q=<query>  — keyword search
 * - GET /api/v1/restaurants/{id}              — fetch by ID
 * - GET /api/v1/restaurants/{id}/images       — fetch photos
 */
class RestaurantRoutesTest {

    // ── GET /api/v1/restaurants/search ────────────────────────────────────

    @Test
    fun `GET restaurants search returns 200 OK`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=pizza")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET restaurants search returns results array`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=pizza")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val results = body["results"]?.jsonArray
        assertNotNull(results, "Response should contain a 'results' array")
    }

    @Test
    fun `GET restaurants search with pizza query filters correctly`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=pizza")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: emptyList()

        assertTrue(results.isNotEmpty(), "Search for 'pizza' should return at least one result")
        assertTrue(results.all { item ->
            val name     = item.jsonObject["name"]?.jsonPrimitive?.content ?: ""
            val cuisines = item.jsonObject["cuisines"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()
            name.contains("pizza", ignoreCase = true) ||
                cuisines.any { it.contains("pizza", ignoreCase = true) }
        }, "All results should match the 'pizza' query in name or cuisine")
    }

    @Test
    fun `GET restaurants search result items contain required fields`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=burger")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: emptyList()

        assertTrue(results.isNotEmpty())
        results.forEach { item ->
            val obj = item.jsonObject
            assertNotNull(obj["id"],        "Restaurant must have an id")
            assertNotNull(obj["name"],      "Restaurant must have a name")
            assertNotNull(obj["latitude"],  "Restaurant must have a latitude")
            assertNotNull(obj["longitude"], "Restaurant must have a longitude")
            assertNotNull(obj["rating"],    "Restaurant must have a rating")
        }
    }

    @Test
    fun `GET restaurants search result rating is between 0 and 5`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=pizza")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray ?: emptyList()

        results.forEach { item ->
            val rating = item.jsonObject["rating"]?.jsonPrimitive?.float ?: -1f
            assertTrue(rating in 0f..5f, "Rating must be between 0.0 and 5.0, got $rating")
        }
    }

    @Test
    fun `GET restaurants search without query param returns 400`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET restaurants search 400 response contains error field`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertNotNull(body["error"], "400 response should contain an 'error' field")
    }

    @Test
    fun `GET restaurants search with unmatched query returns fallback results`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/search?q=zzznomatch")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val results = body["results"]?.jsonArray

        // Mock implementation falls back to all results when nothing matches
        assertNotNull(results)
        assertTrue(results.isNotEmpty(), "Should return fallback results when nothing matches")
    }

    // ── GET /api/v1/restaurants/{id} ──────────────────────────────────────

    @Test
    fun `GET restaurant by id returns 200 OK`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET restaurant by id returns correct id`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_42")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals("rest_42", body["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET restaurant by id response contains required fields`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertNotNull(body["id"],          "Must have id")
        assertNotNull(body["name"],        "Must have name")
        assertNotNull(body["latitude"],    "Must have latitude")
        assertNotNull(body["longitude"],   "Must have longitude")
        assertNotNull(body["rating"],      "Must have rating")
        assertNotNull(body["reviewCount"], "Must have reviewCount")
        assertNotNull(body["cuisines"],    "Must have cuisines")
    }

    @Test
    fun `GET restaurant by id rating is valid`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val rating = body["rating"]?.jsonPrimitive?.float ?: -1f

        assertTrue(rating in 0f..5f, "Rating must be between 0.0 and 5.0")
    }

    @Test
    fun `GET restaurant by id reviewCount is non-negative`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val reviewCount = body["reviewCount"]?.jsonPrimitive?.int ?: -1

        assertTrue(reviewCount >= 0, "reviewCount must be non-negative")
    }

    // ── GET /api/v1/restaurants/{id}/images ───────────────────────────────

    @Test
    fun `GET restaurant images returns 200 OK`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET restaurant images response contains restaurantId`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_99/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals("rest_99", body["restaurantId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GET restaurant images response contains images array`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val images = body["images"]?.jsonArray
        assertNotNull(images)
        assertTrue(images.isNotEmpty(), "Images array should not be empty")
    }

    @Test
    fun `GET restaurant images total matches images array size`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        val images = body["images"]?.jsonArray ?: emptyList()
        val total  = body["total"]?.jsonPrimitive?.int ?: -1
        assertEquals(images.size, total, "total field should match images array length")
    }

    @Test
    fun `GET restaurant images each item has required fields`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val images = body["images"]?.jsonArray ?: emptyList()

        images.forEach { image ->
            val obj = image.jsonObject
            assertNotNull(obj["url"],            "Image must have url")
            assertNotNull(obj["source"],         "Image must have source")
            assertNotNull(obj["uploadedBy"],     "Image must have uploadedBy")
            assertNotNull(obj["uploadedAt"],     "Image must have uploadedAt")
            assertNotNull(obj["relevanceScore"], "Image must have relevanceScore")
        }
    }

    @Test
    fun `GET restaurant images relevanceScore is between 0 and 1`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val images = body["images"]?.jsonArray ?: emptyList()

        images.forEach { image ->
            val score = image.jsonObject["relevanceScore"]?.jsonPrimitive?.float ?: -1f
            assertTrue(score in 0f..1f, "relevanceScore must be between 0.0 and 1.0, got $score")
        }
    }

    @Test
    fun `GET restaurant images includes multiple sources`() = testApplication {

        

        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val sources = body["images"]?.jsonArray
            ?.map { it.jsonObject["source"]?.jsonPrimitive?.content }
            ?.toSet() ?: emptySet()

        assertTrue(sources.size > 1, "Should include images from more than one source")
    }

    @Test
    fun `GET restaurant images source values are valid`() = testApplication {

        

        val validSources = setOf("yelp", "google_maps", "instagram")
        val response = client.get("/api/v1/restaurants/rest_1/images")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val images = body["images"]?.jsonArray ?: emptyList()

        images.forEach { image ->
            val source = image.jsonObject["source"]?.jsonPrimitive?.content
            assertTrue(source in validSources, "Source '$source' is not a valid image source")
        }
    }
}
