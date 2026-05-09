package ru.mirea.robocompetition.match

import ru.mirea.robocompetition.model.MatchResult

/**
 * Подписчик на события матча — для рендеринга, логов, трансляции в браузер.
 *
 * Все методы имеют пустую реализацию по умолчанию, поэтому подписчик
 * переопределяет только то, что ему нужно.
 *
 * matchId передаётся во все коллбеки, чтобы один подписчик мог различать матчи.
 */
interface MatchListener<S> {
    fun onMatchStarted(matchId: String, state: S) {}
    fun onRoundComplete(matchId: String, state: S) {}
    fun onMatchOver(matchId: String, result: MatchResult) {}
}
