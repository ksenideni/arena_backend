package ru.mirea.robocompetition.lobby

import ru.mirea.robocompetition.network.Session

/**
 * Очередь подключившихся ботов, ожидающих матча.
 *
 * Боты группируются по идентификатору игры. Когда в очереди по конкретной
 * игре набирается [matchSize] участников, очередь забирает их и вызывает
 * [onMatchReady], который запускает матч в отдельном потоке.
 *
 * Имена ботов в пределах одной очереди уникальны: повторный register с уже
 * занятым именем отклоняется (см. результат [register]).
 */
class Lobby(
    private val matchSize: Int,
    private val onMatchReady: (game: String, bots: List<Pair<String, Session>>) -> Unit
) {
    private val pending = mutableMapOf<String, MutableList<Pair<String, Session>>>()
    private val lock = Any()

    /**
     * @return true если бот добавлен; false если имя уже занято в очереди этой игры.
     */
    fun register(game: String, name: String, session: Session): Boolean {
        var matchBots: List<Pair<String, Session>>? = null
        synchronized(lock) {
            val list = pending.getOrPut(game) { mutableListOf() }
            if (list.any { it.first == name }) return false
            list.add(name to session)
            if (list.size >= matchSize) {
                matchBots = list.take(matchSize).toList()
                repeat(matchSize) { list.removeAt(0) }
            }
        }
        matchBots?.let { bots ->
            Thread({ onMatchReady(game, bots) }, "match-${game}").start()
        }
        return true
    }
}
