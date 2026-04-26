package ru.mirea.robocompetition.network

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Обёртка над TCP-сокетом, предоставляющая чтение/запись Message.
 *
 * Не делает никаких предположений об играх или ролях — это просто канал.
 * Таймаут чтения настраивается через [setReadTimeout]; по истечении —
 * SocketTimeoutException (extends IOException), которое обрабатывает вызывающий код.
 */
class Session(private val socket: Socket) {

    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val writer = PrintWriter(socket.getOutputStream().bufferedWriter(Charsets.UTF_8), false)

    init {
        socket.tcpNoDelay = true
    }

    fun read(): Message? = MessageCodec.read(reader)

    fun write(message: Message) = MessageCodec.write(writer, message)

    fun setReadTimeout(ms: Int) {
        socket.soTimeout = ms
    }

    fun close() {
        try {
            socket.close()
        } catch (_: IOException) {
            // соединение уже мёртвое — игнорируем
        }
    }

    val isOpen: Boolean
        get() = !socket.isClosed && socket.isConnected

    val remoteAddress: String
        get() = socket.remoteSocketAddress?.toString() ?: "?"
}
