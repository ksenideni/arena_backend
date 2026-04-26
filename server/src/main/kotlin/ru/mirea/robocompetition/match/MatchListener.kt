package ru.mirea.robocompetition.match

import ru.mirea.robocompetition.model.MatchResult

/**
 * Подписчик на события матча — для рендеринга, логов, будущей трансляции в браузер.
 *
 * Все методы имеют пустую реализацию по умолчанию, поэтому подписчик
 * переопределяет только то, что ему нужно.
 */
interface MatchListener<S> {
    fun onMatchStarted(state: S) {}
    fun onRoundComplete(state: S) {}
    fun onMatchOver(result: MatchResult) {}
}
