package ru.mirea.robocompetition.archive

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.events.InMemoryMatchEventBus
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryMatchArchiveTest {

    @Test
    fun `accumulates snapshots and marks match finished`() {
        val bus = InMemoryMatchEventBus()
        val archive = InMemoryMatchArchive()
        archive.subscribeTo(bus)

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

        val activeSummary = archive.listMatches().single()
        assertEquals("M1", activeSummary.matchId)
        assertEquals(MatchStatus.ACTIVE, activeSummary.status)
        assertEquals(2, activeSummary.currentRound)
        assertNull(activeSummary.winner)
        assertNull(activeSummary.finishedAt)

        val activeDetail = archive.getDetail("M1")!!
        assertEquals(3, activeDetail.snapshots.size) // initial + 2 rounds
        assertEquals(listOf(0, 1, 2), activeDetail.snapshots.map { it.round })

        val finishTime = Instant.parse("2026-01-01T00:01:00Z")
        val result = MatchResult(
            matchId = "M1", timestamp = finishTime, config = GameConfig(),
            finalScores = mapOf("a" to 3, "b" to 1), winner = "a", rounds = 5
        )
        bus.publish(MatchEvent.Finished("M1", result, finishTime))

        val finishedSummary = archive.listMatches().single()
        assertEquals(MatchStatus.FINISHED, finishedSummary.status)
        assertEquals("a", finishedSummary.winner)
        assertEquals(finishTime, finishedSummary.finishedAt)

        val finishedDetail = archive.getDetail("M1")!!
        assertEquals(result, finishedDetail.result)
    }

    @Test
    fun `getDetail returns null for unknown match`() {
        val archive = InMemoryMatchArchive()
        assertNull(archive.getDetail("nope"))
    }

    @Test
    fun `listMatches returns matches sorted by startedAt descending`() {
        val bus = InMemoryMatchEventBus()
        val archive = InMemoryMatchArchive()
        archive.subscribeTo(bus)

        bus.publish(startedEvent("first", Instant.parse("2026-01-01T00:00:00Z")))
        bus.publish(startedEvent("second", Instant.parse("2026-01-01T01:00:00Z")))
        bus.publish(startedEvent("third", Instant.parse("2026-01-01T00:30:00Z")))

        val ids = archive.listMatches().map { it.matchId }
        assertEquals(listOf("second", "third", "first"), ids)
    }

    @Test
    fun `events for unknown match id are ignored after Started missed`() {
        val bus = InMemoryMatchEventBus()
        val archive = InMemoryMatchArchive()
        archive.subscribeTo(bus)

        // Started для другого матча
        bus.publish(startedEvent("M1", Instant.now()))
        // Round для матча, которого нет — должен быть проигнорирован
        bus.publish(MatchEvent.Round("M2", snapshot(round = 1)))

        assertEquals(1, archive.listMatches().size)
        assertTrue(archive.listMatches().single().matchId == "M1")
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
