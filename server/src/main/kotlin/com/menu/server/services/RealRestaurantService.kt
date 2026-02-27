package com.menu.server.services

import com.menu.server.client.GoogleMapsClient
import com.menu.server.client.YelpClient
import com.menu.server.models.Restaurant
import com.menu.server.repository.RestaurantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Orchestrates restaurant lookups by combining a local PostgreSQL cache with
 * live data from the Yelp and Google Places APIs.
 *
 * Cache-aside strategy:
 *  1. Check the local database first.
 *  2. On a cache miss, call Yelp and Google **in parallel**.
 *  3. Deduplicate, sort by rating, persist to the database, then return.
 *
 * All blocking JDBC work is executed on [Dispatchers.IO] so the Ktor
 * coroutine dispatcher is never blocked.
 */
class RealRestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val yelpClient: YelpClient,
    private val googleMapsClient: GoogleMapsClient,
) {
    private val logger = LoggerFactory.getLogger("RealRestaurantService")

    /**
     * Searches for restaurants matching [query], optionally biased towards
     * the given [latitude] / [longitude].
     */
    suspend fun searchRestaurants(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): List<Restaurant> {
        logger.info("Searching restaurants: '$query'")

        // 1. Local cache lookup (blocking JDBC → Dispatchers.IO)
        val dbResults = withContext(Dispatchers.IO) {
            restaurantRepository.search(query, limit = 50)
        }
        if (dbResults.isNotEmpty()) {
            logger.info("Cache hit – ${dbResults.size} restaurants from local database")
            return dbResults
        }

        // 2. Cache miss – call Yelp and Google in parallel
        logger.info("Cache miss – calling Yelp and Google Places in parallel…")
        val (yelpResults, googleResults) = coroutineScope {
            val yelp   = async { yelpClient.searchBusinesses(query, latitude, longitude) }
            val google = async { googleMapsClient.searchRestaurants(query, latitude, longitude) }
            Pair(yelp.await(), google.await())
        }

        // 3. Merge, deduplicate by lowercased name, sort by rating
        val combined = (yelpResults + googleResults)
            .distinctBy { it.name.lowercase().trim() }
            .sortedByDescending { it.rating }

        logger.info(
            "External APIs returned ${combined.size} unique restaurants " +
                "(Yelp: ${yelpResults.size}, Google: ${googleResults.size})",
        )

        // 4. Persist results (blocking JDBC → Dispatchers.IO)
        withContext(Dispatchers.IO) {
            combined.forEach { restaurantRepository.save(it) }
        }

        return combined
    }

    /** Looks up a single restaurant by its database ID. */
    suspend fun getRestaurant(id: String): Restaurant? =
        withContext(Dispatchers.IO) {
            restaurantRepository.findById(id)
        }.also { result ->
            if (result != null) {
                logger.info("Found restaurant: '${result.name}' ($id)")
            } else {
                logger.warn("Restaurant not found: $id")
            }
        }

    /** Returns the top [limit] restaurants ordered by rating. */
    suspend fun getTrendingRestaurants(limit: Int = 20): List<Restaurant> {
        val results = withContext(Dispatchers.IO) {
            restaurantRepository.getTrending(limit)
        }
        logger.info("Retrieved ${results.size} trending restaurants")
        return results
    }
}
