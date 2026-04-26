package ru.mirea.robocompetition.match

import ru.mirea.robocompetition.model.MatchResult
import ru.mirea.robocompetition.network.Message
import ru.mirea.robocompetition.network.Session
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Гоняет игровой цикл одного матча по фиксированному сценарию.
 *
 * Не зависит от конкретной игры — работает через GameScenario&lt;S, M&gt;.
 * Параллельно собирает ходы со всех ботов через Thread.join, так что
 * один медленный бот не задерживает остальных.
 *
 * Поведение при сбоях:
 *  - SocketTimeoutException: бот пропускает раунд (ставится defaultMove), остаётся в матче
 *  - IOException (соединение упало): бот выкидывается из матча, остальные продолжают
 *  - Когда выкинуты все боты — матч завершается досрочно
 */
class MatchRunner<S, M>(
    private val scenario: GameScenario<S, M>,
    private val sessions: Map<String, Session>,
    private val moveTimeoutMs: Int = 1000,
    private val listeners: List<MatchListener<S>> = emptyList(),
    private val matchId: String = UUID.randomUUID().toString().substring(0, 8)
) {
    private val active = sessions.keys.toMutableSet()

    fun run(): MatchResult {
        var state = scenario.init(sessions.keys.toList())

        sendMatchStartedToAll(state)
        listeners.forEach { it.onMatchStarted(state) }

        while (active.isNotEmpty() && !scenario.isFinished(state)) {
            sendUpdateToAll(state)
            val moves = collectMoves()
            state = scenario.applyMoves(state, moves)
            listeners.forEach { it.onRoundComplete(state) }
        }

        val result = scenario.getResult(state, matchId)
        sendMatchOverToAll(result)
        listeners.forEach { it.onMatchOver(result) }
        sessions.values.forEach { it.close() }
        return result
    }

    private fun sendMatchStartedToAll(state: S) {
        for (name in active.toList()) {
            val session = sessions[name] ?: continue
            try {
                session.write(scenario.formatMatchStarted(state, matchId, name))
            } catch (e: IOException) {
                deactivate(name, "ошибка при отправке match_started: ${e.message}")
            }
        }
    }

    private fun sendUpdateToAll(state: S) {
        for (name in active.toList()) {
            val session = sessions[name] ?: continue
            try {
                session.write(scenario.formatUpdate(state, name))
            } catch (e: IOException) {
                deactivate(name, "ошибка при отправке update: ${e.message}")
            }
        }
    }

    private fun collectMoves(): Map<String, M> {
        val results = ConcurrentHashMap<String, MoveOutcome<M>>()
        val threads = active.toList().mapNotNull { name ->
            val session = sessions[name] ?: return@mapNotNull null
            Thread {
                results[name] = readMoveSafely(session)
            }.also { it.start() }
        }
        threads.forEach { it.join() }

        val moves = mutableMapOf<String, M>()
        for ((name, outcome) in results) {
            when (outcome) {
                is MoveOutcome.Ok -> moves[name] = outcome.move ?: scenario.defaultMove
                is MoveOutcome.Timeout -> moves[name] = scenario.defaultMove
                is MoveOutcome.Broken -> deactivate(name, "соединение упало: ${outcome.reason}")
            }
        }
        return moves
    }

    private fun readMoveSafely(session: Session): MoveOutcome<M> = try {
        session.setReadTimeout(moveTimeoutMs)
        val msg = session.read()
        MoveOutcome.Ok(msg?.let { scenario.parseMove(it) })
    } catch (e: SocketTimeoutException) {
        MoveOutcome.Timeout
    } catch (e: IOException) {
        MoveOutcome.Broken(e.message ?: "IOException")
    }

    private fun sendMatchOverToAll(result: MatchResult) {
        val message = formatMatchOver(result)
        for ((_, session) in sessions) {
            try {
                if (session.isOpen) session.write(message)
            } catch (_: IOException) {
                // соединение уже мёртвое — ничего не делаем
            }
        }
    }

    private fun deactivate(name: String, reason: String) {
        active.remove(name)
        sessions[name]?.close()
        println("[match $matchId] бот '$name' выбыл: $reason")
    }

    private fun formatMatchOver(result: MatchResult): Message = Message.build("match_over") {
        if (result.winner != null) add("winner", result.winner)
        else add("tie")
        for ((name, score) in result.finalScores) {
            add("score", name, score.toString())
        }
        add("rounds", result.rounds.toString())
    }

    private sealed class MoveOutcome<out M> {
        data class Ok<M>(val move: M?) : MoveOutcome<M>()
        data object Timeout : MoveOutcome<Nothing>()
        data class Broken(val reason: String) : MoveOutcome<Nothing>()
    }
}
