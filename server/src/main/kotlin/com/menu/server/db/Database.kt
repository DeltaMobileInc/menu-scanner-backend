package com.menu.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Manages the application's PostgreSQL connection pool.
 *
 * Call [DatabaseFactory.init] once at startup (inside Application.module).
 * All subsequent Exposed [transaction] blocks will use the connection established here.
 *
 * Environment variables:
 *   DATABASE_URL  – full JDBC URL  (default: jdbc:postgresql://localhost:5432/menu_scanner)
 *   DB_USER       – database username (default: postgres)
 *   DB_PASSWORD   – database password (default: postgres)
 */
object DatabaseFactory {

    private val logger = LoggerFactory.getLogger("DatabaseFactory")
    private lateinit var dataSource: HikariDataSource

    fun init() {
        val databaseUrl = System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/menu_scanner"
        val user     = System.getenv("DB_USER")     ?: "postgres"
        val password = System.getenv("DB_PASSWORD") ?: "postgres"

        logger.info("Initialising database pool → $databaseUrl")

        val config = HikariConfig().apply {
            jdbcUrl              = databaseUrl
            username             = user
            this.password        = password
            driverClassName      = "org.postgresql.Driver"
            maximumPoolSize      = 10
            minimumIdle          = 2
            connectionTimeout    = 30_000
            idleTimeout          = 600_000
            maxLifetime          = 1_800_000
            isAutoCommit         = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        // Create tables if they do not exist yet (idempotent).
        transaction {
            SchemaUtils.create(
                UserTable,
                RestaurantTable,
                ScanTable,
                FavoriteTable,
            )
        }

        logger.info("Database initialised – all tables verified/created")
    }

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            logger.info("Database connection pool closed")
        }
    }
}
