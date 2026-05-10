package ru.mirea.robocompetition.archive

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Тонкая обёртка над пулом, которую можно закрыть. Скрывает HikariCP от
 * web-модуля — он видит только DataSource + AutoCloseable.
 */
class ArenaDataSource internal constructor(
    private val delegate: HikariDataSource
) : DataSource, AutoCloseable {
    override fun getConnection(): Connection = delegate.connection
    override fun getConnection(username: String?, password: String?): Connection =
        delegate.getConnection(username, password)
    override fun getLogWriter(): PrintWriter = delegate.logWriter
    override fun setLogWriter(out: PrintWriter?) { delegate.logWriter = out }
    override fun setLoginTimeout(seconds: Int) { delegate.loginTimeout = seconds }
    override fun getLoginTimeout(): Int = delegate.loginTimeout
    override fun getParentLogger(): Logger = delegate.parentLogger
    override fun <T : Any?> unwrap(iface: Class<T>?): T = delegate.unwrap(iface)
    override fun isWrapperFor(iface: Class<*>?): Boolean = delegate.isWrapperFor(iface)
    override fun close() = delegate.close()
}

object Database {

    data class Config(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val poolSize: Int = 10,
        val changelog: String = "db/changelog/changelog-master.xml"
    )

    fun fromEnv(): Config = Config(
        jdbcUrl = requireEnv("PG_URL"),
        user = requireEnv("PG_USER"),
        password = requireEnv("PG_PASSWORD"),
        poolSize = System.getenv("PG_POOL_SIZE")?.toIntOrNull() ?: 10
    )

    fun start(config: Config): ArenaDataSource {
        val hikari = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            driverClassName = "org.postgresql.Driver"
            poolName = "arena-postgres"
        })
        runMigrations(hikari, config.changelog)
        return ArenaDataSource(hikari)
    }

    fun runMigrations(dataSource: DataSource, changelog: String) {
        dataSource.connection.use { conn ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(conn))
            Liquibase(changelog, ClassLoaderResourceAccessor(), database).use { lb ->
                lb.update("")
            }
        }
    }

    private fun requireEnv(name: String): String =
        System.getenv(name) ?: error("environment variable $name is not set")
}
