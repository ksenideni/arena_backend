package ru.mirea.robocompetition.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import ru.mirea.robocompetition.archive.MatchArchive
import ru.mirea.robocompetition.archive.MatchStats
import ru.mirea.robocompetition.web.dto.WsMessage
import ru.mirea.robocompetition.web.dto.toDto
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor-модуль приложения. Регистрирует REST + WS + CORS.
 *
 * Бэкенд предоставляет ТОЛЬКО HTTP/WS API — фронт деплоится отдельно
 * (любой статик-хостинг или Vite-dev-server) и общается через [corsAllowedOrigins].
 *
 * Зависимости приходят через параметры — никакого DI-фреймворка, всё видно
 * в [ru.mirea.robocompetition.web.Main].
 */
fun Application.webApp(
    archive: MatchArchive,
    broadcaster: MatchLiveBroadcaster,
    stats: MatchStats,
    corsAllowedOrigins: List<String> = emptyList()
) {
    val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    install(ContentNegotiation) {
        json(json)
    }

    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(json)
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }

    val log = KtorSimpleLogger("ru.mirea.robocompetition.web")

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled exception: ${cause.message}", cause)
            call.respondText(
                text = "internal error: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    if (corsAllowedOrigins.isNotEmpty()) {
        install(CORS) {
            for (origin in corsAllowedOrigins) {
                val (scheme, hostPort) = origin.split("://", limit = 2).let {
                    if (it.size == 2) it[0] to it[1] else "http" to it[0]
                }
                allowHost(hostPort, schemes = listOf(scheme))
            }
            allowHeader(HttpHeaders.ContentType)
            allowCredentials = true
        }
    }

    routing {
        get("/api/matches") {
            val list = archive.listMatches().map { it.toDto() }
            call.respond(list)
        }

        get("/api/leaderboard") {
            call.respond(stats.leaderboard().map { it.toDto() })
        }

        get("/api/matches/{id}") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "missing id")
                return@get
            }
            val detail = archive.getDetail(id)
            if (detail == null) {
                call.respond(HttpStatusCode.NotFound, "match not found")
                return@get
            }
            call.respond(detail.toDto())
        }

        webSocket("/api/matches/{id}/live") {
            val id = call.parameters["id"]
            if (id.isNullOrBlank()) {
                close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "missing id"))
                return@webSocket
            }
            val connection = broadcaster.connect(id)
            if (connection == null) {
                close(CloseReason(CloseReason.Codes.NORMAL, "match not found"))
                return@webSocket
            }
            try {
                sendSerialized<WsMessage>(connection.history)
                for (msg in connection.channel) {
                    if (!isActive) break
                    sendSerialized<WsMessage>(msg)
                    if (msg is WsMessage.Finished) {
                        // даём клиенту увидеть финал, потом закрываем сокет
                        close(CloseReason(CloseReason.Codes.NORMAL, "match finished"))
                        break
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                // нормальное закрытие
            } finally {
                connection.close()
            }
        }
    }
}
