package com.menu.server.routes

import com.menu.server.models.ErrorResponse
import com.menu.server.models.ImageResult
import com.menu.server.models.ImagesResponse
import com.menu.server.models.MenuItem
import com.menu.server.models.MenuSection
import com.menu.server.models.ProcessingTime
import com.menu.server.models.Restaurant
import com.menu.server.models.ScanRequest
import com.menu.server.models.ScanResponse
import com.menu.server.models.SearchResponse
import com.menu.server.models.UserLoginRequest
import com.menu.server.models.UserLoginResponse
import com.menu.server.services.RealRestaurantService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ScanRoutes")

// ═══════════════════════════════════════════════════════════════════════════════
// REAL (production) routes – use RealRestaurantService backed by PostgreSQL
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Registers production restaurant endpoints that delegate to [restaurantService].
 *
 * Endpoints:
 *  GET /api/v1/restaurants/search?q=…[&lat=…&lng=…]  – keyword + optional location search
 *  GET /api/v1/restaurants/trending                   – top restaurants by rating
 *  GET /api/v1/restaurants/{id}                       – fetch single restaurant
 *  GET /api/v1/restaurants/{id}/images                – fetch restaurant images
 */
fun Routing.realRestaurantRoutes(restaurantService: RealRestaurantService) {
    route("/api/v1/restaurants") {

        // GET /api/v1/restaurants/search?q=pizza[&lat=37.77&lng=-122.41]
        get("/search") {
            try {
                val query = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() }
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'q' is required"),
                    )

                val latitude  = call.request.queryParameters["lat"]?.toDoubleOrNull()
                val longitude = call.request.queryParameters["lng"]?.toDoubleOrNull()

                logger.info("Restaurant search: q='$query' lat=$latitude lng=$longitude")

                val results = restaurantService.searchRestaurants(query, latitude, longitude)

                logger.info("Search returned ${results.size} result(s) for '$query'")
                call.respond(HttpStatusCode.OK, SearchResponse(results = results))

            } catch (e: Exception) {
                logger.error("Restaurant search failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Search failed"),
                )
            }
        }

        // GET /api/v1/restaurants/trending
        get("/trending") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val results = restaurantService.getTrendingRestaurants(limit)
                call.respond(HttpStatusCode.OK, SearchResponse(results = results))
            } catch (e: Exception) {
                logger.error("Trending restaurants failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to retrieve trending restaurants"),
                )
            }
        }

        // GET /api/v1/restaurants/{id}
        get("/{id}") {
            try {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Restaurant ID is required"),
                    )

                val restaurant = restaurantService.getRestaurant(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Restaurant not found: $id"),
                    )

                call.respond(HttpStatusCode.OK, restaurant)

            } catch (e: Exception) {
                logger.error("Failed to fetch restaurant", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch restaurant"),
                )
            }
        }

        // GET /api/v1/restaurants/{id}/images
        // Returns stored imageUrl wrapped in the standard ImagesResponse envelope.
        // Extend this later to call Yelp/Google Photos APIs for richer results.
        get("/{id}/images") {
            try {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Restaurant ID is required"),
                    )

                val restaurant = restaurantService.getRestaurant(id)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Restaurant not found: $id"),
                    )

                val images = if (restaurant.imageUrl.isNotBlank()) {
                    listOf(
                        ImageResult(
                            url            = restaurant.imageUrl,
                            source         = if (restaurant.yelpId != null) "yelp" else "google_maps",
                            uploadedBy     = "business_owner",
                            uploadedAt     = System.currentTimeMillis(),
                            relevanceScore = 1.0f,
                            thumbnailUrl   = restaurant.imageUrl,
                        ),
                    )
                } else emptyList()

                call.respond(
                    HttpStatusCode.OK,
                    ImagesResponse(restaurantId = id, images = images, total = images.size),
                )

            } catch (e: Exception) {
                logger.error("Failed to fetch images", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch images"),
                )
            }
        }
    }
}

/**
 * Registers production scan endpoints that identify the restaurant via
 * [restaurantService] and return structured results.
 *
 * Endpoints:
 *  POST /api/v1/scans         – submit a new scan
 *  GET  /api/v1/scans/{id}   – retrieve a scan (returns 501 until scan persistence is added)
 */
fun Routing.realScanRoutes(restaurantService: RealRestaurantService) {
    route("/api/v1/scans") {

        // POST /api/v1/scans
        post {
            try {
                val request = call.receive<ScanRequest>()
                val serverStartMs = System.currentTimeMillis()

                logger.info(
                    "Processing scan: restaurantName='${request.restaurantName}' " +
                        "quality=${request.imageQualityScore}",
                )

                val searchQuery = request.restaurantName?.takeIf { it.isNotBlank() }
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("restaurantName is required for a scan"),
                    )

                val restaurant = restaurantService
                    .searchRestaurants(searchQuery, request.latitude, request.longitude)
                    .firstOrNull()
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse("Could not identify a restaurant for: $searchQuery"),
                    )

                val serverMs = System.currentTimeMillis() - serverStartMs

                val response = ScanResponse(
                    scanId     = "scan_${System.currentTimeMillis()}",
                    restaurant = restaurant,
                    menuName   = request.menuName ?: "Standard Menu",
                    sections   = request.sections.ifEmpty {
                        listOf(
                            MenuSection(
                                name  = "Items",
                                items = listOf(MenuItem(name = "See menu for details")),
                            ),
                        )
                    },
                    images        = emptyList(),
                    processingTime = ProcessingTime(
                        deviceMs = request.deviceProcessingTime,
                        serverMs = serverMs,
                        totalMs  = request.deviceProcessingTime + serverMs,
                    ),
                    qualityScore = request.imageQualityScore,
                )

                logger.info("Scan complete: scanId=${response.scanId} restaurant='${restaurant.name}'")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Scan processing failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Scan processing failed"),
                )
            }
        }

        // GET /api/v1/scans/{scanId}
        // Scan persistence (ScanRepository) is a Week 4 addition.
        get("/{scanId}") {
            val scanId = call.parameters["scanId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("Scan ID is required"),
                )
            call.respond(
                HttpStatusCode.NotImplemented,
                ErrorResponse("Scan retrieval by ID ($scanId) will be available in a future release"),
            )
        }
    }
}

// ── Scan endpoints ────────────────────────────────────────────────────────────

/**
 * Registers scan-related API endpoints on the given [Routing] receiver.
 *
 * Endpoints:
 * - POST /api/v1/scans        — submit a new menu scan
 * - GET  /api/v1/scans/{id}  — retrieve a previously submitted scan
 */
fun Routing.scanRoutes() {
    route("/api/v1/scans") {

        // POST /api/v1/scans
        // Accepts a ScanRequest from the mobile client and returns an enriched ScanResponse.
        post {
            try {
                val request = call.receive<ScanRequest>()
                logger.info("Processing scan: restaurantName='${request.restaurantName}' quality=${request.imageQualityScore}")

                val serverStartMs = System.currentTimeMillis()

                val response = ScanResponse(
                    scanId = "scan_${System.currentTimeMillis()}",
                    restaurant = Restaurant(
                        id = "rest_1",
                        name = request.restaurantName ?: "Unknown Restaurant",
                        latitude = request.latitude ?: 0.0,
                        longitude = request.longitude ?: 0.0,
                        cuisines = listOf("Various"),
                        rating = 4.2f,
                        reviewCount = 150,
                        imageUrl = "https://example.com/restaurant.jpg",
                    ),
                    menuName = request.menuName ?: "Standard Menu",
                    sections = request.sections.ifEmpty {
                        listOf(
                            MenuSection(
                                name = "Items",
                                items = listOf(MenuItem(name = "Sample Item", price = 9.99f)),
                            ),
                        )
                    },
                    images = listOf(
                        ImageResult(
                            url = "https://example.com/image1.jpg",
                            source = "yelp",
                            uploadedBy = "yelp_user",
                            uploadedAt = System.currentTimeMillis(),
                            relevanceScore = 0.95f,
                            thumbnailUrl = "https://example.com/thumb1.jpg",
                        ),
                    ),
                    processingTime = ProcessingTime(
                        deviceMs = request.deviceProcessingTime,
                        serverMs = System.currentTimeMillis() - serverStartMs,
                        totalMs = request.deviceProcessingTime + (System.currentTimeMillis() - serverStartMs),
                    ),
                    qualityScore = request.imageQualityScore,
                )

                logger.info("Scan complete: scanId=${response.scanId}")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Scan processing failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Scan processing failed"),
                )
            }
        }

        // GET /api/v1/scans/{scanId}
        // Returns a previously processed scan by its ID (mock data for Week 2).
        get("/{scanId}") {
            try {
                val scanId = call.parameters["scanId"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Scan ID is required"),
                    )

                logger.info("Fetching scan: scanId=$scanId")

                val response = ScanResponse(
                    scanId = scanId,
                    restaurant = Restaurant(
                        id = "rest_1",
                        name = "Mock Restaurant",
                        latitude = 37.7749,
                        longitude = -122.4194,
                        cuisines = listOf("Italian", "Pizza"),
                        rating = 4.2f,
                        reviewCount = 150,
                        imageUrl = "https://example.com/restaurant.jpg",
                    ),
                    menuName = "Mock Menu",
                    sections = listOf(
                        MenuSection(
                            name = "Appetizers",
                            items = listOf(
                                MenuItem(name = "Garlic Bread", price = 5.99f, description = "Toasted with herb butter"),
                                MenuItem(name = "Bruschetta", price = 7.99f, description = "Fresh tomatoes on toast"),
                            ),
                        ),
                        MenuSection(
                            name = "Main Courses",
                            items = listOf(
                                MenuItem(name = "Margherita Pizza", price = 14.99f, description = "Classic tomato and mozzarella"),
                                MenuItem(name = "Grilled Salmon", price = 22.99f, description = "With seasonal vegetables"),
                            ),
                        ),
                    ),
                    images = listOf(
                        ImageResult(
                            url = "https://example.com/image1.jpg",
                            source = "yelp",
                            uploadedBy = "yelp_user",
                            uploadedAt = System.currentTimeMillis(),
                            relevanceScore = 0.95f,
                            thumbnailUrl = "https://example.com/thumb1.jpg",
                        ),
                    ),
                    processingTime = ProcessingTime(
                        deviceMs = 350,
                        serverMs = 100,
                        totalMs = 450,
                    ),
                    qualityScore = 0.85f,
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Failed to retrieve scan", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to retrieve scan"),
                )
            }
        }
    }
}

// ── Restaurant endpoints ──────────────────────────────────────────────────────

/**
 * Registers restaurant-related API endpoints on the given [Routing] receiver.
 *
 * Endpoints:
 * - GET /api/v1/restaurants/search       — keyword search
 * - GET /api/v1/restaurants/{id}         — fetch by ID
 * - GET /api/v1/restaurants/{id}/images  — fetch photos
 */
fun Routing.restaurantRoutes() {
    route("/api/v1/restaurants") {

        // GET /api/v1/restaurants/search?q=<query>
        // Returns restaurants whose name or cuisine matches the query string.
        get("/search") {
            try {
                val query = call.request.queryParameters["q"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Query parameter 'q' is required"),
                    )

                logger.info("Searching restaurants: query='$query'")

                val results = listOf(
                    Restaurant(
                        id = "rest_1",
                        name = "Pizza Hut",
                        latitude = 37.7749,
                        longitude = -122.4194,
                        cuisines = listOf("Italian", "Pizza"),
                        rating = 4.2f,
                        reviewCount = 150,
                        imageUrl = "https://example.com/pizza-hut.jpg",
                    ),
                    Restaurant(
                        id = "rest_2",
                        name = "Burger King",
                        latitude = 37.7849,
                        longitude = -122.4094,
                        cuisines = listOf("Fast Food", "Burgers"),
                        rating = 3.8f,
                        reviewCount = 200,
                        imageUrl = "https://example.com/burger-king.jpg",
                    ),
                    Restaurant(
                        id = "rest_3",
                        name = "The Italian Kitchen",
                        latitude = 37.7650,
                        longitude = -122.4300,
                        cuisines = listOf("Italian", "Pasta", "Seafood"),
                        rating = 4.7f,
                        reviewCount = 320,
                        imageUrl = "https://example.com/italian-kitchen.jpg",
                    ),
                )

                // Filter by query (case-insensitive mock search)
                val filtered = results.filter { r ->
                    r.name.contains(query, ignoreCase = true) ||
                        r.cuisines.any { it.contains(query, ignoreCase = true) }
                }.ifEmpty { results } // Return all if no match (mock behaviour)

                logger.info("Search returned ${filtered.size} result(s) for '$query'")
                call.respond(HttpStatusCode.OK, SearchResponse(results = filtered))

            } catch (e: Exception) {
                logger.error("Restaurant search failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Search failed"),
                )
            }
        }

        // GET /api/v1/restaurants/{id}
        // Returns full details for a single restaurant.
        get("/{id}") {
            try {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Restaurant ID is required"),
                    )

                logger.info("Fetching restaurant: id=$id")

                val restaurant = Restaurant(
                    id = id,
                    name = "Pizza Hut",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    cuisines = listOf("Italian", "Pizza"),
                    rating = 4.2f,
                    reviewCount = 150,
                    imageUrl = "https://example.com/pizza-hut.jpg",
                )

                call.respond(HttpStatusCode.OK, restaurant)

            } catch (e: Exception) {
                logger.error("Failed to fetch restaurant", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch restaurant"),
                )
            }
        }

        // GET /api/v1/restaurants/{id}/images
        // Returns photos for a restaurant sourced from Yelp / Google Maps.
        get("/{id}/images") {
            try {
                val id = call.parameters["id"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Restaurant ID is required"),
                    )

                logger.info("Fetching images for restaurant: id=$id")

                val images = listOf(
                    ImageResult(
                        url = "https://example.com/rest-$id-1.jpg",
                        source = "yelp",
                        uploadedBy = "yelp_business_owner",
                        uploadedAt = System.currentTimeMillis() - 86_400_000L,
                        relevanceScore = 0.95f,
                        thumbnailUrl = "https://example.com/rest-$id-thumb1.jpg",
                    ),
                    ImageResult(
                        url = "https://example.com/rest-$id-2.jpg",
                        source = "google_maps",
                        uploadedBy = "google_contributor",
                        uploadedAt = System.currentTimeMillis() - 172_800_000L,
                        relevanceScore = 0.88f,
                        thumbnailUrl = "https://example.com/rest-$id-thumb2.jpg",
                    ),
                    ImageResult(
                        url = "https://example.com/rest-$id-3.jpg",
                        source = "instagram",
                        uploadedBy = "foodie_user_42",
                        uploadedAt = System.currentTimeMillis() - 3_600_000L,
                        relevanceScore = 0.72f,
                        thumbnailUrl = "https://example.com/rest-$id-thumb3.jpg",
                    ),
                )

                call.respond(
                    HttpStatusCode.OK,
                    ImagesResponse(restaurantId = id, images = images, total = images.size),
                )

            } catch (e: Exception) {
                logger.error("Failed to fetch images", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Failed to fetch images"),
                )
            }
        }
    }
}

// ── Auth endpoints ────────────────────────────────────────────────────────────

/**
 * Registers authentication endpoints on the given [Routing] receiver.
 *
 * Endpoints:
 * - POST /api/v1/auth/login    — authenticate existing user, returns JWT
 * - POST /api/v1/auth/register — create new account, returns JWT
 */
fun Routing.authRoutes() {
    route("/api/v1/auth") {

        // POST /api/v1/auth/login
        // Accepts email + password, returns a mock JWT token.
        post("/login") {
            try {
                val request = call.receive<UserLoginRequest>()
                logger.info("Login attempt: email=${request.email}")

                val response = UserLoginResponse(
                    userId = "user_123",
                    email = request.email,
                    token = "mock_jwt_token_${System.currentTimeMillis()}",
                    expiresAt = System.currentTimeMillis() + 3_600_000L, // +1 hour
                )

                logger.info("Login successful: userId=${response.userId}")
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                logger.error("Login failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Login failed"),
                )
            }
        }

        // POST /api/v1/auth/register
        // Creates a new mock user account and returns a JWT token.
        post("/register") {
            try {
                val request = call.receive<UserLoginRequest>()
                logger.info("Register attempt: email=${request.email}")

                val response = UserLoginResponse(
                    userId = "user_${System.currentTimeMillis()}",
                    email = request.email,
                    token = "mock_jwt_token_${System.currentTimeMillis()}",
                    expiresAt = System.currentTimeMillis() + 3_600_000L, // +1 hour
                )

                logger.info("Registration successful: userId=${response.userId}")
                call.respond(HttpStatusCode.Created, response)

            } catch (e: Exception) {
                logger.error("Registration failed", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(e.message ?: "Registration failed"),
                )
            }
        }
    }
}
