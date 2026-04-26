package ru.mirea.robocompetition.model

import ru.mirea.robocompetition.config.GameConfig

/**
 * Неизменяемый снимок игрового поля в конкретном раунде.
 *
 * Движок всегда создаёт новый GameState после применения ходов — никогда
 * не мутирует существующий. Это позволяет безопасно передавать его
 * рендереру и хранилищу без defensive copying.
 *
 * [bots] упорядочены по индексу регистрации: bots[0] ходит первым.
 * [coins] — Set для O(1) проверки наличия монеты на позиции.
 */
data class GameState(
    val round: Int,
    val config: GameConfig,
    val bots: List<BotState>,
    val coins: Set<Position>
)
