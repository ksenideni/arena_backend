package ru.mirea.robocompetition.archive

import javax.sql.DataSource
import kotlin.math.pow

class PostgresRatingService(private val dataSource: DataSource) : RatingService {

    override fun listPeriods(): List<CompetitionPeriod> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, name, period_unit, periods_count, started_at FROM competition_periods ORDER BY started_at DESC"
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(
                            CompetitionPeriod(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                periodUnit = rs.getString("period_unit"),
                                periodsCount = rs.getInt("periods_count"),
                                startedAt = rs.getTimestamp("started_at").toInstant()
                            )
                        )
                    }
                }
            }
        }

    override fun getRatings(periodId: String): PeriodRating? {
        val period = loadPeriod(periodId) ?: return null
        val N = period.periodsCount

        val dailyScores = loadDailyScores(periodId)
        if (dailyScores.isEmpty()) return PeriodRating(period, emptyList())

        val allBots = dailyScores.values.flatMap { it.keys }.toSet()

        val rTopMap = computeRTop(dailyScores, allBots, k = 3)
        val rWeightMap = computeRWeighted(dailyScores, allBots, N)
        val rEloMap = computeRElo(periodId, allBots)
        val totalScoreMap = allBots.associateWith { bot ->
            dailyScores.values.sumOf { it[bot] ?: 0 }
        }

        val ratings = allBots
            .map { bot ->
                BotPeriodRating(
                    bot = bot,
                    rTop = rTopMap[bot] ?: 0,
                    rWeighted = rWeightMap[bot] ?: 0.0,
                    rElo = rEloMap[bot] ?: 1000.0,
                    totalScore = totalScoreMap[bot] ?: 0,
                    rank = 0
                )
            }
            .sortedWith(
                compareByDescending<BotPeriodRating> { it.rElo }
                    .thenByDescending { it.rWeighted }
                    .thenByDescending { it.rTop }
            )
            .mapIndexed { i, r -> r.copy(rank = i + 1) }

        return PeriodRating(period, ratings)
    }

    private fun loadPeriod(periodId: String): CompetitionPeriod? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, name, period_unit, periods_count, started_at FROM competition_periods WHERE id = ?"
            ).use { ps ->
                ps.setString(1, periodId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null
                    else CompetitionPeriod(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        periodUnit = rs.getString("period_unit"),
                        periodsCount = rs.getInt("periods_count"),
                        startedAt = rs.getTimestamp("started_at").toInstant()
                    )
                }
            }
        }

    // Map<period_day, Map<player, total_score>>
    private fun loadDailyScores(periodId: String): Map<Int, Map<String, Int>> {
        val sql = """
            SELECT m.period_day, ms.player_name, SUM(ms.score) AS day_score
              FROM matches m
              JOIN match_scores ms ON ms.match_id = m.match_id
             WHERE m.period_id = ?
               AND m.status = 'FINISHED'
               AND m.period_day IS NOT NULL
             GROUP BY m.period_day, ms.player_name
             ORDER BY m.period_day
        """.trimIndent()

        val result = mutableMapOf<Int, MutableMap<String, Int>>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, periodId)
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        val day = rs.getInt("period_day")
                        val player = rs.getString("player_name")
                        val score = rs.getInt("day_score")
                        result.getOrPut(day) { mutableMapOf() }[player] = score
                    }
                }
            }
        }
        return result
    }

    // R_top = Σ 𝟙[rank_i ≤ k]
    private fun computeRTop(
        dailyScores: Map<Int, Map<String, Int>>,
        allBots: Set<String>,
        k: Int
    ): Map<String, Int> {
        val result = allBots.associateWith { 0 }.toMutableMap()
        for ((_, scores) in dailyScores) {
            val topK = scores.entries
                .sortedByDescending { it.value }
                .take(k)
                .map { it.key }
            for (bot in topK) result[bot] = (result[bot] ?: 0) + 1
        }
        return result
    }

    // R_w = Σ sc_i · w_i  where w_i = i / Σ(1..N),  sc_i = score_i / max_score_i
    private fun computeRWeighted(
        dailyScores: Map<Int, Map<String, Int>>,
        allBots: Set<String>,
        N: Int
    ): Map<String, Double> {
        val result = allBots.associateWith { 0.0 }.toMutableMap()
        val sumW = (1..N).sum().toDouble()

        for ((day, scores) in dailyScores) {
            val maxScore = scores.values.maxOrNull()?.takeIf { it > 0 } ?: continue
            val weight = day.toDouble() / sumW
            for (bot in allBots) {
                val sc = (scores[bot] ?: 0).toDouble() / maxScore
                result[bot] = (result[bot] ?: 0.0) + sc * weight
            }
        }
        return result
    }

    // R_elo: процессируем матчи попарно в хронологическом порядке, K=32, начало 1000
    private fun computeRElo(periodId: String, allBots: Set<String>): Map<String, Double> {
        data class Row(val matchId: String, val player: String, val score: Int)

        val sql = """
            SELECT m.match_id, ms.player_name, ms.score
              FROM matches m
              JOIN match_scores ms ON ms.match_id = m.match_id
             WHERE m.period_id = ?
               AND m.status = 'FINISHED'
             ORDER BY m.started_at, m.match_id
        """.trimIndent()

        val rows = mutableListOf<Row>()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, periodId)
                ps.executeQuery().use { rs ->
                    while (rs.next())
                        rows.add(Row(rs.getString("match_id"), rs.getString("player_name"), rs.getInt("score")))
                }
            }
        }

        val K = 32.0
        val elo = allBots.associateWith { 1000.0 }.toMutableMap()

        for ((_, matchRows) in rows.groupBy { it.matchId }) {
            val scores = matchRows.associate { it.player to it.score }
            val players = scores.keys.toList()
            val deltas = players.associateWith { 0.0 }.toMutableMap()

            for (i in players.indices) {
                for (j in i + 1 until players.size) {
                    val a = players[i];
                    val b = players[j]
                    val eA = elo[a] ?: 1000.0;
                    val eB = elo[b] ?: 1000.0
                    val sA = scores[a] ?: 0;
                    val sB = scores[b] ?: 0
                    val wA = when {
                        sA > sB -> 1.0; sA < sB -> 0.0; else -> 0.5
                    }
                    val wB = 1.0 - wA
                    val expA = 1.0 / (1.0 + 10.0.pow((eB - eA) / 400.0))
                    val expB = 1.0 - expA
                    deltas[a] = (deltas[a] ?: 0.0) + K * (wA - expA)
                    deltas[b] = (deltas[b] ?: 0.0) + K * (wB - expB)
                }
            }
            for ((player, delta) in deltas) elo[player] = (elo[player] ?: 1000.0) + delta
        }

        return elo
    }
}
