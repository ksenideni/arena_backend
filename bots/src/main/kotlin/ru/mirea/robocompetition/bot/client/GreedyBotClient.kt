package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset
import ru.mirea.robocompetition.model.Position
import kotlin.math.abs

/**
 * Жадный бот: каждый ход движется к ближайшей монете.
 *
 * Алгоритм:
 *  1. Найти монету с минимальным торическим расстоянием Манхэттена.
 *  2. Из пяти кандидатов отфильтровать клетки, занятые другими ботами.
 *  3. Выбрать ход, который минимизирует расстояние до цели.
 */
class GreedyBotClient(
    name: String = "Greedy",
    login: String? = null,
    password: String? = null
) : BotClient(name, login = login, password = password) {

    private val moves = listOf(
        Offset(0, -1), Offset(0, 1), Offset(-1, 0), Offset(1, 0), Offset(0, 0)
    )

    override fun decideMove(state: BotView): Offset {
        if (state.coins.isEmpty()) return Offset(0, 0)

        val target = state.coins.minBy { coin ->
            toroidalDist(state.myPosition, coin, state.width, state.height)
        }

        val occupied = state.bots
            .filter { it.id != state.myId }
            .map { it.position }
            .toSet()

        return moves
            .filter { offset -> applyOffset(state.myPosition, offset, state.width, state.height) !in occupied }
            .minBy { offset ->
                toroidalDist(
                    applyOffset(state.myPosition, offset, state.width, state.height),
                    target,
                    state.width,
                    state.height
                )
            }
    }

    private fun applyOffset(pos: Position, offset: Offset, width: Int, height: Int) =
        Position(
            x = (pos.x + offset.dx).mod(width),
            y = (pos.y + offset.dy).mod(height)
        )

    // Торическое расстояние Манхэттена: учитывает перенос через края поля
    private fun toroidalDist(a: Position, b: Position, width: Int, height: Int): Int {
        val dx = minOf(abs(a.x - b.x), width - abs(a.x - b.x))
        val dy = minOf(abs(a.y - b.y), height - abs(a.y - b.y))
        return dx + dy
    }
}

fun main(args: Array<String>) {
    val name = args.getOrNull(0) ?: "Greedy"
    GreedyBotClient(
        name,
        login = System.getenv("BOT_LOGIN"),
        password = System.getenv("BOT_PASSWORD")
    ).run()
}
