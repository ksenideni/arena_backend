package ru.mirea.robocompetition

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.lobby.Server

/**
 * Точка входа сервера соревнований.
 *
 * Запускает Server на порту 9000 с фиксированной конфигурацией игры CoinCollector.
 * Боты подключаются по TCP, регистрируются, ждут пока наберётся 4 участника, играют.
 */
fun main() {
    val config = GameConfig(
        width = 10,
        height = 10,
        coinCount = 15,
        maxRounds = 30,
        stepDelayMs = 0
    )
    Server(gameConfig = config).start()
}
