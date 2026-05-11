package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset

/** Всегда движется вниз (0, +1). На торическом поле обходит весь столбец. */
class VerticalBotClient(
    name: String = "Vertical",
    login: String? = null,
    password: String? = null
) : BotClient(name, login = login, password = password) {
    override fun decideMove(state: BotView): Offset = Offset(0, 1)
}

fun main(args: Array<String>) {
    val name = args.getOrNull(0) ?: "Vertical"
    VerticalBotClient(name, login = System.getenv("BOT_LOGIN"), password = System.getenv("BOT_PASSWORD")).run()
}
