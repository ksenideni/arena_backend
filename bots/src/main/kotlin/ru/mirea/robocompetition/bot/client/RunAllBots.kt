package ru.mirea.robocompetition.bot.client

/**
 * Удобный лаунчер: одной командой поднимает четырёх ботов в одном процессе.
 *
 * Каждый бот живёт в отдельном потоке и независимо подключается к серверу,
 * как если бы это были разные процессы. Удобно для локального демо: запустил
 * сервер в одном терминале, лаунчер в другом — и через секунду идёт матч.
 */
fun main() {
    val bots: List<BotClient> = listOf(
        HorizontalBotClient("Alice"),
        VerticalBotClient("Bob"),
        RandomBotClient("Charlie"),
        GreedyBotClient("Diana")
    )

    val threads = bots.map { bot ->
        Thread({ bot.run() }, "bot-${bot.name}").also { it.start() }
    }
    threads.forEach { it.join() }
}
