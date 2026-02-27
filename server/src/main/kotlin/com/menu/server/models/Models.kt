// FILE 5: server/src/main/kotlin/com/menu/server/models/Models.kt
// All request/response DTOs used across the API.
// Every class is @Serializable for automatic JSON conversion via kotlinx.serialization.

package com.menu.server.models

import kotlinx.serialization.Serializable

// ── Utility responses ─────────────────────────────────────────────────────────

/**
 * Standard health-check response returned by GET /health.
 *
 * @property status    Always "ok" when the server is healthy.
 * @property timestamp Unix epoch milliseconds at the time of the check.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
)

/**
 * Generic error envelope returned for 4xx / 5xx responses.
 *
 * @property error Human-readable description of what went wrong.
 */
@Serializable
data class ErrorResponse(
    val error: String,
)

/**
 * Wraps a list of [Restaurant] objects for search results.
 *
 * @property results Matching restaurants ordered by relevance.
 */
@Serializable
data class SearchResponse(
    val results: List<Restaurant>,
)

/**
 * Wraps a list of [ImageResult] objects for a given restaurant.
 *
 * @property restaurantId The restaurant whose images are returned.
 * @property images       Ordered list of photos (most relevant first).
 * @property total        Total number of images in [images].
 */
@Serializable
data class ImagesResponse(
    val restaurantId: String,
    val images: List<ImageResult>,
    val total: Int,
)

// ── Request DTOs ──────────────────────────────────────────────────────────────

/**
 * Payload sent by the mobile client when submitting a menu scan.
 *
 * The device performs on-device ML first, packages the results here, and sends
 * them to the backend for restaurant matching and image enrichment.
 *
 * @property imagePath             Local file path of the captured image (optional upload).
 * @property imageBase64           Base64-encoded image for inline upload (optional).
 * @property restaurantName        Name extracted on-device (null if undetected).
 * @property restaurantConfidence  On-device name extraction confidence [0.0–1.0].
 * @property menuName              Menu type label extracted on-device (e.g. "Wine Menu").
 * @property extractedText         Raw OCR output from the device vision framework.
 * @property sections              Structured menu sections already parsed on-device.
 * @property imageQualityScore     Image quality score computed on-device [0.0–1.0].
 * @property processedOnDevice     Always true for mobile submissions.
 * @property deviceProcessingTime  Milliseconds taken by on-device ML inference.
 * @property latitude              GPS latitude at scan time (null if no permission).
 * @property longitude             GPS longitude at scan time (null if no permission).
 */
@Serializable
data class ScanRequest(
    val imagePath: String? = null,
    val imageBase64: String? = null,
    val restaurantName: String? = null,
    val restaurantConfidence: Float = 0f,
    val menuName: String? = null,
    val extractedText: String = "",
    val sections: List<MenuSection> = emptyList(),
    val imageQualityScore: Float = 0f,
    val processedOnDevice: Boolean = false,
    val deviceProcessingTime: Long = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * Credentials for authenticating an existing user account.
 *
 * @property email    Registered email address.
 * @property password Account password (plaintext; always sent over TLS).
 */
@Serializable
data class UserLoginRequest(
    val email: String,
    val password: String,
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

/**
 * Full result returned after the backend processes a [ScanRequest].
 *
 * @property scanId         Server-assigned unique identifier for this scan.
 * @property restaurant     Matched or inferred restaurant entity.
 * @property menuName       Verified menu type label.
 * @property sections       Parsed menu sections (verbatim or server-enriched).
 * @property images         Restaurant photos fetched from external sources.
 * @property processingTime Timing breakdown for analytics.
 * @property qualityScore   Overall scan quality score [0.0–1.0].
 */
@Serializable
data class ScanResponse(
    val scanId: String,
    val restaurant: Restaurant,
    val menuName: String,
    val sections: List<MenuSection>,
    val images: List<ImageResult> = emptyList(),
    val processingTime: ProcessingTime,
    val qualityScore: Float,
)

/**
 * A restaurant entity returned in API responses.
 *
 * @property id          Internal unique identifier.
 * @property name        Restaurant name.
 * @property latitude    WGS-84 latitude.
 * @property longitude   WGS-84 longitude.
 * @property cuisines    Cuisine types (e.g. ["Italian", "Pizza"]).
 * @property rating      Average rating 0.0–5.0.
 * @property reviewCount Total number of reviews.
 * @property imageUrl    URL of the primary photo.
 */
@Serializable
data class Restaurant(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val cuisines: List<String> = emptyList(),
    val rating: Float = 0f,
    val reviewCount: Int = 0,
    val imageUrl: String = "",
    /** Yelp business ID – populated when the record originated from Yelp. */
    val yelpId: String? = null,
    /** Google Places place_id – populated when the record originated from Google. */
    val googlePlaceId: String? = null,
)

/**
 * A named section of a restaurant menu (e.g. "Appetizers").
 *
 * @property name  Section heading as it appears on the menu.
 * @property items Ordered list of dishes in this section.
 */
@Serializable
data class MenuSection(
    val name: String,
    val items: List<MenuItem>,
)

/**
 * A single dish or beverage entry on a menu.
 *
 * @property name        Dish name.
 * @property price       Price in local currency (null = market price or unknown).
 * @property description Optional description printed below the item name.
 */
@Serializable
data class MenuItem(
    val name: String,
    val price: Float? = null,
    val description: String = "",
)

/**
 * A restaurant photo sourced from an external platform.
 *
 * @property url            Full-resolution image URL.
 * @property source         Platform that provided the photo ("yelp", "google_maps", "instagram").
 * @property uploadedBy     Username or attribution of the original uploader.
 * @property uploadedAt     Unix epoch milliseconds when the photo was published.
 * @property relevanceScore Relevance to the restaurant [0.0–1.0].
 * @property thumbnailUrl   Smaller preview URL (empty string when unavailable).
 */
@Serializable
data class ImageResult(
    val url: String,
    val source: String,
    val uploadedBy: String,
    val uploadedAt: Long,
    val relevanceScore: Float,
    val thumbnailUrl: String = "",
)

/**
 * Timing breakdown for a scan pipeline execution.
 *
 * @property deviceMs  Milliseconds spent on-device (ML inference + parsing).
 * @property serverMs  Milliseconds spent on the server (matching + image fetch).
 * @property totalMs   Total end-to-end elapsed time.
 */
@Serializable
data class ProcessingTime(
    val deviceMs: Long = 0,
    val serverMs: Long,
    val totalMs: Long,
)

/**
 * Authentication result returned after a successful login or registration.
 *
 * @property userId    Unique identifier of the authenticated user.
 * @property email     Email address of the account.
 * @property token     Signed JWT bearer token for subsequent API calls.
 * @property expiresAt Unix epoch milliseconds when [token] expires.
 */
@Serializable
data class UserLoginResponse(
    val userId: String,
    val email: String,
    val token: String,
    val expiresAt: Long,
)
