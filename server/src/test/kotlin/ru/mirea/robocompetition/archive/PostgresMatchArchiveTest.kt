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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresMatchArchiveTest {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var dataSource: HikariDataSource

    @BeforeAll
    fun startContainer() {
        postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("arena")
            .withUsername("arena")
            .withPassword("arena")
        postgres.start()

        waitForJdbc(postgres.jdbcUrl, postgres.username, postgres.password, timeoutMs = 30_000)

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 4
            connectionTimeout = 10_000
        })
        Database.runMigrations(dataSource as DataSource, "db/changelog/changelog-master.xml")

        // Регистрируем ботов, участвующих в тестах — FK требует их наличия в user_bots.
        seedBot("a")
        seedBot("b")
    }

    private fun waitForJdbc(url: String, user: String, pass: String, timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastErr: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                java.sql.DriverManager.getConnection(url, user, pass).use { /* ok */ }
                return
            } catch (e: Throwable) {
                lastErr = e
                Thread.sleep(250)
            }
        }
        throw IllegalStateException("Postgres JDBC URL $url not reachable within ${timeoutMs}ms", lastErr)
    }

    @AfterAll
    fun stopContainer() {
        dataSource.close()
        postgres.stop()
    }

    @BeforeEach
    fun cleanTables() {
        dataSource.connection.use { c ->
            // Truncate только matches — пользователи и боты создаются один раз в @BeforeAll.
            c.createStatement().use { it.execute("TRUNCATE matches CASCADE") }
        }
    }

    @Test
    fun `accumulates snapshots and marks match finished`() {
        val (bus, archive, writer) = newWiring()
        try {
            val started = MatchEvent.Started(
                matchId = "M1",
                gameId = "collector",
                players = listOf("a", "b"),
                width = 8,
                height = 8,
                maxRounds = 5,
                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                initialSnapshot = snapshot(round = 0)
            )
            bus.publish(started)
            bus.publish(MatchEvent.Round("M1", snapshot(round = 1)))
            bus.publish(MatchEvent.Round("M1", snapshot(round = 2)))

            waitForRound(archive, "M1", 2)

            val activeSummary = archive.listMatches().single()
            assertEquals("M1", activeSummary.matchId)
            assertEquals(MatchStatus.ACTIVE, activeSummary.status)
            assertEquals(2, activeSummary.currentRound)
            assertNull(activeSummary.winner)
            assertNull(activeSummary.finishedAt)

            val activeDetail = archive.getDetail("M1")!!
            assertEquals(3, activeDetail.snapshots.size)
            assertEquals(listOf(0, 1, 2), activeDetail.snapshots.map { it.round })

            val finishTime = Instant.parse("2026-01-01T00:01:00Z")
            val result = MatchResult(
                matchId = "M1", timestamp = finishTime,
                config = GameConfig(width = 8, height = 8, coinCount = 15, maxRounds = 5, stepDelayMs = 500L),
                finalScores = mapOf("a" to 3, "b" to 1), winner = "a", rounds = 5
            )
            bus.publish(MatchEvent.Finished("M1", result, finishTime))

            waitForFinished(archive, "M1")

            val finishedSummary = archive.listMatches().single()
            assertEquals(MatchStatus.FINISHED, finishedSummary.status)
            assertEquals("a", finishedSummary.winner)
            assertEquals(finishTime, finishedSummary.finishedAt)

            val finishedDetail = archive.getDetail("M1")!!
            assertEquals(result, finishedDetail.result)
        } finally {
            writer.close()
        }
    }

    @Test
    fun `getDetail returns null for unknown match`() {
        val (_, archive, writer) = newWiring()
        try {
            assertNull(archive.getDetail("nope"))
        } finally {
            writer.close()
        }
    }

    @Test
    fun `listMatches returns matches sorted by startedAt descending`() {
        val (bus, archive, writer) = newWiring()
        try {
            bus.publish(startedEvent("first", Instant.parse("2026-01-01T00:00:00Z")))
            bus.publish(startedEvent("second", Instant.parse("2026-01-01T01:00:00Z")))
            bus.publish(startedEvent("third", Instant.parse("2026-01-01T00:30:00Z")))

            waitForCount(archive, expected = 3)

            val ids = archive.listMatches().map { it.matchId }
            assertEquals(listOf("second", "third", "first"), ids)
        } finally {
            writer.close()
        }
    }

    @Test
    fun `events for unknown match id are ignored after Started missed`() {
        val (bus, archive, writer) = newWiring()
        try {
            bus.publish(startedEvent("M1", Instant.parse("2026-01-01T00:00:00Z")))
            // Round для матча, которого нет — FK + ON CONFLICT отвергнут writer'ом
            bus.publish(MatchEvent.Round("M2", snapshot(round = 1)))

            waitForCount(archive, expected = 1)

            assertEquals(1, archive.listMatches().size)
            assertTrue(archive.listMatches().single().matchId == "M1")
        } finally {
            writer.close()
        }
    }

    private fun seedBot(name: String) {
        dataSource.connection.use { conn ->
            val userId = conn.prepareStatement(
                "INSERT INTO users (login, password_hash, display_name) VALUES (?, 'test', ?) RETURNING id"
            ).use { ps ->
                ps.setString(1, name)
                ps.setString(2, name)
                ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
            }
            conn.prepareStatement(
                "INSERT INTO user_bots (user_id, bot_name) VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use { ps ->
                ps.setLong(1, userId)
                ps.setString(2, name)
                ps.executeUpdate()
            }
        }
    }

    private fun newWiring(): Triple<InMemoryMatchEventBus, PostgresMatchArchive, PostgresArchiveWriter> {
        val bus = InMemoryMatchEventBus()
        val archive = PostgresMatchArchive(dataSource)
        val writer = PostgresArchiveWriter(dataSource, bus).start()
        return Triple(bus, archive, writer)
    }

    private fun waitForRound(archive: MatchArchive, matchId: String, round: Int) {
        eventually { archive.listMatches().firstOrNull { it.matchId == matchId }?.currentRound == round }
    }

    private fun waitForFinished(archive: MatchArchive, matchId: String) {
        eventually { archive.listMatches().firstOrNull { it.matchId == matchId }?.status == MatchStatus.FINISHED }
    }

    private fun waitForCount(archive: MatchArchive, expected: Int) {
        eventually { archive.listMatches().size == expected }
    }

    private fun eventually(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        error("condition did not become true within ${timeoutMs}ms")
    }

    private fun startedEvent(id: String, time: Instant) = MatchEvent.Started(
        matchId = id, gameId = "collector", players = listOf("a"),
        width = 5, height = 5, maxRounds = 5, startedAt = time,
        initialSnapshot = snapshot(round = 0)
    )

    private fun snapshot(round: Int) = MatchSnapshot(
        round = round, width = 5, height = 5, maxRounds = 5,
        bots = emptyList(), items = emptyList()
    )
}
