package com.menu.server

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
 * Tests for authentication endpoints.
 *
 * Covers:
 * - POST /api/v1/auth/login    — authenticate user, receive JWT
 * - POST /api/v1/auth/register — create new account, receive JWT
 */
class AuthRoutesTest {

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() =
        createClient {
            install(ContentNegotiation) { json() }
        }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────

    @Test
    fun `POST auth login returns 200 OK`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"secret123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST auth login response contains userId`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"secret123"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["userId"], "Response must contain userId")
        assertTrue(body["userId"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `POST auth login response echoes email`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"alice@example.com","password":"pass123"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("alice@example.com", body["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST auth login response contains token`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"secret123"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = body["token"]?.jsonPrimitive?.content
        assertNotNull(token, "Response must contain a token")
        assertTrue(token.isNotBlank(), "Token must not be blank")
    }

    @Test
    fun `POST auth login response contains future expiresAt`() = testApplication {

        
        val client = jsonClient()

        val beforeMs = System.currentTimeMillis()
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"secret123"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val expiresAt = body["expiresAt"]?.jsonPrimitive?.long ?: 0L
        assertTrue(expiresAt > beforeMs, "expiresAt must be a future timestamp")
    }

    @Test
    fun `POST auth login token expires approximately 1 hour from now`() = testApplication {

        
        val client = jsonClient()

        val beforeMs = System.currentTimeMillis()
        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"secret123"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val expiresAt = body["expiresAt"]?.jsonPrimitive?.long ?: 0L
        val oneHourMs = 3_600_000L
        val toleranceMs = 5_000L  // 5s window for test execution

        assertTrue(
            expiresAt in (beforeMs + oneHourMs - toleranceMs)..(beforeMs + oneHourMs + toleranceMs),
            "Token should expire approximately 1 hour from now"
        )
    }

    @Test
    fun `POST auth login missing password returns 500`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com"}""")
        }

        // Missing required field causes deserialization error -> 500
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `POST auth login missing email returns 500`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"password":"secret123"}""")
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    // ── POST /api/v1/auth/register ────────────────────────────────────────

    @Test
    fun `POST auth register returns 201 Created`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"newuser@example.com","password":"newpass456"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `POST auth register response contains userId`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"newuser@example.com","password":"newpass456"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val userId = body["userId"]?.jsonPrimitive?.content
        assertNotNull(userId)
        assertTrue(userId.startsWith("user_"), "userId should start with 'user_', got: $userId")
    }

    @Test
    fun `POST auth register response echoes email`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"bob@example.com","password":"mypassword"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("bob@example.com", body["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST auth register response contains token`() = testApplication {

        
        val client = jsonClient()

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"newuser@example.com","password":"newpass456"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = body["token"]?.jsonPrimitive?.content
        assertNotNull(token)
        assertTrue(token.isNotBlank(), "Token must not be blank")
    }

    @Test
    fun `POST auth register generates unique userId each time`() = testApplication {

        
        val client = jsonClient()

        val payload = """{"email":"test@example.com","password":"pass"}"""

        val response1 = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        val response2 = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        val userId1 = Json.parseToJsonElement(response1.bodyAsText()).jsonObject["userId"]?.jsonPrimitive?.content
        val userId2 = Json.parseToJsonElement(response2.bodyAsText()).jsonObject["userId"]?.jsonPrimitive?.content

        assertTrue(userId1 != userId2, "Each registration should produce a unique userId")
    }

    @Test
    fun `POST auth register expiresAt is in the future`() = testApplication {

        
        val client = jsonClient()

        val beforeMs = System.currentTimeMillis()
        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"future@example.com","password":"pass"}""")
        }

        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val expiresAt = body["expiresAt"]?.jsonPrimitive?.long ?: 0L
        assertTrue(expiresAt > beforeMs, "expiresAt must be a future timestamp")
    }
}
