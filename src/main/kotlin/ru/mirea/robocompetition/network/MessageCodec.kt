package ru.mirea.robocompetition.network

import java.io.BufferedReader
import java.io.IOException
import java.io.PrintWriter

/**
 * Кодирование/декодирование сообщений в текстовом формате.
 *
 * Формат:
 *   <type>
 *   <key1> <v1> <v2> ...
 *   <key2> <v1>
 *   end
 *
 * Разделитель полей внутри строки — пробел; завершение сообщения — строка "end".
 */
object MessageCodec {

    private const val TERMINATOR = "end"

    fun read(reader: BufferedReader): Message? {
        val type = reader.readLine() ?: return null
        if (type.isBlank()) return null
        if (type == TERMINATOR) throw IOException("Получен 'end' до типа сообщения")

        val fields = mutableListOf<Message.Field>()
        while (true) {
            val line = reader.readLine() ?: throw IOException("Соединение закрыто посередине сообщения")
            if (line == TERMINATOR) break
            if (line.isBlank()) continue
            val parts = line.split(" ").filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            fields.add(Message.Field(parts[0], parts.drop(1)))
        }
        return Message(type.trim(), fields)
    }

    fun write(writer: PrintWriter, message: Message) {
        writer.println(message.type)
        for (field in message.fields) {
            if (field.values.isEmpty()) writer.println(field.key)
            else writer.println("${field.key} ${field.values.joinToString(" ")}")
        }
        writer.println(TERMINATOR)
        writer.flush()
        if (writer.checkError()) throw IOException("Ошибка записи в сокет")
    }
}
