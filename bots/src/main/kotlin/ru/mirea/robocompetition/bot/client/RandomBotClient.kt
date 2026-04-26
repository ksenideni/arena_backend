package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset
import kotlin.random.Random

/** Каждый ход выбирает случайный из пяти стандартных векторов. */
class RandomBotClient(
    name: String = "Random",
    private val random: Random = Random.Default
) : BotClient(name) {

    private val moves = listOf(
        Offset(0, -1), Offset(0, 1), Offset(-1, 0), Offset(1, 0), Offset(0, 0)
    )

    override fun decideMove(state: BotView): Offset = moves.random(random)
}

fun main(args: Array<String>) {
    val name = args.getOrNull(0) ?: "Random"
    RandomBotClient(name).run()
}
