package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset

/** Всегда движется вниз (0, +1). На торическом поле обходит весь столбец. */
class VerticalBotClient(name: String = "Vertical") : BotClient(name) {
    override fun decideMove(state: BotView): Offset = Offset(0, 1)
}

fun main(args: Array<String>) {
    val name = args.getOrNull(0) ?: "Vertical"
    VerticalBotClient(name).run()
}
