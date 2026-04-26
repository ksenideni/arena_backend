package ru.mirea.robocompetition.lobby

import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.games.coincollector.CoinCollectorScenario
import ru.mirea.robocompetition.match.MatchRunner
import ru.mirea.robocompetition.network.Message
import ru.mirea.robocompetition.network.Session
import ru.mirea.robocompetition.render.ConsoleRenderer
import ru.mirea.robocompetition.storage.InMemoryMatchRepository
import ru.mirea.robocompetition.storage.MatchRepository
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Принимает TCP-подключения от ботов, обрабатывает регистрацию и передаёт
 * сессии в Lobby. Когда лобби набирает 4 ботов — запускает матч.
 *
 * Конфигурация игры зашита в коде (по требованию первой итерации).
 * Чтобы поддержать новую игру: реализовать GameScenario и добавить ветку
 * в [createScenarioFor].
 */
class Server(
    private val port: Int = DEFAULT_PORT,
    private val matchSize: Int = DEFAULT_MATCH_SIZE,
    private val gameConfig: GameConfig = GameConfig(),
    private val repository: MatchRepository = InMemoryMatchRepository(),
    private val registerTimeoutMs: Int = 10_000
) {

    private val lobby = Lobby(matchSize) { game, bots -> runMatch(game, bots) }

    fun start() {
        val server = ServerSocket(port)
        println("Сервер слушает порт $port (по $matchSize ботов в матче)")

        while (true) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                println("Ошибка accept: ${e.message}")
                continue
            }
            Thread({ handleConnection(socket) }, "register-${socket.remoteSocketAddress}").start()
        }
    }

    private fun handleConnection(socket: Socket) {
        val session = Session(socket)
        try {
            session.write(Message.build("hello") { add("protocol_version", "1") })

            session.setReadTimeout(registerTimeoutMs)
            val msg = session.read() ?: throw IOException("EOF до регистрации")
            if (msg.type != "register") {
                writeError(session, "ожидалось сообщение register, получено '${msg.type}'")
                session.close()
                return
            }

            val name = msg.string("name")
            val game = msg.string("game")
            if (name.isNullOrBlank() || game.isNullOrBlank()) {
                writeError(session, "поле name или game не указано")
                session.close()
                return
            }

            if (createScenarioFor(game) == null) {
                writeError(session, "неизвестная игра: $game")
                session.close()
                return
            }

            // снимаем регистрационный таймаут — дальше его выставит MatchRunner
            session.setReadTimeout(0)

            val added = lobby.register(game, name, session)
            if (!added) {
                writeError(session, "имя '$name' уже занято в очереди игры '$game'")
                session.close()
                return
            }

            println("Бот '$name' зарегистрирован для '$game' (с ${session.remoteAddress})")
        } catch (e: IOException) {
            println("Ошибка регистрации с ${session.remoteAddress}: ${e.message}")
            session.close()
        }
    }

    private fun runMatch(game: String, bots: List<Pair<String, Session>>) {
        val scenario = createScenarioFor(game) ?: return
        val sessions = bots.toMap()
        println("Старт матча '$game' с ботами: ${bots.map { it.first }}")

        val renderer = ConsoleRenderer()
        val runner = MatchRunner(scenario, sessions, listeners = listOf(renderer))
        try {
            val result = runner.run()
            repository.save(result)
        } catch (e: Exception) {
            println("Матч '$game' завершён с ошибкой: ${e.message}")
            sessions.values.forEach { it.close() }
        }
    }

    // Точка расширения для новых игр: добавить ветку для нового id
    private fun createScenarioFor(gameId: String) = when (gameId) {
        "collector" -> CoinCollectorScenario(gameConfig)
        else -> null
    }

    private fun writeError(session: Session, reason: String) {
        try {
            session.write(Message.build("error") { add("reason", reason) })
        } catch (_: IOException) {
            // игнорируем — соединение уже могло отвалиться
        }
    }

    companion object {
        const val DEFAULT_PORT = 9000
        const val DEFAULT_MATCH_SIZE = 4
    }
}
