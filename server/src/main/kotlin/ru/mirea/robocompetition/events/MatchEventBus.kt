package ru.mirea.robocompetition.events

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Шина событий матчей.
 *
 * Издатели (BroadcastingMatchListener) публикуют MatchEvent,
 * подписчики (архив, веб-броадкастер) получают копии.
 *
 * Подписчики должны обрабатывать события быстро — публикация синхронная,
 * блокирующий подписчик задержит игровой цикл. Тяжёлая работа (I/O, рассылка
 * по WS) должна оффлоадиться внутри подписчика.
 */
interface MatchEventBus {
    fun subscribe(subscriber: MatchEventSubscriber): Subscription
    fun publish(event: MatchEvent)

    interface Subscription {
        fun cancel()
    }
}

fun interface MatchEventSubscriber {
    fun onEvent(event: MatchEvent)
}

class InMemoryMatchEventBus : MatchEventBus {

    private val subscribers = CopyOnWriteArrayList<MatchEventSubscriber>()

    override fun subscribe(subscriber: MatchEventSubscriber): MatchEventBus.Subscription {
        subscribers.add(subscriber)
        return object : MatchEventBus.Subscription {
            override fun cancel() { subscribers.remove(subscriber) }
        }
    }

    override fun publish(event: MatchEvent) {
        for (s in subscribers) {
            try {
                s.onEvent(event)
            } catch (e: Exception) {
                System.err.println("MatchEventBus: подписчик упал на ${event::class.simpleName}: ${e.message}")
            }
        }
    }
}
