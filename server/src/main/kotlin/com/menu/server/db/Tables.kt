package com.menu.server.db

import org.jetbrains.exposed.sql.Table

// ── Users ────────────────────────────────────────────────────────────────────
// Defined first because ScanTable and FavoriteTable reference it.

object UserTable : Table("users") {
    val id               = text("id")
    val email            = text("email").uniqueIndex()
    val passwordHash     = text("password_hash")
    val subscriptionType = text("subscription_type").default("free")
    val createdAt        = long("created_at")
    val lastLogin        = long("last_login")

    override val primaryKey = PrimaryKey(id)
}

// ── Restaurants ───────────────────────────────────────────────────────────────

object RestaurantTable : Table("restaurants") {
    val id            = text("id")
    val name          = text("name").index()
    val latitude      = double("latitude")
    val longitude     = double("longitude")
    /** Comma-separated cuisine labels, e.g. "Italian,Pizza,Seafood". */
    val cuisines      = text("cuisines").default("")
    val rating        = float("rating").default(0f)
    val reviewCount   = integer("review_count").default(0)
    val imageUrl      = text("image_url").default("")
    val yelpId        = text("yelp_id").nullable()
    val googlePlaceId = text("google_place_id").nullable()
    val createdAt     = long("created_at").index()
    val updatedAt     = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

// ── Scans ─────────────────────────────────────────────────────────────────────

object ScanTable : Table("scans") {
    val scanId               = text("scan_id")
    val userId               = text("user_id").references(UserTable.id).nullable()
    val restaurantId         = text("restaurant_id").references(RestaurantTable.id)
    val restaurantName       = text("restaurant_name")
    val menuName             = text("menu_name")
    val extractedText        = text("extracted_text")
    val imageQualityScore    = float("image_quality_score")
    val processedOnDevice    = bool("processed_on_device")
    val deviceProcessingTime = long("device_processing_time")
    val createdAt            = long("created_at").index()

    override val primaryKey = PrimaryKey(scanId)
}

// ── Favourites ────────────────────────────────────────────────────────────────

object FavoriteTable : Table("favorites") {
    val id        = text("id")
    val userId    = text("user_id").references(UserTable.id)
    val scanId    = text("scan_id").references(ScanTable.scanId)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(userId, scanId)
    }
}
