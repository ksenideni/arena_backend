package ru.mirea.robocompetition.archive

import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchEventBus
import ru.mirea.robocompetition.events.MatchEventSubscriber
import ru.mirea.robocompetition.events.MatchSnapshot
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory архив матчей. Подписывается на [MatchEventBus] и накапливает
 * историю снимков по каждому матчу.
 *
 * Чтения консистентны на уровне отдельной записи — ConcurrentHashMap +
 * атомарные операции через .compute() / .computeIfPresent(). Для тяжёлой
 * нагрузки достаточно: запись событий редкая, чтения параллельные.
 */
class InMemoryMatchArchive : MatchArchive, MatchEventSubscriber {

    private val matches = ConcurrentHashMap<String, MatchEntry>()

    /** Подписаться на шину; возвращает Subscription для отмены. */
    fun subscribeTo(bus: MatchEventBus): MatchEventBus.Subscription = bus.subscribe(this)

    override fun onEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.Started -> matches[event.matchId] = MatchEntry(
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

            is MatchEvent.Round -> matches.computeIfPresent(event.matchId) { _, entry ->
                synchronized(entry.snapshots) { entry.snapshots.add(event.snapshot) }
                entry.copy(summary = entry.summary.copy(currentRound = event.snapshot.round))
            }

            is MatchEvent.Finished -> matches.computeIfPresent(event.matchId) { _, entry ->
                entry.copy(
                    summary = entry.summary.copy(
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
        val entry = matches[matchId] ?: return null
        // снимки копируем, чтобы читатель не видел дальнейших мутаций
        val snapshotsCopy = synchronized(entry.snapshots) { entry.snapshots.toList() }
        return MatchDetail(
            summary = entry.summary,
            width = entry.width,
            height = entry.height,
            snapshots = snapshotsCopy,
            result = entry.result
        )
    }

    private data class MatchEntry(
        val summary: MatchSummary,
        val width: Int,
        val height: Int,
        val snapshots: MutableList<MatchSnapshot>,
        val result: ru.mirea.robocompetition.model.MatchResult?
    )
}
