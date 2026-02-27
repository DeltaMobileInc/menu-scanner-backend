// FILE 3: server/build.gradle.kts
// Server module build configuration with all Ktor and supporting dependencies

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
}

group = "com.menu"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

application {
    mainClass.set("com.menu.server.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("restaurant-menu-scanner-backend.jar")
    }
}

dependencies {
    // ── Ktor server ────────────────────────────────────────────────────────
    implementation("io.ktor:ktor-server-core:2.3.4")
    implementation("io.ktor:ktor-server-netty:2.3.4")
    implementation("io.ktor:ktor-server-auth:2.3.4")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.4")
    implementation("io.ktor:ktor-server-call-logging:2.3.4")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-server-default-headers:2.3.4")
    implementation("io.ktor:ktor-server-status-pages:2.3.4")

    // ── Ktor client (for future Yelp/Google Maps calls) ────────────────────
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-cio:2.3.4")

    // ── Serialization ──────────────────────────────────────────────────────
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.1")

    // ── Authentication ─────────────────────────────────────────────────────
    implementation("com.auth0:java-jwt:4.4.0")

    // ── Coroutines ─────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // ── Logging ────────────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.slf4j:slf4j-api:2.0.9")

    // Database (Exposed ORM)
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.41.1")

    // PostgreSQL Driver
    implementation("org.postgresql:postgresql:42.6.0")

    // Connection Pool
    implementation("com.zaxxer:HikariCP:5.0.1")

    // HTTP Client – content negotiation (JSON parsing for Yelp / Google APIs)
    implementation("io.ktor:ktor-client-content-negotiation:2.3.4")

    // ── Testing ────────────────────────────────────────────────────────────
    testImplementation("io.ktor:ktor-server-tests:2.3.4")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
    testImplementation("io.mockk:mockk:1.13.7")
}
