package ru.mirea.robocompetition.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import java.time.Instant
import javax.sql.DataSource

/**
 * Учётка участника: один человек — много ботов. Связь хранится в user_bots.
 */
data class User(
    val id: Long,
    val login: String,
    val displayName: String,
    val createdAt: Instant
)

interface UserRepository {
    fun create(login: String, password: String, displayName: String = login): User
    fun findByLogin(login: String): User?
    fun authenticate(login: String, password: String): User?
    fun listBotsFor(userId: Long): List<String>
    fun ownerOfBot(botName: String): Long?

    /**
     * Закрепить bot_name за пользователем. Если уже принадлежит ему — no-op.
     * Если принадлежит другому — бросает [IllegalStateException].
     */
    fun claimBot(userId: Long, botName: String)

    companion object {
        val LOGIN_REGEX: Regex = Regex("^[A-Za-z0-9._]{3,32}$")
        const val MIN_PASSWORD_LEN = 6
    }
}

class PostgresUserRepository(private val dataSource: DataSource) : UserRepository {

    override fun create(login: String, password: String, displayName: String): User {
        require(login.matches(UserRepository.LOGIN_REGEX)) { "login must be 3-32 chars, alnum/_/." }
        require(password.length >= UserRepository.MIN_PASSWORD_LEN) { "password must be at least ${UserRepository.MIN_PASSWORD_LEN} chars" }
        val hash = PasswordHasher.hash(password)
        val sql = """
            INSERT INTO users (login, password_hash, display_name)
            VALUES (?, ?, ?)
            RETURNING id, created_at
        """.trimIndent()
        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, login)
                ps.setString(2, hash)
                ps.setString(3, displayName)
                ps.executeQuery().use { rs ->
                    check(rs.next())
                    User(
                        id = rs.getLong("id"),
                        login = login,
                        displayName = displayName,
                        createdAt = rs.getTimestamp("created_at").toInstant()
                    )
                }
            }
        }
    }

    override fun findByLogin(login: String): User? = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT id, display_name, created_at FROM users WHERE login = ?"
        ).use { ps ->
            ps.setString(1, login)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                User(
                    id = rs.getLong("id"),
                    login = login,
                    displayName = rs.getString("display_name"),
                    createdAt = rs.getTimestamp("created_at").toInstant()
                )
            }
        }
    }

    override fun authenticate(login: String, password: String): User? {
        val (user, hash) = dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, display_name, created_at, password_hash FROM users WHERE login = ?"
            ).use { ps ->
                ps.setString(1, login)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    val u = User(
                        id = rs.getLong("id"),
                        login = login,
                        displayName = rs.getString("display_name"),
                        createdAt = rs.getTimestamp("created_at").toInstant()
                    )
                    u to rs.getString("password_hash")
                }
            }
        } ?: return null
        return if (PasswordHasher.verify(password, hash)) user else null
    }

    override fun listBotsFor(userId: Long): List<String> = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT bot_name FROM user_bots WHERE user_id = ? ORDER BY bot_name").use { ps ->
            ps.setLong(1, userId)
            ps.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
    }

    override fun ownerOfBot(botName: String): Long? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT user_id FROM user_bots WHERE bot_name = ?").use { ps ->
            ps.setString(1, botName)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }

    override fun claimBot(userId: Long, botName: String) {
        require(botName.isNotBlank()) { "bot_name is blank" }
        val existing = ownerOfBot(botName)
        if (existing == userId) return
        if (existing != null) error("bot '$botName' уже занят другим участником")
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO user_bots (user_id, bot_name) VALUES (?, ?) ON CONFLICT DO NOTHING"
            ).use { ps ->
                ps.setLong(1, userId)
                ps.setString(2, botName)
                ps.executeUpdate()
            }
        }
    }
}

object PasswordHasher {
    private const val COST = 10

    fun hash(plain: String): String =
        BCrypt.withDefaults().hashToString(COST, plain.toCharArray())

    fun verify(plain: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plain.toCharArray(), hash).verified
}
