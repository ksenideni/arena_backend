package ru.mirea.robocompetition.events

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.games.coincollector.CoinCollectorScenario
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BroadcastingMatchListenerTest {

    @Test
    fun `publishes Started, Round, Finished in order with correct ids`() {
        val bus = InMemoryMatchEventBus()
        val received = mutableListOf<MatchEvent>()
        bus.subscribe { received.add(it) }

        val scenario = CoinCollectorScenario(
            config = GameConfig(width = 5, height = 5, coinCount = 1, maxRounds = 1, stepDelayMs = 0),
            random = Random(42)
        )
        val players = listOf("a", "b", "c", "d")
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val listener = BroadcastingMatchListener(scenario, players, bus, clock = { now })

        val state0 = scenario.init(players)
        listener.onMatchStarted("MID", state0)

        val state1 = scenario.applyMoves(state0, players.associateWith { scenario.defaultMove })
        listener.onRoundComplete("MID", state1)

        val result = MatchResult(
            matchId = "MID", timestamp = now, config = state0.config,
            finalScores = players.associateWith { 0 }, winner = null, rounds = 1
        )
        listener.onMatchOver("MID", result)

        assertEquals(3, received.size)
        val started = assertIs<MatchEvent.Started>(received[0])
        assertEquals("MID", started.matchId)
        assertEquals(CoinCollectorScenario.ID, started.gameId)
        assertEquals(players, started.players)
        assertEquals(5, started.width)
        assertEquals(5, started.height)
        assertEquals(1, started.maxRounds)
        assertEquals(now, started.startedAt)
        assertNotNull(started.initialSnapshot)
        assertEquals(0, started.initialSnapshot.round)

        val round = assertIs<MatchEvent.Round>(received[1])
        assertEquals("MID", round.matchId)
        assertEquals(1, round.snapshot.round)

        val finished = assertIs<MatchEvent.Finished>(received[2])
        assertEquals("MID", finished.matchId)
        assertEquals(result, finished.result)
        assertEquals(now, finished.finishedAt)
    }
}
