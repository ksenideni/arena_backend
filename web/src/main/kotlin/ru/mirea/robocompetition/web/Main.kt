package ru.mirea.robocompetition.web

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ru.mirea.robocompetition.archive.Database
import ru.mirea.robocompetition.archive.PostgresArchiveWriter
import ru.mirea.robocompetition.archive.PostgresMatchArchive
import ru.mirea.robocompetition.archive.PostgresMatchStats
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
 *  - PostgreSQL в качестве архива матчей: [PostgresArchiveWriter] пишет события
 *    из шины асинхронно, [PostgresMatchArchive] обслуживает чтение API.
 *
 * Параметры подключения к БД берутся из env: PG_URL, PG_USER, PG_PASSWORD.
 * Schema-миграции применяются Liquibase на старте.
 */
fun main() {
    val gameConfig = GameConfig(
        width = 10,
        height = 10,
        coinCount = 15,
        maxRounds = 30,
        stepDelayMs = 0
    )

    val dataSource = Database.start(Database.fromEnv())
    val archive = PostgresMatchArchive(dataSource)
    val stats = PostgresMatchStats(dataSource)

    val bus = InMemoryMatchEventBus()
    val writer = PostgresArchiveWriter(dataSource, bus).start()
    val broadcaster = MatchLiveBroadcaster(archive).also { bus.register(it) }

    Runtime.getRuntime().addShutdownHook(Thread {
        try { writer.close() } catch (_: Throwable) {}
        try { dataSource.close() } catch (_: Throwable) {}
    })

    // Опционально: искусственный delay между раундами для визуального демо.
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
        webApp(archive = archive, broadcaster = broadcaster, stats = stats, corsAllowedOrigins = corsOrigins)
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
