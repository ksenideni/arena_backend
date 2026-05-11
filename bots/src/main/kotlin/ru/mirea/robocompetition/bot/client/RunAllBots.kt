package ru.mirea.robocompetition.bot.client

import java.io.File

/**
 * Лаунчер для четырёх ботов в одном процессе.
 *
 * Каждый бот регистрируется на сервере под своим аккаунтом. Креды считываются
 * из переменных окружения BOT_<BOTNAME>_LOGIN / BOT_<BOTNAME>_PASSWORD. Если в
 * корне модуля :bots лежит файл .env — он подгружается перед стартом.
 */
fun main() {
    loadDotEnvIfExists()

    val bots: List<BotClient> = listOf(
        HorizontalBotClient("Alice",   login = credLogin("Alice"),   password = credPassword("Alice")),
        VerticalBotClient(  "Bob",     login = credLogin("Bob"),     password = credPassword("Bob")),
        RandomBotClient(name = "Charlie", login = credLogin("Charlie"), password = credPassword("Charlie")),
        GreedyBotClient(    "Diana",   login = credLogin("Diana"),   password = credPassword("Diana"))
    )

    val threads = bots.map { bot ->
        Thread({ bot.run() }, "bot-${bot.name}").also { it.start() }
    }
    threads.forEach { it.join() }
}

private fun credLogin(botName: String): String =
    envOrNull("BOT_${botName.uppercase()}_LOGIN") ?: botName.lowercase()

private fun credPassword(botName: String): String =
    envOrNull("BOT_${botName.uppercase()}_PASSWORD") ?: "${botName.lowercase()}123"

private fun envOrNull(key: String): String? =
    System.getProperty(key) ?: System.getenv(key)

/**
 * Минимальная поддержка .env: KEY=VALUE построчно, # — комментарии. Значения
 * кладём в System Properties, чтобы они перебивали отсутствующие env.
 */
private fun loadDotEnvIfExists() {
    val candidates = listOf(File(".env"), File("bots/.env"))
    val file = candidates.firstOrNull { it.isFile } ?: return
    file.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val eq = line.indexOf('=')
        if (eq <= 0) return@forEach
        val key = line.substring(0, eq).trim()
        val value = line.substring(eq + 1).trim().trim('"')
        if (System.getProperty(key) == null && System.getenv(key) == null) {
            System.setProperty(key, value)
        }
    }
    println(".env подгружен из ${file.path}")
}
