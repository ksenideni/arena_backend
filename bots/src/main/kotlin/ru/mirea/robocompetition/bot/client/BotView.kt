package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Position

/**
 * Картина мира с точки зрения бота на конкретном раунде.
 *
 * Это то что бот получает в [BotClient.decideMove]. Сформировано из
 * сообщения update + статичных параметров из match_started.
 */
data class BotView(
    val myId: Int,
    val numBots: Int,
    val width: Int,
    val height: Int,
    val maxRounds: Int,
    val round: Int,
    val myPosition: Position,
    val myScore: Int,
    val bots: List<BotInfo>,
    val coins: Set<Position>
) {
    data class BotInfo(val id: Int, val position: Position, val score: Int)
}
