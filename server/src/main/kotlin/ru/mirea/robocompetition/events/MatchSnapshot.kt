package ru.mirea.robocompetition.events

/**
 * Game-agnostic снимок состояния матча в конкретном раунде.
 *
 * Используется для трансляции зрителям и сохранения в архив.
 * Каждая GameScenario знает, как преобразовать своё внутреннее состояние в этот формат.
 *
 * Поле [items] — обобщение для любых статичных объектов на поле (монеты, препятствия,
 * выходы лабиринта и т.п.). Конкретный смысл задаётся [ItemView.type].
 */
data class MatchSnapshot(
    val round: Int,
    val width: Int,
    val height: Int,
    val maxRounds: Int,
    val bots: List<BotView>,
    val items: List<ItemView>
)

data class BotView(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val score: Int,
    val active: Boolean = true
)

data class ItemView(
    val type: String,
    val x: Int,
    val y: Int
)
