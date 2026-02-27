package com.menu.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.menu.server.client.GoogleMapsClient
import com.menu.server.client.YelpClient
import com.menu.server.db.DatabaseFactory
import com.menu.server.models.ErrorResponse
import com.menu.server.models.HealthResponse
import com.menu.server.repository.RestaurantRepository
import com.menu.server.routes.authRoutes
import com.menu.server.routes.realRestaurantRoutes
import com.menu.server.routes.realScanRoutes
import com.menu.server.services.RealRestaurantService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

private val logger = LoggerFactory.getLogger("Application")

private val JWT_SECRET   = System.getenv("JWT_SECRET")   ?: "your-secret-key-replace-in-production"
private const val JWT_AUDIENCE = "menu-scanner-api"
private const val JWT_ISSUER   = "menu-scanner"

fun main() {
    embeddedServer(
        factory = Netty,
        port    = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host    = "0.0.0.0",
        module  = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    // ── 1. Database ───────────────────────────────────────────────────────────
    DatabaseFactory.init()

    // ── 2. HTTP client (shared across all API clients) ────────────────────────
    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient         = true
            })
        }
    }

    // ── 3. Repository, API clients, service ──────────────────────────────────
    val restaurantRepository = RestaurantRepository()

    val yelpClient = YelpClient(
        httpClient,
        apiKey = System.getenv("YELP_API_KEY") ?: "",
    )
    val googleMapsClient = GoogleMapsClient(
        httpClient,
        apiKey = System.getenv("GOOGLE_PLACES_API_KEY") ?: "",
    )

    val restaurantService = RealRestaurantService(
        restaurantRepository,
        yelpClient,
        googleMapsClient,
    )

    // ── 4. Ktor plugins ───────────────────────────────────────────────────────
    installPlugins()

    // ── 5. Routes ─────────────────────────────────────────────────────────────
    routing {
        realScanRoutes(restaurantService)
        realRestaurantRoutes(restaurantService)
        authRoutes()
        healthRoutes()
    }

    // ── 6. Graceful shutdown ──────────────────────────────────────────────────
    environment.monitor.subscribe(ApplicationStopped) {
        httpClient.close()
        DatabaseFactory.close()
    }

    logger.info("Server started  →  http://localhost:8080")
    logger.info("Health check    →  http://localhost:8080/health")
}

fun Application.installPlugins() {

    install(DefaultHeaders) {
        header("X-Engine",  "Ktor")
        header("X-Version", "1.0.0")
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint       = true
            isLenient         = true
            ignoreUnknownKeys = true
            encodeDefaults    = true
        })
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Bad request: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Menu Scanner API"
            verifier(
                JWT.require(Algorithm.HMAC256(JWT_SECRET))
                    .withAudience(JWT_AUDIENCE)
                    .withIssuer(JWT_ISSUER)
                    .build(),
            )
            validate { credential ->
                if (credential.payload.audience.contains(JWT_AUDIENCE)) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid or expired token"))
            }
        }
    }
}

fun Routing.healthRoutes() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(status = "ok", timestamp = System.currentTimeMillis()),
        )
    }
}
