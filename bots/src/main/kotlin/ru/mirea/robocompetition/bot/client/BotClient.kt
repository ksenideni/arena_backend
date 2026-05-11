package ru.mirea.robocompetition.bot.client

import ru.mirea.robocompetition.model.Offset
import ru.mirea.robocompetition.model.Position
import ru.mirea.robocompetition.network.Message
import ru.mirea.robocompetition.network.Session
import java.io.IOException
import java.net.Socket

/**
 * База для всех ботов-клиентов CoinCollector, работающих по TCP.
 *
 * Наследник реализует [decideMove] — всю сетевую логику база берёт на себя:
 *  - подключается к серверу
 *  - проходит handshake (hello → register → match_started)
 *  - в цикле получает update, отдаёт ход через move
 *  - корректно завершается на match_over или ошибке
 *
 * Чтобы добавить нового бота, достаточно унаследоваться и реализовать одну функцию.
 */
abstract class BotClient(
    val name: String,
    private val host: String = DEFAULT_HOST,
    private val port: Int = DEFAULT_PORT,
    private val game: String = "collector",
    private val login: String? = null,
    private val password: String? = null
) {

    abstract fun decideMove(state: BotView): Offset

    fun run() {
        val socket = try {
            Socket(host, port)
        } catch (e: IOException) {
            println("[$name] не удалось подключиться к $host:$port — ${e.message}")
            return
        }

        socket.use {
            val session = Session(socket)
            try {
                handshakeAndPlay(session)
            } catch (e: IOException) {
                println("[$name] соединение прервано: ${e.message}")
            } catch (e: Exception) {
                println("[$name] неожиданная ошибка: ${e.message}")
            }
        }
    }

    private fun handshakeAndPlay(session: Session) {
        val hello = session.read() ?: throw IOException("EOF до hello")
        require(hello.type == "hello") { "ожидался hello, получен '${hello.type}'" }

        session.write(Message.build("register") {
            add("name", name)
            add("game", game)
            if (!login.isNullOrBlank()) add("login", login)
            if (!password.isNullOrBlank()) add("password", password)
        })

        val started = session.read() ?: throw IOException("EOF до match_started")
        if (started.type == "error") {
            println("[$name] сервер отклонил регистрацию: ${started.string("reason")}")
            return
        }
        require(started.type == "match_started") { "ожидался match_started, получен '${started.type}'" }

        val myId = started.int("my_id") ?: error("match_started без my_id")
        val numBots = started.int("num_bots") ?: error("match_started без num_bots")
        val width = started.int("width") ?: error("match_started без width")
        val height = started.int("height") ?: error("match_started без height")
        val maxRounds = started.int("max_rounds") ?: error("match_started без max_rounds")

        println("[$name] матч начался: my_id=$myId, поле ${width}x${height}, $numBots ботов")

        while (true) {
            val msg = session.read() ?: break
            when (msg.type) {
                "update" -> {
                    val view = parseView(msg, myId, numBots, width, height, maxRounds)
                    val move = decideMove(view)
                    session.write(Message.build("move") {
                        add("offset", move.dx.toString(), move.dy.toString())
                    })
                }
                "match_over" -> {
                    val winner = msg.string("winner")
                    println("[$name] матч окончен. Победитель: ${winner ?: "ничья"}")
                    return
                }
                "error" -> {
                    println("[$name] ошибка от сервера: ${msg.string("reason")}")
                    return
                }
                else -> println("[$name] неизвестный тип сообщения: ${msg.type}")
            }
        }
    }

    private fun parseView(
        msg: Message,
        myId: Int,
        numBots: Int,
        width: Int,
        height: Int,
        maxRounds: Int
    ): BotView {
        val round = msg.int("round") ?: error("update без round")
        val myPos = msg.firstField("my_position")?.values
            ?: error("update без my_position")
        val myPosition = Position(myPos[0].toInt(), myPos[1].toInt())
        val myScore = msg.int("my_score") ?: 0

        val bots = msg.allFields("bot").map { f ->
            BotView.BotInfo(
                id = f.values[0].toInt(),
                position = Position(f.values[1].toInt(), f.values[2].toInt()),
                score = f.values[3].toInt()
            )
        }
        val coins = msg.allFields("coin").map { f ->
            Position(f.values[0].toInt(), f.values[1].toInt())
        }.toSet()

        return BotView(
            myId = myId,
            numBots = numBots,
            width = width,
            height = height,
            maxRounds = maxRounds,
            round = round,
            myPosition = myPosition,
            myScore = myScore,
            bots = bots,
            coins = coins
        )
    }

    companion object {
        const val DEFAULT_HOST = "localhost"
        const val DEFAULT_PORT = 9000
    }
}
