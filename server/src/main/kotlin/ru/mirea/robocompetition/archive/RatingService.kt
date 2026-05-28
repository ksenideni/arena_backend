package ru.mirea.robocompetition.archive

import java.time.Instant

interface RatingService {
    fun listPeriods(): List<CompetitionPeriod>
    fun getRatings(periodId: String): PeriodRating?
}

data class CompetitionPeriod(
    val id: String,
    val name: String,
    val periodUnit: String,
    val periodsCount: Int,
    val startedAt: Instant
)

data class PeriodRating(
    val period: CompetitionPeriod,
    val ratings: List<BotPeriodRating>
)

data class BotPeriodRating(
    val bot: String,
    val rTop: Int,
    val rWeighted: Double,
    val rElo: Double,
    val totalScore: Int,
    val rank: Int
)
