package com.menu.server.client

import com.menu.server.models.Restaurant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

// ── Google Places API response shapes ────────────────────────────────────────

@Serializable
data class GooglePlace(
    val place_id: String,
    val name: String,
    val geometry: GoogleGeometry? = null,
    val rating: Float? = null,
    val user_ratings_total: Int = 0,
    val photos: List<GooglePhoto> = emptyList(),
    val types: List<String> = emptyList(),
)

@Serializable
data class GoogleGeometry(
    val location: GoogleLocation,
)

@Serializable
data class GoogleLocation(
    val lat: Double,
    val lng: Double,
)

@Serializable
data class GooglePhoto(
    val photo_reference: String,
    val height: Int = 0,
    val width: Int = 0,
)

@Serializable
data class GooglePlacesResponse(
    val results: List<GooglePlace> = emptyList(),
    val status: String = "",
)

// ── Client ────────────────────────────────────────────────────────────────────

/** Types excluded when building the cuisines list from Google Places `types`. */
private val GENERIC_PLACE_TYPES = setOf(
    "establishment", "point_of_interest", "food", "restaurant",
    "meal_takeaway", "meal_delivery",
)

/**
 * Wraps the Google Places Text Search endpoint.
 *
 * The [httpClient] must have ContentNegotiation with JSON installed.
 */
class GoogleMapsClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
) {
    private val logger = LoggerFactory.getLogger("GoogleMapsClient")

    /**
     * Searches Google Places for restaurants matching [query].
     * Returns an empty list and logs a warning if the API key is absent or the
     * request fails for any reason.
     */
    suspend fun searchRestaurants(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): List<Restaurant> {
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            logger.warn("Google Places API key not configured – skipping Google search")
            return emptyList()
        }

        return try {
            logger.info("Google Places → searching: '$query'")

            val response: GooglePlacesResponse =
                httpClient.get("https://maps.googleapis.com/maps/api/place/textsearch/json") {
                    parameter("query", "$query restaurant")
                    parameter("type", "restaurant")
                    parameter("key", apiKey)
                    if (latitude != null && longitude != null) {
                        parameter("location", "$latitude,$longitude")
                        parameter("radius", 50_000)
                    }
                }.body()

            logger.info(
                "Google Places → ${response.results.size} results for '$query' " +
                    "(status: ${response.status})",
            )

            response.results.map { place ->
                val photoRef = place.photos.firstOrNull()?.photo_reference
                val imageUrl = if (photoRef != null) {
                    "https://maps.googleapis.com/maps/api/place/photo" +
                        "?maxwidth=800&photo_reference=$photoRef&key=$apiKey"
                } else ""

                val cuisines = place.types
                    .filter { it !in GENERIC_PLACE_TYPES }
                    .map { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }

                Restaurant(
                    id            = "google_${place.place_id}",
                    name          = place.name,
                    latitude      = place.geometry?.location?.lat ?: 0.0,
                    longitude     = place.geometry?.location?.lng ?: 0.0,
                    cuisines      = cuisines,
                    rating        = place.rating ?: 0f,
                    reviewCount   = place.user_ratings_total,
                    imageUrl      = imageUrl,
                    googlePlaceId = place.place_id,
                )
            }
        } catch (e: Exception) {
            logger.error("Google Places API call failed for '$query': ${e.message}", e)
            emptyList()
        }
    }
}
