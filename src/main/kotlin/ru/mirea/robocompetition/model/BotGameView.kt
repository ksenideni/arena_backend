package ru.mirea.robocompetition.model

import ru.mirea.robocompetition.config.GameConfig

/**
 * Read-only срез состояния игры, передаваемый боту на каждом ходу.
 *
 * Отдельный тип (не сам GameState) — граница сериализации для будущего
 * сетевого протокола: только BotGameView отправляется удалённым ботам,
 * внутренности движка остаются неизменными.
 */
data class BotGameView(
    val myId: Int,
    val myPosition: Position,
    val myScore: Int,
    val allBots: List<BotState>,
    val coins: Set<Position>,
    val round: Int,
    val config: GameConfig
)
