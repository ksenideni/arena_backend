package ru.mirea.robocompetition.match

import ru.mirea.robocompetition.model.MatchResult
import ru.mirea.robocompetition.network.Message

/**
 * Контракт для игрового сценария — единая точка расширения для новых игр.
 *
 * Параметры типа:
 *  - S: тип состояния игры (например, GameState для CoinCollector)
 *  - M: тип хода бота (например, Offset для CoinCollector)
 *
 * Чтобы добавить новую игру, достаточно реализовать этот интерфейс и
 * зарегистрировать его в Server. Сетевой слой и MatchRunner ничего
 * про конкретную игру не знают.
 */
interface GameScenario<S, M> {

    val id: String

    val defaultMove: M

    fun init(botNames: List<String>): S

    fun applyMoves(state: S, moves: Map<String, M>): S

    fun isFinished(state: S): Boolean

    fun getResult(state: S, matchId: String): MatchResult

    fun formatMatchStarted(state: S, matchId: String, botName: String): Message

    fun formatUpdate(state: S, botName: String): Message

    fun parseMove(message: Message): M?
}
