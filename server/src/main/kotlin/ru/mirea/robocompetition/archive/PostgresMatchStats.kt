package ru.mirea.robocompetition.archive

import javax.sql.DataSource

/**
 * Считает агрегаты по матчам на лету в Postgres.
 *
 * Игроки лежат в match_players, финальные очки — в match_scores.
 * FILTER (...) считает победы/ничьи без подзапросов.
 *
 * Учитываются только FINISHED матчи — в активных match_scores ещё пуст.
 */
class PostgresMatchStats(
    private val dataSource: DataSource
) : MatchStats {

    override fun leaderboard(): List<LeaderboardRow> {
        val sql = """
            SELECT
                mp.player_name                                               AS player,
                m.game_id                                                    AS game_id,
                COUNT(*)                                                     AS matches,
                COUNT(*) FILTER (WHERE m.winner = mp.player_name)            AS wins,
                COUNT(*) FILTER (WHERE m.winner IS NULL)                     AS draws,
                COALESCE(SUM(ms.score), 0)                                   AS total_score,
                COALESCE(AVG(ms.score::numeric), 0)                          AS avg_score
              FROM matches m
              JOIN match_players mp ON mp.match_id = m.match_id
              LEFT JOIN match_scores ms ON ms.match_id = m.match_id
                                      AND ms.player_name = mp.player_name
             WHERE m.status = 'FINISHED'
             GROUP BY mp.player_name, m.game_id
             ORDER BY wins DESC, total_score DESC, matches DESC, mp.player_name
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
                                    player     = rs.getString("player"),
                                    gameId     = rs.getString("game_id"),
                                    matches    = matches,
                                    wins       = wins,
                                    draws      = rs.getInt("draws"),
                                    totalScore = rs.getInt("total_score"),
                                    avgScore   = rs.getBigDecimal("avg_score").toDouble(),
                                    winRate    = if (matches == 0) 0.0 else wins.toDouble() / matches
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
