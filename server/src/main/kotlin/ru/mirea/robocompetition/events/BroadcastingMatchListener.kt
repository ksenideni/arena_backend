package ru.mirea.robocompetition.events

import ru.mirea.robocompetition.match.GameScenario
import ru.mirea.robocompetition.match.MatchListener
import ru.mirea.robocompetition.model.MatchResult
import java.time.Instant

/**
 * MatchListener, публикующий события матча в [MatchEventBus].
 *
 * Получает gameId и список игроков из конструктора (Server знает их в момент создания матча).
 * Преобразует state каждого раунда в [MatchSnapshot] через [GameScenario.toSnapshot] —
 * шина оперирует только game-agnostic снимками.
 */
class BroadcastingMatchListener<S, M>(
    private val scenario: GameScenario<S, M>,
    private val players: List<String>,
    private val bus: MatchEventBus,
    private val clock: () -> Instant = Instant::now
) : MatchListener<S> {

    override fun onMatchStarted(matchId: String, state: S) {
        val snapshot = scenario.toSnapshot(state)
        bus.publish(
            MatchEvent.Started(
                matchId = matchId,
                gameId = scenario.id,
                players = players,
                width = snapshot.width,
                height = snapshot.height,
                maxRounds = snapshot.maxRounds,
                startedAt = clock(),
                initialSnapshot = snapshot
            )
        )
    }

    override fun onRoundComplete(matchId: String, state: S) {
        bus.publish(MatchEvent.Round(matchId = matchId, snapshot = scenario.toSnapshot(state)))
    }

    override fun onMatchOver(matchId: String, result: MatchResult) {
        bus.publish(MatchEvent.Finished(matchId = matchId, result = result, finishedAt = clock()))
    }
}
