package ru.mirea.robocompetition.archive

import org.postgresql.util.PGobject
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
            SELECT match_id, game_id, players, status, started_at, finished_at,
                   current_round, max_rounds, winner
              FROM matches
             ORDER BY started_at DESC
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
            SELECT match_id, game_id, players, status, started_at, finished_at,
                   current_round, max_rounds, winner, width, height, result
              FROM matches
             WHERE match_id = ?
        """.trimIndent()

        return conn.prepareStatement(sql).use { ps ->
            ps.setString(1, matchId)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                val summary = readSummary(rs)
                val resultJson = rs.getString("result")
                MatchHead(
                    summary = summary,
                    width = rs.getInt("width"),
                    height = rs.getInt("height"),
                    result = resultJson?.let { ArchiveJson.decodeFromString<MatchResult>(it) }
                )
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
        val playersArray = rs.getArray("players").array as Array<*>
        val players = playersArray.mapNotNull { it as? String }
        return MatchSummary(
            matchId = rs.getString("match_id"),
            gameId = rs.getString("game_id"),
            players = players,
            status = MatchStatus.valueOf(rs.getString("status")),
            startedAt = rs.getTimestamp("started_at").toInstant(),
            finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
            currentRound = rs.getInt("current_round"),
            maxRounds = rs.getInt("max_rounds"),
            winner = rs.getString("winner")
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
