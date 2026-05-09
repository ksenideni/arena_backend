package ru.mirea.robocompetition.web

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import ru.mirea.robocompetition.archive.InMemoryMatchArchive
import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.events.InMemoryMatchEventBus
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult
import ru.mirea.robocompetition.web.dto.MatchDetailDto
import ru.mirea.robocompetition.web.dto.MatchSummaryDto
import ru.mirea.robocompetition.web.dto.WsMessage
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebAppTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `GET api matches returns active and finished`() = testApplication {
        val (bus, archive, broadcaster) = setupArenaState()
        application { webApp(archive = archive, broadcaster = broadcaster) }

        bus.publish(startedEvent("M1", Instant.parse("2026-01-01T00:00:00Z")))
        bus.publish(startedEvent("M2", Instant.parse("2026-01-01T01:00:00Z")))

        val client = createJsonClient()
        val list: List<MatchSummaryDto> = client.get("/api/matches").body()
        assertEquals(listOf("M2", "M1"), list.map { it.matchId })
        assertTrue(list.all { it.status == "active" })
    }

    @Test
    fun `GET api matches id returns 404 for unknown`() = testApplication {
        val (_, archive, broadcaster) = setupArenaState()
        application { webApp(archive = archive, broadcaster = broadcaster) }

        val client = createJsonClient()
        val response = client.get("/api/matches/nope")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET api matches id returns full detail with snapshots`() = testApplication {
        val (bus, archive, broadcaster) = setupArenaState()
        application { webApp(archive = archive, broadcaster = broadcaster) }

        bus.publish(startedEvent("M1", Instant.parse("2026-01-01T00:00:00Z")))
        bus.publish(MatchEvent.Round("M1", snapshot(round = 1)))
        bus.publish(MatchEvent.Round("M1", snapshot(round = 2)))

        val client = createJsonClient()
        val detail: MatchDetailDto = client.get("/api/matches/M1").body()
        assertEquals("M1", detail.summary.matchId)
        assertEquals(listOf(0, 1, 2), detail.snapshots.map { it.round })
    }

    @Test
    fun `WebSocket sends history then live rounds then finished`() = testApplication {
        val (bus, archive, broadcaster) = setupArenaState()
        application { webApp(archive = archive, broadcaster = broadcaster) }

        bus.publish(startedEvent("M1", Instant.parse("2026-01-01T00:00:00Z")))
        bus.publish(MatchEvent.Round("M1", snapshot(round = 1)))

        val client = createWsClient()
        client.webSocket("/api/matches/M1/live") {
            // 1) history
            val history = receiveDeserialized<WsMessage>()
            val h = assertIs<WsMessage.History>(history)
            assertEquals("M1", h.matchId)
            assertEquals(listOf(0, 1), h.snapshots.map { it.round })

            // 2) после подключения публикуем новые события
            bus.publish(MatchEvent.Round("M1", snapshot(round = 2)))
            val round = receiveDeserialized<WsMessage>()
            val r = assertIs<WsMessage.Round>(round)
            assertEquals(2, r.snapshot.round)

            // 3) finished
            bus.publish(MatchEvent.Finished("M1", finishedResult("M1"), Instant.parse("2026-01-01T00:05:00Z")))
            val fin = receiveDeserialized<WsMessage>()
            val f = assertIs<WsMessage.Finished>(fin)
            assertEquals("M1", f.matchId)
            assertNotNull(f.result)
        }
    }

    @Test
    fun `WebSocket closes if match unknown`() = testApplication {
        val (_, archive, broadcaster) = setupArenaState()
        application { webApp(archive = archive, broadcaster = broadcaster) }

        val client = createWsClient()
        client.webSocket("/api/matches/unknown/live") {
            // сервер должен отправить close и завершить
            val reason = closeReason.await()
            assertNotNull(reason)
        }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createJsonClient() = createClient {
        install(ClientContentNegotiation) { json(json) }
    }

    private fun io.ktor.server.testing.ApplicationTestBuilder.createWsClient() = createClient {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    private fun setupArenaState(): Triple<InMemoryMatchEventBus, InMemoryMatchArchive, MatchLiveBroadcaster> {
        val bus = InMemoryMatchEventBus()
        val archive = InMemoryMatchArchive().also { it.subscribeTo(bus) }
        val broadcaster = MatchLiveBroadcaster(archive).also { bus.register(it) }
        return Triple(bus, archive, broadcaster)
    }

    private fun startedEvent(id: String, time: Instant) = MatchEvent.Started(
        matchId = id, gameId = "collector", players = listOf("a", "b"),
        width = 5, height = 5, maxRounds = 5, startedAt = time,
        initialSnapshot = snapshot(round = 0)
    )

    private fun snapshot(round: Int) = MatchSnapshot(
        round = round, width = 5, height = 5, maxRounds = 5,
        bots = emptyList(), items = emptyList()
    )

    private fun finishedResult(id: String) = MatchResult(
        matchId = id, timestamp = Instant.now(), config = GameConfig(),
        finalScores = mapOf("a" to 1, "b" to 0), winner = "a", rounds = 5
    )
}
