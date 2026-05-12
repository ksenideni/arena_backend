package ru.mirea.robocompetition.archive

import org.postgresql.util.PGobject
import ru.mirea.robocompetition.config.GameConfig
import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Реализация [MatchArchive], читающая данные из Postgres.
 *
 * Запись делает [PostgresArchiveWriter] (подписан на шину событий и пишет
 * асинхронно). Этот класс — только читалка; одна и та же БД, разные обязанности.
 */
class PostgresMatchArchive(
    private val dataSource: DataSource
) : MatchArchive {

    override fun listMatches(): List<MatchSummary> {
        val sql = """
            SELECT m.match_id, m.game_id, m.status, m.started_at, m.finished_at,
                   m.current_round, m.max_rounds, m.winner,
                   array_agg(mp.player_name ORDER BY mp.position) AS players
              FROM matches m
              LEFT JOIN match_players mp ON mp.match_id = m.match_id
             GROUP BY m.match_id
             ORDER BY m.started_at DESC
        """.trimIndent()

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(readSummary(rs)) }
                }
            }
        }
    }

    override fun getDetail(matchId: String): MatchDetail? =
        dataSource.connection.use { conn ->
            val head = loadHead(conn, matchId) ?: return@use null
            val snapshots = loadSnapshots(conn, matchId)
            MatchDetail(
                summary = head.summary,
                width = head.width,
                height = head.height,
                snapshots = snapshots,
                result = head.result
            )
        }

    private fun loadHead(conn: Connection, matchId: String): MatchHead? {
        val sql = """
            SELECT m.match_id, m.game_id, m.status, m.started_at, m.finished_at,
                   m.current_round, m.max_rounds, m.winner, m.width, m.height,
                   m.coin_count, m.step_delay_ms,
                   array_agg(mp.player_name ORDER BY mp.position) AS players
              FROM matches m
              LEFT JOIN match_players mp ON mp.match_id = m.match_id
             WHERE m.match_id = ?
             GROUP BY m.match_id
        """.trimIndent()

        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, matchId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                val summary = readSummary(rs)
                val width = rs.getInt("width")
                val height = rs.getInt("height")
                val result = if (summary.status == MatchStatus.FINISHED) {
                    buildMatchResult(conn, rs, summary, width, height)
                } else null
                MatchHead(summary = summary, width = width, height = height, result = result)
            }
        }
    }

    private fun buildMatchResult(
        conn: Connection,
        rs: ResultSet,
        summary: MatchSummary,
        width: Int,
        height: Int
    ): MatchResult {
        val finalScores = loadScores(conn, summary.matchId)
        val config = GameConfig(
            width        = width,
            height       = height,
            coinCount    = rs.getInt("coin_count").takeIf { !rs.wasNull() } ?: 15,
            maxRounds    = summary.maxRounds,
            stepDelayMs  = rs.getLong("step_delay_ms").takeIf { !rs.wasNull() } ?: 500L
        )
        return MatchResult(
            matchId     = summary.matchId,
            timestamp   = summary.finishedAt ?: summary.startedAt,
            config      = config,
            finalScores = finalScores,
            winner      = summary.winner,
            rounds      = summary.currentRound
        )
    }

    private fun loadScores(conn: Connection, matchId: String): Map<String, Int> {
        return conn.prepareStatement(
            "SELECT player_name, score FROM match_scores WHERE match_id = ?"
        ).use { ps ->
            ps.setString(1, matchId)
            ps.executeQuery().use { rs ->
                buildMap { while (rs.next()) put(rs.getString("player_name"), rs.getInt("score")) }
            }
        }
    }

    private fun loadSnapshots(conn: Connection, matchId: String): List<MatchSnapshot> {
        val sql = """
            SELECT payload
              FROM match_snapshots
             WHERE match_id = ?
             ORDER BY round
        """.trimIndent()

        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, matchId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(ArchiveJson.decodeFromString<MatchSnapshot>(rs.getString("payload")))
                    }
                }
            }
        }
    }

    private fun readSummary(rs: ResultSet): MatchSummary {
        val playersArray = rs.getArray("players")?.array as? Array<*>
        val players = playersArray?.mapNotNull { it as? String } ?: emptyList()
        return MatchSummary(
            matchId      = rs.getString("match_id"),
            gameId       = rs.getString("game_id"),
            players      = players,
            status       = MatchStatus.valueOf(rs.getString("status")),
            startedAt    = rs.getTimestamp("started_at").toInstant(),
            finishedAt   = rs.getTimestamp("finished_at")?.toInstant(),
            currentRound = rs.getInt("current_round"),
            maxRounds    = rs.getInt("max_rounds"),
            winner       = rs.getString("winner")
        )
    }

    private data class MatchHead(
        val summary: MatchSummary,
        val width: Int,
        val height: Int,
        val result: MatchResult?
    )
}

internal fun jsonbOf(json: String): PGobject = PGobject().apply {
    type = "jsonb"
    value = json
}
