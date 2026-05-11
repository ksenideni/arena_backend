package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset

/** Всегда движется вправо (+1, 0). На торическом поле обходит всю строку. */
class HorizontalBotClient(
    name: String = "Horizontal",
    login: String? = null,
    password: String? = null
) : BotClient(name, login = login, password = password) {
    override fun decideMove(state: BotView): Offset = Offset(1, 0)
}

fun main(args: Array<String>) {
    val name = args.getOrNull(0) ?: "Horizontal"
    HorizontalBotClient(name, login = System.getenv("BOT_LOGIN"), password = System.getenv("BOT_PASSWORD")).run()
}
