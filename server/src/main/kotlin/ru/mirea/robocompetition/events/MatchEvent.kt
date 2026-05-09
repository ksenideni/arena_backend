package ru.mirea.robocompetition.events

import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant

/**
 * События жизненного цикла матча для зрителей и архива.
 *
 * Game-agnostic — не содержит ссылок на конкретные игровые модели.
 * Это позволяет одному обработчику (архив, веб-броадкастер) работать
 * с любыми играми, реализующими GameScenario.
 */
sealed class MatchEvent {

    abstract val matchId: String

    data class Started(
        override val matchId: String,
        val gameId: String,
        val players: List<String>,
        val width: Int,
        val height: Int,
        val maxRounds: Int,
        val startedAt: Instant,
        val initialSnapshot: MatchSnapshot
    ) : MatchEvent()

    data class Round(
        override val matchId: String,
        val snapshot: MatchSnapshot
    ) : MatchEvent()

    data class Finished(
        override val matchId: String,
        val result: MatchResult,
        val finishedAt: Instant
    ) : MatchEvent()
}
