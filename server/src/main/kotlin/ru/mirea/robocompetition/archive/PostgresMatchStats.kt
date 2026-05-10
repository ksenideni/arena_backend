package ru.mirea.robocompetition.archive

import javax.sql.DataSource

/**
 * Считает агрегаты по матчам на лету в Postgres.
 *
 * Игроки лежат в matches.players (text[]), а финальные очки — в matches.result
 * (jsonb с ключом finalScores). LATERAL unnest даёт строку на каждого игрока
 * каждого матча; FILTER (...) считает победы/ничьи без подзапросов.
 *
 * Учитываются только FINISHED матчи — в активных result ещё null.
 */
class PostgresMatchStats(
    private val dataSource: DataSource
) : MatchStats {

    override fun leaderboard(): List<LeaderboardRow> {
        val sql = """
            SELECT
                u.name                                                AS player,
                m.game_id                                             AS game_id,
                COUNT(*)                                              AS matches,
                COUNT(*) FILTER (WHERE m.winner = u.name)             AS wins,
                COUNT(*) FILTER (WHERE m.winner IS NULL)              AS draws,
                COALESCE(SUM((m.result->'finalScores'->>u.name)::int), 0)        AS total_score,
                COALESCE(AVG((m.result->'finalScores'->>u.name)::numeric), 0)    AS avg_score
              FROM matches m
              CROSS JOIN LATERAL unnest(m.players) AS u(name)
             WHERE m.status = 'FINISHED'
               AND m.result IS NOT NULL
             GROUP BY u.name, m.game_id
             ORDER BY wins DESC, total_score DESC, matches DESC, u.name
        """.trimIndent()

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val matches = rs.getInt("matches")
                            val wins = rs.getInt("wins")
                            add(
                                LeaderboardRow(
                                    player = rs.getString("player"),
                                    gameId = rs.getString("game_id"),
                                    matches = matches,
                                    wins = wins,
                                    draws = rs.getInt("draws"),
                                    totalScore = rs.getInt("total_score"),
                                    avgScore = rs.getBigDecimal("avg_score").toDouble(),
                                    winRate = if (matches == 0) 0.0 else wins.toDouble() / matches
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
