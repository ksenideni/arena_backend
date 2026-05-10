package ru.mirea.robocompetition.web

import ru.mirea.robocompetition.archive.MatchArchive
import ru.mirea.robocompetition.archive.MatchDetail
import ru.mirea.robocompetition.archive.MatchStatus
import ru.mirea.robocompetition.archive.MatchSummary
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchEventBus
import ru.mirea.robocompetition.events.MatchEventSubscriber
import ru.mirea.robocompetition.events.MatchSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * Test-only фейк архива. Не в продакшн-коде — нужен лишь чтобы тестировать
 * Ktor-роутинг без поднятия Postgres.
 */
internal class FakeMatchArchive : MatchArchive, MatchEventSubscriber {
    private val matches = ConcurrentHashMap<String, Entry>()

    fun subscribeTo(bus: MatchEventBus): MatchEventBus.Subscription = bus.subscribe(this)

    override fun onEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.Started -> matches[event.matchId] = Entry(
                summary = MatchSummary(
                    matchId = event.matchId,
                    gameId = event.gameId,
                    players = event.players,
                    status = MatchStatus.ACTIVE,
                    startedAt = event.startedAt,
                    finishedAt = null,
                    currentRound = event.initialSnapshot.round,
                    maxRounds = event.maxRounds,
                    winner = null
                ),
                width = event.width,
                height = event.height,
                snapshots = mutableListOf(event.initialSnapshot),
                result = null
            )
            is MatchEvent.Round -> matches.computeIfPresent(event.matchId) { _, e ->
                synchronized(e.snapshots) { e.snapshots.add(event.snapshot) }
                e.copy(summary = e.summary.copy(currentRound = event.snapshot.round))
            }
            is MatchEvent.Finished -> matches.computeIfPresent(event.matchId) { _, e ->
                e.copy(
                    summary = e.summary.copy(
                        status = MatchStatus.FINISHED,
                        finishedAt = event.finishedAt,
                        winner = event.result.winner
                    ),
                    result = event.result
                )
            }
        }
    }

    override fun listMatches(): List<MatchSummary> =
        matches.values.map { it.summary }.sortedByDescending { it.startedAt }

    override fun getDetail(matchId: String): MatchDetail? {
        val e = matches[matchId] ?: return null
        val snapshots = synchronized(e.snapshots) { e.snapshots.toList() }
        return MatchDetail(e.summary, e.width, e.height, snapshots, e.result)
    }

    private data class Entry(
        val summary: MatchSummary,
        val width: Int,
        val height: Int,
        val snapshots: MutableList<MatchSnapshot>,
        val result: ru.mirea.robocompetition.model.MatchResult?
    )
}
