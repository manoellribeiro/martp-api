package manoellribeiro.dev.core.data.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import manoellribeiro.dev.core.config.DatabaseConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Owns the connection to PostgreSQL (the API's equivalent of the Android app's MartpDatabase).
 * On startup it opens the HikariCP pool, runs the pending Flyway migrations from
 * resources/db/migration and connects Exposed. DAOs receive this class and use [database]
 * in their transactions.
 */
class MartpDatabase(
    private val config: DatabaseConfig,
) {
    private var dataSource: HikariDataSource? = null

    lateinit var database: Database
        private set

    fun connect() {
        val hikariDataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                // Neon (production) sleeps after 5 minutes without queries and bills awake time:
                // don't keep idle connections open. The first query after a pause opens a new one.
                minimumIdle = 0
                idleTimeout = 60_000
                // Exposed opens and commits its own transactions
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        dataSource = hikariDataSource

        Flyway.configure()
            .dataSource(hikariDataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        database = Database.connect(hikariDataSource)
    }

    fun close() {
        dataSource?.close()
    }
}
