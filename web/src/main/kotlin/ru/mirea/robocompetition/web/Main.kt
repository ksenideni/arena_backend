package ru.mirea.robocompetition.web

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ru.mirea.robocompetition.archive.InMemoryMatchArchive
import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.events.InMemoryMatchEventBus
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchEventSubscriber
import ru.mirea.robocompetition.lobby.Server

/**
 * Точка входа всей платформы.
 *
 * Поднимает:
 *  - HTTP/WS-сервер Ktor на порту 8080 (REST для фронта + WS для лайв-просмотра)
 *  - TCP-сервер ботов на порту 9000 (через :server.Server)
 *
 * Шина событий [InMemoryMatchEventBus] связывает их: Server публикует события
 * матча, архив их сохраняет, broadcaster раздаёт WebSocket-зрителям.
 *
 * Фронт деплоится отдельно (не входит в этот jar). Origins подключающихся
 * клиентов задаются через CORS_ALLOWED_ORIGINS (CSV).
 */
fun main() {
    val gameConfig = GameConfig(
        width = 10,
        height = 10,
        coinCount = 15,
        maxRounds = 30,
        stepDelayMs = 0
    )

    val bus = InMemoryMatchEventBus()
    val archive = InMemoryMatchArchive().also { it.subscribeTo(bus) }
    val broadcaster = MatchLiveBroadcaster(archive).also { bus.register(it) }

    // Опционально: искусственный delay между раундами для визуального демо
    // (например, чтобы зритель успел увидеть "live"-матч в браузере).
    val slowMs = System.getenv("MATCH_STEP_MS")?.toLongOrNull() ?: 0L
    if (slowMs > 0) {
        bus.subscribe(MatchEventSubscriber {
            if (it is MatchEvent.Round) Thread.sleep(slowMs)
        })
        println("Demo mode: задержка $slowMs мс между раундами")
    }

    // HTTP/WS — Ktor
    val httpPort = System.getenv("HTTP_PORT")?.toIntOrNull() ?: 8080
    val corsOrigins = System.getenv("CORS_ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("http://localhost:5173", "http://localhost:4173")

    embeddedServer(Netty, port = httpPort) {
        webApp(archive = archive, broadcaster = broadcaster, corsAllowedOrigins = corsOrigins)
    }.start(wait = false)
    println("HTTP/WS на порту $httpPort (CORS: $corsOrigins)")

    // TCP — боты. Console renderer выключен, события идут только в шину.
    val tcpPort = System.getenv("TCP_PORT")?.toIntOrNull() ?: Server.DEFAULT_PORT
    Server(
        port = tcpPort,
        gameConfig = gameConfig,
        bus = bus,
        enableConsoleRenderer = false
    ).start()
}
