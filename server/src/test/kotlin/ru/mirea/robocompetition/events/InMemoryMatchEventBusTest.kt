package ru.mirea.robocompetition.events

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryMatchEventBusTest {

    @Test
    fun `multiple subscribers each receive published event`() {
        val bus = InMemoryMatchEventBus()
        val a = mutableListOf<MatchEvent>()
        val b = mutableListOf<MatchEvent>()
        bus.subscribe(MatchEventSubscriber { a.add(it) })
        bus.subscribe(MatchEventSubscriber { b.add(it) })

        val event: MatchEvent = MatchEvent.Round("m1", snapshotOf(round = 3))
        bus.publish(event)

        assertEquals(listOf(event), a)
        assertEquals(listOf(event), b)
    }

    @Test
    fun `subscription cancel stops further events`() {
        val bus = InMemoryMatchEventBus()
        val received = mutableListOf<MatchEvent>()
        val sub = bus.subscribe(MatchEventSubscriber { received.add(it) })

        bus.publish(MatchEvent.Round("m", snapshotOf(round = 0)))
        sub.cancel()
        bus.publish(MatchEvent.Round("m", snapshotOf(round = 1)))

        assertEquals(1, received.size)
    }

    @Test
    fun `failing subscriber does not stop others`() {
        val bus = InMemoryMatchEventBus()
        val received = mutableListOf<MatchEvent>()
        bus.subscribe(MatchEventSubscriber { error("boom") })
        bus.subscribe(MatchEventSubscriber { received.add(it) })

        bus.publish(MatchEvent.Finished("m", finishedResult(), Instant.now()))
        assertTrue(received.isNotEmpty())
    }

    private fun snapshotOf(round: Int) = MatchSnapshot(
        round = round,
        width = 10,
        height = 10,
        maxRounds = 30,
        bots = emptyList(),
        items = emptyList()
    )

    private fun finishedResult() = MatchResult(
        matchId = "m",
        timestamp = Instant.now(),
        config = GameConfig(),
        finalScores = emptyMap(),
        winner = null,
        rounds = 30
    )
}
