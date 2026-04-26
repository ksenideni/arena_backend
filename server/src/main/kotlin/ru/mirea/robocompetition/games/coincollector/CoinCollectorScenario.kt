package ru.mirea.robocompetition.games.coincollector

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.match.GameScenario
import ru.mirea.robocompetition.model.BotState
import ru.mirea.robocompetition.model.GameState
import ru.mirea.robocompetition.model.MatchResult
import ru.mirea.robocompetition.model.Offset
import ru.mirea.robocompetition.model.Position
import ru.mirea.robocompetition.network.Message
import java.time.Instant
import kotlin.random.Random

/**
 * Реализация GameScenario для игры «Сборщик».
 *
 * Поле торическое (края зацикливаются), боты ходят последовательно по id —
 * каждый бот видит результат хода предыдущих. При столкновении бот стоит на месте.
 * Бот, вставший на клетку с монетой, забирает её.
 */
class CoinCollectorScenario(
    private val config: GameConfig,
    private val random: Random = Random.Default
) : GameScenario<GameState, Offset> {

    override val id: String = "collector"

    override val defaultMove: Offset = Offset(0, 0)

    override fun init(botNames: List<String>): GameState {
        val allCells = (0 until config.width).flatMap { x ->
            (0 until config.height).map { y -> Position(x, y) }
        }.toMutableList()
        allCells.shuffle(random)

        val botStates = botNames.mapIndexed { index, name ->
            BotState(id = index, name = name, position = allCells[index], score = 0)
        }

        val occupied = botStates.map { it.position }.toSet()
        val coinCandidates = allCells.drop(botNames.size).filter { it !in occupied }
        val coins = coinCandidates.take(config.coinCount).toSet()

        return GameState(round = 0, config = config, bots = botStates, coins = coins)
    }

    override fun applyMoves(state: GameState, moves: Map<String, Offset>): GameState {
        val updatedBots = state.bots.toMutableList()
        val remainingCoins = state.coins.toMutableSet()

        for (i in updatedBots.indices) {
            val bot = updatedBots[i]
            val offset = moves[bot.name] ?: defaultMove
            val candidate = resolvePosition(bot.position, offset)

            // Проверка столкновения: занята ли целевая клетка другим ботом?
            val occupied = updatedBots.any { other -> other.id != bot.id && other.position == candidate }
            if (occupied) continue

            val collectedCoin = candidate in remainingCoins
            if (collectedCoin) remainingCoins.remove(candidate)

            updatedBots[i] = bot.copy(
                position = candidate,
                score = if (collectedCoin) bot.score + 1 else bot.score
            )
        }

        return state.copy(bots = updatedBots, coins = remainingCoins, round = state.round + 1)
    }

    override fun isFinished(state: GameState): Boolean = state.round >= config.maxRounds

    override fun getResult(state: GameState, matchId: String): MatchResult {
        val scores = state.bots.associate { it.name to it.score }
        val maxScore = scores.values.maxOrNull() ?: 0
        val leaders = scores.entries.filter { it.value == maxScore }.map { it.key }
        val winner = if (leaders.size == 1) leaders.first() else null
        return MatchResult(
            matchId = matchId,
            timestamp = Instant.now(),
            config = config,
            finalScores = scores,
            winner = winner,
            rounds = state.round
        )
    }

    override fun formatMatchStarted(state: GameState, matchId: String, botName: String): Message {
        val myId = state.bots.first { it.name == botName }.id
        return Message.build("match_started") {
            add("match_id", matchId)
            add("game", id)
            add("my_id", myId.toString())
            add("num_bots", state.bots.size.toString())
            add("width", config.width.toString())
            add("height", config.height.toString())
            add("max_rounds", config.maxRounds.toString())
        }
    }

    override fun formatUpdate(state: GameState, botName: String): Message {
        val me = state.bots.first { it.name == botName }
        return Message.build("update") {
            add("round", state.round.toString())
            add("my_position", me.position.x.toString(), me.position.y.toString())
            add("my_score", me.score.toString())
            for (b in state.bots) {
                add("bot", b.id.toString(), b.position.x.toString(), b.position.y.toString(), b.score.toString())
            }
            for (c in state.coins) {
                add("coin", c.x.toString(), c.y.toString())
            }
        }
    }

    override fun parseMove(message: Message): Offset? {
        if (message.type != "move") return null
        val values = message.firstField("offset")?.values ?: return null
        if (values.size < 2) return null
        val dx = values[0].toIntOrNull() ?: return null
        val dy = values[1].toIntOrNull() ?: return null
        return Offset(dx, dy)
    }

    // Применение смещения на торическом поле; .mod() корректно обрабатывает отрицательные значения
    private fun resolvePosition(current: Position, offset: Offset): Position =
        Position(
            x = (current.x + offset.dx).mod(config.width),
            y = (current.y + offset.dy).mod(config.height)
        )
}
