package ru.mirea.robocompetition.model

data class BotState(
    val id: Int,
    val name: String,
    val position: Position,
    val score: Int
)
