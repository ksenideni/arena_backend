package ru.mirea.robocompetition.web

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import ru.mirea.robocompetition.archive.MatchArchive
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchEventBus
import ru.mirea.robocompetition.events.MatchEventSubscriber
import ru.mirea.robocompetition.web.dto.WsMessage
import ru.mirea.robocompetition.web.dto.toDto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Стримит события матча подключённым WebSocket-клиентам.
 *
 * Поведение:
 *  - Подписан на [MatchEventBus] (синхронный коллбек). Внутри только non-suspend
 *    операции (Channel.trySend) — не блокирует игровой цикл.
 *  - Каждый клиент получает свой Channel<WsMessage> (бэкпрешур через DROP_OLDEST,
 *    чтобы медленный клиент не пузырил OOM).
 *  - При подключении [connect] возвращает History (читается из archive), а в Channel
 *    далее идут только Round/Finished. Возможные дубликаты по round — frontend
 *    дедуплицирует по полю snapshot.round.
 *
 * Подписчик регистрируется на шине ПОСЛЕ архива (в Main), чтобы события сначала
 * попадали в архив и были видны при подключении нового зрителя.
 */
class MatchLiveBroadcaster(
    private val archive: MatchArchive
) : MatchEventSubscriber {

    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<Channel<WsMessage>>>()

    /**
     * Зарегистрировать подключившегося зрителя для [matchId].
     * Возвращает данные истории и канал для последующих live-событий, либо null
     * если матч с таким id не найден в архиве.
     */
    fun connect(matchId: String): Connection? {
        val detail = archive.getDetail(matchId) ?: return null

        val history = WsMessage.History(
            matchId = matchId,
            summary = detail.summary.toDto(),
            width = detail.width,
            height = detail.height,
            snapshots = detail.snapshots.map { it.toDto() },
            result = detail.result?.toDto()
        )

        val channel = Channel<WsMessage>(
            capacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        sessions.computeIfAbsent(matchId) { CopyOnWriteArrayList() }.add(channel)

        return Connection(history = history, channel = channel) {
            sessions[matchId]?.remove(channel)
            channel.close()
        }
    }

    override fun onEvent(event: MatchEvent) {
        val list = sessions[event.matchId] ?: return
        val msg: WsMessage = when (event) {
            is MatchEvent.Started -> return // зрители получают это через History
            is MatchEvent.Round -> WsMessage.Round(matchId = event.matchId, snapshot = event.snapshot.toDto())
            is MatchEvent.Finished -> WsMessage.Finished(
                matchId = event.matchId,
                result = event.result.toDto(),
                finishedAt = event.finishedAt.toString()
            )
        }
        for (ch in list) ch.trySend(msg)
    }

    data class Connection(
        val history: WsMessage.History,
        val channel: Channel<WsMessage>,
        val close: () -> Unit
    )
}

/** Зарегистрировать broadcaster как подписчика шины. Удобный helper для wiring. */
fun MatchEventBus.register(broadcaster: MatchLiveBroadcaster): MatchEventBus.Subscription =
    subscribe(broadcaster)
