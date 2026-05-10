package ru.mirea.robocompetition.archive

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.events.InMemoryMatchEventBus
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresMatchStatsTest {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var dataSource: HikariDataSource

    @BeforeAll
    fun startContainer() {
        postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("arena").withUsername("arena").withPassword("arena")
        postgres.start()
        waitForJdbc(postgres.jdbcUrl, postgres.username, postgres.password)

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 4
            connectionTimeout = 10_000
        })
        Database.runMigrations(dataSource as DataSource, "db/changelog/changelog-master.xml")
    }

    @AfterAll
    fun stopContainer() {
        dataSource.close()
        postgres.stop()
    }

    @BeforeEach
    fun cleanTables() {
        dataSource.connection.use { c ->
            c.createStatement().use { it.execute("TRUNCATE match_snapshots, matches RESTART IDENTITY") }
        }
    }

    @Test
    fun `leaderboard aggregates wins draws totalScore per player and gameId`() {
        val bus = InMemoryMatchEventBus()
        val writer = PostgresArchiveWriter(dataSource, bus).start()
        try {
            // Матч 1: collector — Alice выигрывает
            publishMatch(bus, "M1", "collector", listOf("Alice", "Bob"),
                Instant.parse("2026-01-01T00:00:00Z"),
                winner = "Alice", scores = mapOf("Alice" to 5, "Bob" to 2))
            // Матч 2: collector — Alice опять выигрывает
            publishMatch(bus, "M2", "collector", listOf("Alice", "Bob"),
                Instant.parse("2026-01-02T00:00:00Z"),
                winner = "Alice", scores = mapOf("Alice" to 4, "Bob" to 3))
            // Матч 3: collector — ничья
            publishMatch(bus, "M3", "collector", listOf("Alice", "Bob"),
                Instant.parse("2026-01-03T00:00:00Z"),
                winner = null, scores = mapOf("Alice" to 1, "Bob" to 1))
            // Матч 4: другая игра — Bob выигрывает у Alice
            publishMatch(bus, "M4", "maze", listOf("Alice", "Bob"),
                Instant.parse("2026-01-04T00:00:00Z"),
                winner = "Bob", scores = mapOf("Alice" to 0, "Bob" to 10))

            eventually { archiveCount() == 4 }

            val rows = PostgresMatchStats(dataSource).leaderboard()

            // Ожидаем 4 строки: (Alice, collector), (Bob, collector), (Alice, maze), (Bob, maze)
            assertEquals(4, rows.size)

            val aliceCollector = rows.single { it.player == "Alice" && it.gameId == "collector" }
            assertEquals(3, aliceCollector.matches)
            assertEquals(2, aliceCollector.wins)
            assertEquals(1, aliceCollector.draws)
            assertEquals(5 + 4 + 1, aliceCollector.totalScore)
            assertEquals(2.0 / 3, aliceCollector.winRate, "win rate ${aliceCollector.winRate}")

            val bobCollector = rows.single { it.player == "Bob" && it.gameId == "collector" }
            assertEquals(3, bobCollector.matches)
            assertEquals(0, bobCollector.wins)
            assertEquals(1, bobCollector.draws)
            assertEquals(2 + 3 + 1, bobCollector.totalScore)

            val bobMaze = rows.single { it.player == "Bob" && it.gameId == "maze" }
            assertEquals(1, bobMaze.matches)
            assertEquals(1, bobMaze.wins)
            assertEquals(10, bobMaze.totalScore)
            assertEquals(1.0, bobMaze.winRate)

            // Сортировка: Alice/collector (2 wins) первой
            assertEquals("Alice" to "collector", rows.first().player to rows.first().gameId)
        } finally {
            writer.close()
        }
    }

    @Test
    fun `leaderboard ignores active matches`() {
        val bus = InMemoryMatchEventBus()
        val writer = PostgresArchiveWriter(dataSource, bus).start()
        try {
            bus.publish(startedEvent("M1", listOf("Alice"), Instant.parse("2026-01-01T00:00:00Z")))
            eventually { archiveCount() == 1 }
            assertTrue(PostgresMatchStats(dataSource).leaderboard().isEmpty())
        } finally {
            writer.close()
        }
    }

    private fun publishMatch(
        bus: InMemoryMatchEventBus,
        matchId: String,
        gameId: String,
        players: List<String>,
        startedAt: Instant,
        winner: String?,
        scores: Map<String, Int>
    ) {
        bus.publish(
            MatchEvent.Started(
                matchId = matchId, gameId = gameId, players = players,
                width = 5, height = 5, maxRounds = 5, startedAt = startedAt,
                initialSnapshot = MatchSnapshot(0, 5, 5, 5, emptyList(), emptyList())
            )
        )
        val finishedAt = startedAt.plusSeconds(60)
        bus.publish(
            MatchEvent.Finished(
                matchId = matchId,
                result = MatchResult(matchId, finishedAt, GameConfig(), scores, winner, 5),
                finishedAt = finishedAt
            )
        )
    }

    private fun startedEvent(id: String, players: List<String>, time: Instant) = MatchEvent.Started(
        matchId = id, gameId = "collector", players = players,
        width = 5, height = 5, maxRounds = 5, startedAt = time,
        initialSnapshot = MatchSnapshot(0, 5, 5, 5, emptyList(), emptyList())
    )

    private fun archiveCount(): Int = dataSource.connection.use { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT COUNT(*) FROM matches").use { rs ->
                rs.next(); rs.getInt(1)
            }
        }
    }

    private fun eventually(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        error("condition not satisfied within ${timeoutMs}ms")
    }

    private fun waitForJdbc(url: String, user: String, pass: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { java.sql.DriverManager.getConnection(url, user, pass).use { }; return }
            catch (e: Throwable) { last = e; Thread.sleep(250) }
        }
        throw IllegalStateException("Postgres JDBC $url not ready", last)
    }
}
