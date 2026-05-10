package ru.mirea.robocompetition.config

import kotlinx.serialization.Serializable

@Serializable
data class GameConfig(
    val width: Int = 10,
    val height: Int = 10,
    val coinCount: Int = 15,
    val maxRounds: Int = 10,
    val stepDelayMs: Long = 500L
)
