# Menu Scanner Backend

A production-ready REST API backend for a restaurant menu scanning mobile app. Built with Kotlin and Ktor, backed by PostgreSQL, with live data from Yelp and Google Places.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9.20 |
| Framework | Ktor 2.3.4 (Netty) |
| Database | PostgreSQL 15 + Exposed ORM + HikariCP |
| External APIs | Yelp Fusion, Google Places |
| Auth | JWT (Auth0 java-jwt) |
| Build | Gradle (Kotlin DSL) |

## Features

- **Restaurant search** — queries Yelp and Google Places in parallel, caches results in PostgreSQL
- **Cache-aside strategy** — DB is checked first; external APIs are only called on a cache miss
- **Menu scan processing** — accepts on-device ML results from mobile clients, matches to real restaurants
- **Trending restaurants** — ranked by rating from the local database

## Project Structure

```
server/src/main/kotlin/com/menu/server/
├── Application.kt                   # Server bootstrap, plugin config
├── models/Models.kt                 # All request/response DTOs
├── db/
│   ├── Database.kt                  # HikariCP pool + Exposed setup
│   └── Tables.kt                    # ORM table definitions
├── repository/
│   └── RestaurantRepository.kt      # Database CRUD layer
├── client/
│   ├── YelpClient.kt                # Yelp Fusion API
│   └── GoogleMapsClient.kt          # Google Places API
├── services/
│   └── RealRestaurantService.kt     # Business logic + parallel API calls
└── routes/
    └── ScanRoutes.kt                # All route handlers
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | Health check |
| GET | `/api/v1/restaurants/search?q=pizza&lat=37.77&lng=-122.41` | Search restaurants |
| GET | `/api/v1/restaurants/trending?limit=10` | Top restaurants by rating |
| GET | `/api/v1/restaurants/{id}` | Get restaurant by ID |
| GET | `/api/v1/restaurants/{id}/images` | Get restaurant images |
| POST | `/api/v1/scans` | Submit a menu scan |
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login user |

## Getting Started

### Prerequisites

- JDK 17+
- Docker

### 1. Clone the repo

```bash
git clone https://github.com/YOUR_USERNAME/menu-scanner-backend.git
cd menu-scanner-backend
```

### 2. Set up environment variables

```bash
cp .env.example .env
```

Edit `.env` and fill in your API keys:

```env
DATABASE_URL=jdbc:postgresql://localhost:5433/menu_scanner
DB_USER=postgres
DB_PASSWORD=postgres
JWT_SECRET=your-secret-here
YELP_API_KEY=your-yelp-key
GOOGLE_PLACES_API_KEY=your-google-key
```

- **Yelp API key** — [yelp.com/developers/v3/manage_app](https://www.yelp.com/developers/v3/manage_app)
- **Google Places key** — [console.cloud.google.com](https://console.cloud.google.com) → Enable **Places API**

### 3. Start the database

```bash
docker-compose up -d
```

### 4. Run the server

```bash
source .env && ./gradlew :server:run
```

Server starts at `http://localhost:8080`.

## Example Requests

**Search restaurants:**
```bash
curl "http://localhost:8080/api/v1/restaurants/search?q=sushi&lat=37.7749&lng=-122.4194"
```

**Submit a scan:**
```bash
curl -X POST http://localhost:8080/api/v1/scans \
  -H "Content-Type: application/json" \
  -d '{
    "restaurantName": "pizza",
    "menuName": "Dinner Menu",
    "imageQualityScore": 0.92,
    "deviceProcessingTime": 320,
    "latitude": 37.7749,
    "longitude": -122.4194
  }'
```

**Trending restaurants:**
```bash
curl "http://localhost:8080/api/v1/restaurants/trending?limit=5"
```

## Running Tests

```bash
./gradlew :server:test
```

## Building a Fat JAR

```bash
./gradlew :server:shadowJar
java -jar server/build/libs/restaurant-menu-scanner-backend.jar
```

## Environment Variables

| Variable | Default | Required |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/menu_scanner` | Yes |
| `DB_USER` | `postgres` | Yes |
| `DB_PASSWORD` | `postgres` | Yes |
| `JWT_SECRET` | — | Yes (production) |
| `YELP_API_KEY` | — | No (search degrades gracefully) |
| `GOOGLE_PLACES_API_KEY` | — | No (search degrades gracefully) |
| `PORT` | `8080` | No |
