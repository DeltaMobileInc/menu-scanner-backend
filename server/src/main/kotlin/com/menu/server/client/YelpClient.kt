package com.menu.server.client

import com.menu.server.models.Restaurant
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

// ── Yelp API response shapes ──────────────────────────────────────────────────

@Serializable
data class YelpBusiness(
    val id: String,
    val name: String,
    val coordinates: YelpCoordinates? = null,
    val rating: Float = 0f,
    val review_count: Int = 0,
    val image_url: String = "",
    val categories: List<YelpCategory> = emptyList(),
)

@Serializable
data class YelpCoordinates(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
)

@Serializable
data class YelpCategory(
    val alias: String = "",
    val title: String = "",
)

@Serializable
data class YelpResponse(
    val businesses: List<YelpBusiness> = emptyList(),
)

// ── Client ────────────────────────────────────────────────────────────────────

/**
 * Wraps the Yelp Fusion Businesses Search endpoint.
 *
 * The [httpClient] must have ContentNegotiation with JSON installed so that
 * the response body is automatically deserialised into [YelpResponse].
 */
class YelpClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
) {
    private val logger = LoggerFactory.getLogger("YelpClient")

    /**
     * Searches Yelp for businesses matching [query].
     * Returns an empty list and logs a warning if the API key is absent or the
     * request fails for any reason.
     */
    suspend fun searchBusinesses(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): List<Restaurant> {
        if (apiKey.isBlank() || apiKey.startsWith("your_")) {
            logger.warn("Yelp API key not configured – skipping Yelp search")
            return emptyList()
        }

        return try {
            logger.info("Yelp → searching: '$query'")

            val response: YelpResponse =
                httpClient.get("https://api.yelp.com/v3/businesses/search") {
                    headers { append("Authorization", "Bearer $apiKey") }
                    parameter("term", query)
                    parameter("limit", 20)
                    if (latitude  != null) parameter("latitude",  latitude)
                    if (longitude != null) parameter("longitude", longitude)
                }.body()

            logger.info("Yelp → ${response.businesses.size} results for '$query'")

            response.businesses.map { biz ->
                Restaurant(
                    id          = "yelp_${biz.id}",
                    name        = biz.name,
                    latitude    = biz.coordinates?.latitude  ?: 0.0,
                    longitude   = biz.coordinates?.longitude ?: 0.0,
                    cuisines    = biz.categories.map { it.title }.filter { it.isNotBlank() },
                    rating      = biz.rating,
                    reviewCount = biz.review_count,
                    imageUrl    = biz.image_url,
                    yelpId      = biz.id,
                )
            }
        } catch (e: Exception) {
            logger.error("Yelp API call failed for '$query': ${e.message}", e)
            emptyList()
        }
    }
}
