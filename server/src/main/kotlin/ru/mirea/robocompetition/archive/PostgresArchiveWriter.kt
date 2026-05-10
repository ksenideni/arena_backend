package ru.mirea.robocompetition.archive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.mirea.robocompetition.events.MatchEvent
import ru.mirea.robocompetition.events.MatchEventBus
import ru.mirea.robocompetition.events.MatchEventSubscriber
import java.sql.Connection
import javax.sql.DataSource

/**
 * Асинхронный писатель архива.
 *
 * - Подписывается на [MatchEventBus] синхронным коллбеком, но внутри только
 *   `Channel.trySend`/`send` — не блокирует игровой цикл при наличии места в очереди.
 * - Отдельная coroutine читает канал и пишет события в Postgres.
 * - Если очередь переполнена, `send` приостанавливает игру (бэкпрешур),
 *   что по нашему контракту предпочтительнее потери истории матча.
 *
 * Жизненный цикл: [start] подписывается на шину, [close] отписывается, дренирует
 * очередь и закрывает coroutine. Подходит для shutdown-хука.
 */
class PostgresArchiveWriter(
    private val dataSource: DataSource,
    private val bus: MatchEventBus,
    queueCapacity: Int = 1024,
    private val drainTimeoutMs: Long = 5_000
) : MatchEventSubscriber {

    private val channel = Channel<MatchEvent>(capacity = queueCapacity)
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)
    private var consumerJob: Job? = null
    private var subscription: MatchEventBus.Subscription? = null

    fun start(): PostgresArchiveWriter {
        subscription = bus.subscribe(this)
        consumerJob = scope.launch { consume() }
        return this
    }

    /**
     * onEvent вызывается из потока публикатора (MatchRunner). Используем
     * блокирующий `runBlocking { send() }` для бэкпрешура — если очередь
     * заполнилась, лучше затормозить раунд, чем терять снимки.
     */
    override fun onEvent(event: MatchEvent) {
        // trySend сначала — на горячем пути почти всегда успешен
        val sent = channel.trySend(event).isSuccess
        if (!sent) runBlocking { channel.send(event) }
    }

    private suspend fun consume() {
        for (event in channel) {
            try {
                dataSource.connection.use { conn -> writeEvent(conn, event) }
            } catch (e: Exception) {
                System.err.println("PostgresArchiveWriter: ошибка записи ${event::class.simpleName} matchId=${event.matchId}: ${e.message}")
            }
        }
    }

    private fun writeEvent(conn: Connection, event: MatchEvent) {
        when (event) {
            is MatchEvent.Started -> insertMatch(conn, event)
            is MatchEvent.Round -> insertSnapshot(conn, event)
            is MatchEvent.Finished -> finishMatch(conn, event)
        }
    }

    private fun insertMatch(conn: Connection, event: MatchEvent.Started) {
        conn.autoCommit = false
        try {
            val matchSql = """
                INSERT INTO matches
                    (match_id, game_id, players, status, started_at, finished_at,
                     current_round, max_rounds, width, height, winner, result)
                VALUES (?, ?, ?, 'ACTIVE', ?, NULL, ?, ?, ?, ?, NULL, NULL)
                ON CONFLICT (match_id) DO NOTHING
            """.trimIndent()
            conn.prepareStatement(matchSql).use { ps ->
                ps.setString(1, event.matchId)
                ps.setString(2, event.gameId)
                ps.setArray(3, conn.createArrayOf("text", event.players.toTypedArray()))
                ps.setTimestamp(4, java.sql.Timestamp.from(event.startedAt))
                ps.setInt(5, event.initialSnapshot.round)
                ps.setInt(6, event.maxRounds)
                ps.setInt(7, event.width)
                ps.setInt(8, event.height)
                ps.executeUpdate()
            }

            insertSnapshotRow(conn, event.matchId, event.initialSnapshot.round, ArchiveJson.encodeToString(
                ru.mirea.robocompetition.events.MatchSnapshot.serializer(), event.initialSnapshot
            ))
            conn.commit()
        } catch (t: Throwable) {
            conn.rollback()
            throw t
        } finally {
            conn.autoCommit = true
        }
    }

    private fun insertSnapshot(conn: Connection, event: MatchEvent.Round) {
        conn.autoCommit = false
        try {
            insertSnapshotRow(conn, event.matchId, event.snapshot.round, ArchiveJson.encodeToString(
                ru.mirea.robocompetition.events.MatchSnapshot.serializer(), event.snapshot
            ))
            conn.prepareStatement(
                "UPDATE matches SET current_round = ? WHERE match_id = ?"
            ).use { ps ->
                ps.setInt(1, event.snapshot.round)
                ps.setString(2, event.matchId)
                ps.executeUpdate()
            }
            conn.commit()
        } catch (t: Throwable) {
            conn.rollback()
            throw t
        } finally {
            conn.autoCommit = true
        }
    }

    private fun insertSnapshotRow(conn: Connection, matchId: String, round: Int, payloadJson: String) {
        val sql = """
            INSERT INTO match_snapshots (match_id, round, payload)
            VALUES (?, ?, ?)
            ON CONFLICT (match_id, round) DO NOTHING
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            ps.setString(1, matchId)
            ps.setInt(2, round)
            ps.setObject(3, jsonbOf(payloadJson))
            ps.executeUpdate()
        }
    }

    private fun finishMatch(conn: Connection, event: MatchEvent.Finished) {
        val sql = """
            UPDATE matches
               SET status = 'FINISHED',
                   finished_at = ?,
                   winner = ?,
                   result = ?
             WHERE match_id = ?
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            ps.setTimestamp(1, java.sql.Timestamp.from(event.finishedAt))
            ps.setString(2, event.result.winner)
            ps.setObject(3, jsonbOf(ArchiveJson.encodeToString(
                ru.mirea.robocompetition.model.MatchResult.serializer(), event.result
            )))
            ps.setString(4, event.matchId)
            ps.executeUpdate()
        }
    }

    /**
     * Останавливает приём событий, дренирует очередь, завершает coroutine.
     * Безопасно вызывать дважды.
     */
    fun close() {
        subscription?.cancel()
        subscription = null
        channel.close()
        runBlocking {
            withTimeoutOrNull(drainTimeoutMs) { consumerJob?.join() }
        }
        scope.cancel()
    }
}
