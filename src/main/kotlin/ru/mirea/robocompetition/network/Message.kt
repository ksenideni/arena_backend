package ru.mirea.robocompetition.network

/**
 * Универсальный конверт сетевого сообщения.
 *
 * Состоит из типа (первая строка) и упорядоченного списка полей (key + значения).
 * Один ключ может встречаться несколько раз — например, "bot" / "coin" в update.
 * Знание о конкретных играх живёт в GameScenario, не здесь.
 */
data class Message(
    val type: String,
    val fields: List<Field>
) {
    data class Field(val key: String, val values: List<String>)

    fun firstField(key: String): Field? = fields.firstOrNull { it.key == key }
    fun allFields(key: String): List<Field> = fields.filter { it.key == key }
    fun string(key: String): String? = firstField(key)?.values?.firstOrNull()
    fun int(key: String): Int? = string(key)?.toIntOrNull()

    companion object {
        fun build(type: String, block: Builder.() -> Unit): Message {
            val builder = Builder()
            builder.block()
            return Message(type, builder.fields)
        }
    }

    class Builder {
        val fields = mutableListOf<Field>()
        fun add(key: String, vararg values: String) {
            fields.add(Field(key, values.toList()))
        }
        fun add(key: String, values: List<String>) {
            fields.add(Field(key, values))
        }
    }
}
