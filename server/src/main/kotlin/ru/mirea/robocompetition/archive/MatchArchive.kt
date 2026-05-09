package ru.mirea.robocompetition.archive

import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant

/**
 * Архив матчей: хранит метаданные и полную историю снимков по каждому матчу.
 *
 * В отличие от старого MatchRepository (хранил только финальный результат),
 * archive позволяет реализовать реплей и догон с начала матча для зрителей,
 * подключившихся посреди игры.
 *
 * Реализация по умолчанию — InMemoryMatchArchive. Для постоянного хранения
 * можно добавить SqliteMatchArchive — контракт остаётся тем же.
 */
interface MatchArchive {

    fun listMatches(): List<MatchSummary>

    fun getDetail(matchId: String): MatchDetail?
}

enum class MatchStatus { ACTIVE, FINISHED }

data class MatchSummary(
    val matchId: String,
    val gameId: String,
    val players: List<String>,
    val status: MatchStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val currentRound: Int,
    val maxRounds: Int,
    val winner: String?
)

data class MatchDetail(
    val summary: MatchSummary,
    val width: Int,
    val height: Int,
    val snapshots: List<MatchSnapshot>,
    val result: MatchResult?
)
