package ru.mirea.robocompetition.model

import kotlinx.serialization.Serializable
import ru.mirea.robocompetition.archive.InstantIsoSerializer
import ru.mirea.robocompetition.config.GameConfig
import java.time.Instant

@Serializable
data class MatchResult(
    val matchId: String,
    @Serializable(with = InstantIsoSerializer::class)
    val timestamp: Instant,
    val config: GameConfig,
    val finalScores: Map<String, Int>,  // имя бота → счёт
    val winner: String?,                // null означает ничью
    val rounds: Int
)
