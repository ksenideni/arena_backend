package ru.mirea.robocompetition.web

import ru.mirea.robocompetition.archive.LeaderboardRow
import ru.mirea.robocompetition.archive.MatchArchive
import ru.mirea.robocompetition.archive.MatchDetail
import ru.mirea.robocompetition.archive.MatchStats
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

/** Test-only stub статистики: считает агрегаты в памяти по завершённым матчам. */
internal class FakeMatchStats(private val archive: FakeMatchArchive) : MatchStats {
    override fun leaderboard(): List<LeaderboardRow> {
        val finished = archive.listMatches().filter { it.status == MatchStatus.FINISHED }
            .mapNotNull { summary -> archive.getDetail(summary.matchId)?.let { summary to it } }

        data class Key(val player: String, val gameId: String)
        val acc = mutableMapOf<Key, MutableList<Pair<MatchSummary, Int>>>()

        for ((summary, detail) in finished) {
            val scores = detail.result?.finalScores ?: continue
            for (player in summary.players) {
                acc.getOrPut(Key(player, summary.gameId)) { mutableListOf() } +=
                    summary to (scores[player] ?: 0)
            }
        }
        return acc.map { (key, rows) ->
            val matches = rows.size
            val wins = rows.count { it.first.winner == key.player }
            val draws = rows.count { it.first.winner == null }
            val total = rows.sumOf { it.second }
            LeaderboardRow(
                player = key.player,
                gameId = key.gameId,
                matches = matches,
                wins = wins,
                draws = draws,
                totalScore = total,
                avgScore = if (matches == 0) 0.0 else total.toDouble() / matches,
                winRate = if (matches == 0) 0.0 else wins.toDouble() / matches
            )
        }.sortedWith(
            compareByDescending<LeaderboardRow> { it.wins }
                .thenByDescending { it.totalScore }
                .thenByDescending { it.matches }
                .thenBy { it.player }
        )
    }
}
