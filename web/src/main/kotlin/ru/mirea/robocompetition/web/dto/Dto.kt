package ru.mirea.robocompetition.web.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * JSON DTO для REST и WebSocket. Полностью изолированы от внутренних
 * моделей :server — фронт зависит только от этого слоя.
 *
 * Имена полей в camelCase (стандарт JS), kotlinx-serialization сериализует
 * data-классы автоматически.
 */

@Serializable
data class MatchSnapshotDto(
    val round: Int,
    val width: Int,
    val height: Int,
    val maxRounds: Int,
    val bots: List<BotViewDto>,
    val items: List<ItemViewDto>
)

@Serializable
data class BotViewDto(
    val id: Int,
    val name: String,
    val x: Int,
    val y: Int,
    val score: Int,
    val active: Boolean = true
)

@Serializable
data class ItemViewDto(
    val type: String,
    val x: Int,
    val y: Int
)

@Serializable
data class MatchSummaryDto(
    val matchId: String,
    val gameId: String,
    val players: List<String>,
    val status: String, // "active" | "finished"
    val startedAt: String, // ISO-8601
    val finishedAt: String?,
    val currentRound: Int,
    val maxRounds: Int,
    val winner: String?
)

@Serializable
data class MatchResultDto(
    val winner: String?,
    val finalScores: Map<String, Int>,
    val rounds: Int
)

@Serializable
data class MatchDetailDto(
    val summary: MatchSummaryDto,
    val width: Int,
    val height: Int,
    val snapshots: List<MatchSnapshotDto>,
    val result: MatchResultDto?
)

@Serializable
data class LeaderboardEntryDto(
    val player: String,
    val gameId: String,
    val matches: Int,
    val wins: Int,
    val draws: Int,
    val totalScore: Int,
    val avgScore: Double,
    val winRate: Double
)

@Serializable
data class UserDto(
    val login: String,
    val displayName: String,
    val createdAt: String
)

@Serializable
data class LoginRequestDto(val login: String, val password: String)

@Serializable
data class RegisterRequestDto(val login: String, val password: String, val displayName: String? = null)

@Serializable
data class AuthResponseDto(val token: String, val user: UserDto)

@Serializable
data class ProfileDto(
    val user: UserDto,
    val bots: List<String>,
    val stats: List<LeaderboardEntryDto>,
    val recentMatches: List<MatchSummaryDto>
)

@Serializable
data class CompetitionPeriodDto(
    val id: String,
    val name: String,
    val periodUnit: String,
    val periodsCount: Int,
    val startedAt: String
)

@Serializable
data class BotPeriodRatingDto(
    val bot: String,
    val rTop: Int,
    val rWeighted: Double,
    val rElo: Double,
    val totalScore: Int,
    val rank: Int
)

@Serializable
data class PeriodRatingDto(
    val period: CompetitionPeriodDto,
    val ratings: List<BotPeriodRatingDto>
)

/**
 * Сообщения WebSocket от сервера к клиенту.
 *
 * JSON-форма: { "type": "history" | "round" | "finished", ... }
 * Дискриминатор "type" — фронт диспатчит на нём.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class WsMessage {

    @Serializable
    @SerialName("history")
    data class History(
        val matchId: String,
        val summary: MatchSummaryDto,
        val width: Int,
        val height: Int,
        val snapshots: List<MatchSnapshotDto>,
        val result: MatchResultDto?
    ) : WsMessage()

    @Serializable
    @SerialName("round")
    data class Round(
        val matchId: String,
        val snapshot: MatchSnapshotDto
    ) : WsMessage()

    @Serializable
    @SerialName("finished")
    data class Finished(
        val matchId: String,
        val result: MatchResultDto,
        val finishedAt: String
    ) : WsMessage()
}
