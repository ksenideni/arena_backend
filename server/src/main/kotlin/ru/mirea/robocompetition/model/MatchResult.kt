package ru.mirea.robocompetition.model

import ru.mirea.robocompetition.config.GameConfig
import java.time.Instant

data class MatchResult(
    val matchId: String,
    val timestamp: Instant,
    val config: GameConfig,
    val finalScores: Map<String, Int>,  // имя бота → счёт
    val winner: String?,                // null означает ничью
    val rounds: Int
)
