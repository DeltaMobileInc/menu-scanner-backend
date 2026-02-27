# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Restaurant Menu Scanner Backend — a Kotlin/Ktor REST API that processes menu scans from mobile clients. Backed by PostgreSQL (Exposed ORM + HikariCP) with live Yelp Fusion and Google Places API integration and a cache-aside caching strategy.

## Commands

```bash
# Start PostgreSQL (required before running the server)
docker-compose up -d

# Run the server (dev) — reads env vars from the shell
./gradlew :server:run

# Run all tests
./gradlew :server:test

# Run a single test class
./gradlew :server:test --tests "com.menu.server.ScanRoutesTest"

# Build a fat JAR for deployment
./gradlew :server:shadowJar
# Output: server/build/libs/restaurant-menu-scanner-backend.jar
```

## Architecture

**Single Gradle module (`server`) with layered architecture:**

```
Application.kt              – server bootstrap, plugin installation, wires all layers
models/Models.kt            – all serializable DTOs
db/Database.kt              – DatabaseFactory singleton (HikariCP pool + Exposed connect)
db/Tables.kt                – Exposed ORM table objects (UserTable, RestaurantTable,
                              ScanTable, FavoriteTable)
repository/
  RestaurantRepository.kt  – blocking JDBC CRUD via Exposed SQL DSL
client/
  YelpClient.kt            – Yelp Fusion /businesses/search
  GoogleMapsClient.kt      – Google Places textsearch
services/
  RealRestaurantService.kt – cache-aside: DB first, then parallel Yelp+Google
routes/ScanRoutes.kt        – all route handlers; real routes accept the service
                              as a constructor parameter
```

**Data flow for a restaurant search:**
1. `realRestaurantRoutes` handler calls `RealRestaurantService.searchRestaurants()`
2. Service queries `RestaurantRepository.search()` on `Dispatchers.IO`
3. On a cache miss, Yelp and Google are called **in parallel** via `async/await`
4. Results are deduplicated, sorted by rating, saved to DB, and returned

**Adding new endpoints:** register the handler function in `ScanRoutes.kt`, pass
the required service/repository via parameter, and call it from the `routing {}` block in `Application.kt`.

## API Structure

```
POST   /api/v1/scans                                    - Submit menu scan (identifies restaurant via real APIs)
GET    /api/v1/scans/{scanId}                           - 501 Not Implemented (scan persistence is Week 4)
GET    /api/v1/restaurants/search?q=<query>[&lat&lng]   - Search (DB cache → Yelp + Google in parallel)
GET    /api/v1/restaurants/trending[?limit=N]           - Top restaurants by rating
GET    /api/v1/restaurants/{id}                         - Get by database ID
GET    /api/v1/restaurants/{id}/images                  - Images from stored imageUrl
POST   /api/v1/auth/login                               - Login, returns JWT (mock)
POST   /api/v1/auth/register                            - Register, returns JWT (mock)
GET    /health                                           - Health check
```

## Configuration

Copy `.env` and populate your real keys, then `source .env` before running.

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8080` | Server listen port |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/menu_scanner` | Full JDBC URL |
| `DB_USER` | `postgres` | |
| `DB_PASSWORD` | `postgres` | |
| `JWT_SECRET` | insecure placeholder | **Must be overridden in production** |
| `YELP_API_KEY` | *(empty)* | Yelp Fusion key — searches skipped if absent |
| `GOOGLE_PLACES_API_KEY` | *(empty)* | Google Places key — searches skipped if absent |

JWT config: audience `"menu-scanner-api"`, issuer `"menu-scanner"`, algorithm HMAC256.
`authenticate("auth-jwt")` is scaffolded but not yet enforced on individual endpoints.

Database tables are created automatically on first startup via `SchemaUtils.create()`.
The HikariCP pool is configured with `maximumPoolSize=10`, `minimumIdle=2`.

## Testing

Tests use Ktor's `testApplication` DSL (no real server started). Four test files:
- `ApplicationTest.kt` — health check, default headers, 404 handling
- `ScanRoutesTest.kt` — scan submission/retrieval
- `AuthRoutesTest.kt` — login/register
- `RestaurantRoutesTest.kt` — search and restaurant endpoints

Existing tests were written against the mock routes. After migrating to real routes,
tests that call restaurant/scan endpoints need to mock `RealRestaurantService`
(MockK is already a test dependency). DB-level tests require a running PostgreSQL
instance or an in-memory H2 database configured for Exposed.

## Key Conventions

- JSON serialization via `kotlinx.serialization` with `ignoreUnknownKeys = true`
- All error responses use the shared `ErrorResponse` data class
- `IllegalArgumentException` → 400, unhandled exceptions → 500 (status pages plugin)
- Logging: DEBUG for `com.menu.*`, INFO for Ktor, WARN for Netty/coroutines; rolling file at `logs/application.log`
- Repository methods are **regular blocking functions** (not `suspend`); callers wrap them in `withContext(Dispatchers.IO)`
- API clients (`YelpClient`, `GoogleMapsClient`) are `suspend` functions using the shared `HttpClient` configured with `ContentNegotiation` JSON
- The `Restaurant` model has optional `yelpId` and `googlePlaceId` fields used only by the persistence layer; they are `null` for restaurants not yet sourced from those APIs
