package com.menu.server.repository

import com.menu.server.db.RestaurantTable
import com.menu.server.models.Restaurant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

/**
 * Data-access layer for [Restaurant] entities.
 *
 * All methods execute synchronous JDBC calls inside Exposed [transaction] blocks.
 * Callers that live in a coroutine context (e.g. [RealRestaurantService]) are
 * responsible for dispatching these calls onto [kotlinx.coroutines.Dispatchers.IO].
 */
class RestaurantRepository {

    private val logger = LoggerFactory.getLogger("RestaurantRepository")

    /**
     * Inserts [restaurant] if its ID doesn't exist; otherwise updates it.
     * Never throws: failures are logged and swallowed so a bad entry doesn't
     * abort an entire batch save.
     */
    fun save(restaurant: Restaurant) {
        try {
            transaction {
                val exists = RestaurantTable
                    .select { RestaurantTable.id eq restaurant.id }
                    .firstOrNull() != null

                if (!exists) {
                    RestaurantTable.insert {
                        it[id]            = restaurant.id
                        it[name]          = restaurant.name
                        it[latitude]      = restaurant.latitude
                        it[longitude]     = restaurant.longitude
                        it[cuisines]      = restaurant.cuisines.joinToString(",")
                        it[rating]        = restaurant.rating
                        it[reviewCount]   = restaurant.reviewCount
                        it[imageUrl]      = restaurant.imageUrl
                        it[yelpId]        = restaurant.yelpId
                        it[googlePlaceId] = restaurant.googlePlaceId
                        it[createdAt]     = System.currentTimeMillis()
                        it[updatedAt]     = System.currentTimeMillis()
                    }
                    logger.debug("Inserted restaurant: ${restaurant.name} (${restaurant.id})")
                } else {
                    RestaurantTable.update({ RestaurantTable.id eq restaurant.id }) {
                        it[name]        = restaurant.name
                        it[latitude]    = restaurant.latitude
                        it[longitude]   = restaurant.longitude
                        it[cuisines]    = restaurant.cuisines.joinToString(",")
                        it[rating]      = restaurant.rating
                        it[reviewCount] = restaurant.reviewCount
                        if (restaurant.imageUrl.isNotEmpty()) it[imageUrl] = restaurant.imageUrl
                        if (restaurant.yelpId        != null) it[yelpId]        = restaurant.yelpId
                        if (restaurant.googlePlaceId != null) it[googlePlaceId] = restaurant.googlePlaceId
                        it[updatedAt] = System.currentTimeMillis()
                    }
                    logger.debug("Updated restaurant: ${restaurant.name} (${restaurant.id})")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save restaurant '${restaurant.name}': ${e.message}", e)
        }
    }

    /** Returns the restaurant with the exact [id], or null if not found. */
    fun findById(id: String): Restaurant? = try {
        transaction {
            RestaurantTable
                .select { RestaurantTable.id eq id }
                .firstOrNull()
                ?.let { mapToRestaurant(it) }
        }
    } catch (e: Exception) {
        logger.error("findById failed for id='$id': ${e.message}", e)
        null
    }

    /** Returns the first restaurant whose name contains [name] (case-insensitive), or null. */
    fun findByName(name: String): Restaurant? = try {
        transaction {
            RestaurantTable
                .select { RestaurantTable.name.lowerCase() like "%${name.lowercase()}%" }
                .limit(1)
                .firstOrNull()
                ?.let { mapToRestaurant(it) }
        }
    } catch (e: Exception) {
        logger.error("findByName failed for name='$name': ${e.message}", e)
        null
    }

    /**
     * Full-text search on name and cuisines.
     * Returns up to [limit] results ordered by insertion time (newest first).
     */
    fun search(query: String, limit: Int = 20): List<Restaurant> = try {
        val q = "%${query.lowercase()}%"
        transaction {
            RestaurantTable
                .select {
                    (RestaurantTable.name.lowerCase()     like q) or
                    (RestaurantTable.cuisines.lowerCase() like q)
                }
                .orderBy(RestaurantTable.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { mapToRestaurant(it) }
        }
    } catch (e: Exception) {
        logger.error("search failed for query='$query': ${e.message}", e)
        emptyList()
    }

    /** Returns up to [limit] restaurants ordered by rating descending. */
    fun getTrending(limit: Int = 20): List<Restaurant> = try {
        transaction {
            RestaurantTable
                .selectAll()
                .orderBy(RestaurantTable.rating, SortOrder.DESC)
                .limit(limit)
                .map { mapToRestaurant(it) }
        }
    } catch (e: Exception) {
        logger.error("getTrending failed: ${e.message}", e)
        emptyList()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun mapToRestaurant(row: ResultRow) = Restaurant(
        id            = row[RestaurantTable.id],
        name          = row[RestaurantTable.name],
        latitude      = row[RestaurantTable.latitude],
        longitude     = row[RestaurantTable.longitude],
        cuisines      = row[RestaurantTable.cuisines]
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() },
        rating        = row[RestaurantTable.rating],
        reviewCount   = row[RestaurantTable.reviewCount],
        imageUrl      = row[RestaurantTable.imageUrl],
        yelpId        = row[RestaurantTable.yelpId],
        googlePlaceId = row[RestaurantTable.googlePlaceId],
    )
}
