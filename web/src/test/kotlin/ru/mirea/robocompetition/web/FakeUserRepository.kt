package ru.mirea.robocompetition.web

import ru.mirea.robocompetition.auth.PasswordHasher
import ru.mirea.robocompetition.auth.User
import ru.mirea.robocompetition.auth.UserRepository
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory реализация [UserRepository] для тестов. Не in-prod.
 */
internal class FakeUserRepository : UserRepository {
    private data class Entry(val user: User, val passwordHash: String)

    private val byLogin = mutableMapOf<String, Entry>()
    private val botOwner = mutableMapOf<String, Long>()
    private val botsByUser = mutableMapOf<Long, MutableSet<String>>()
    private val nextId = AtomicLong(1)

    override fun create(login: String, password: String, displayName: String): User {
        require(login.matches(UserRepository.LOGIN_REGEX))
        require(password.length >= UserRepository.MIN_PASSWORD_LEN)
        check(login !in byLogin) { "login занят" }
        val user = User(nextId.getAndIncrement(), login, displayName, Instant.now())
        byLogin[login] = Entry(user, PasswordHasher.hash(password))
        return user
    }

    override fun findByLogin(login: String): User? = byLogin[login]?.user

    override fun authenticate(login: String, password: String): User? {
        val entry = byLogin[login] ?: return null
        return if (PasswordHasher.verify(password, entry.passwordHash)) entry.user else null
    }

    override fun listBotsFor(userId: Long): List<String> =
        botsByUser[userId]?.sorted().orEmpty()

    override fun ownerOfBot(botName: String): Long? = botOwner[botName]

    override fun claimBot(userId: Long, botName: String) {
        val current = botOwner[botName]
        if (current == userId) return
        if (current != null) error("bot '$botName' уже занят другим участником")
        botOwner[botName] = userId
        botsByUser.getOrPut(userId) { mutableSetOf() }.add(botName)
    }
}
