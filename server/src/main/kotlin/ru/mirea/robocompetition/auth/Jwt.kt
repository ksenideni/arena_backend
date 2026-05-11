package ru.mirea.robocompetition.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.time.Instant
import java.util.Date

/**
 * Простой HS256 JWT-эмиттер/верификатор. Секрет — из env JWT_SECRET; для dev
 * допустим дефолт, в проде — обязательно задавать.
 */
class JwtIssuer(
    private val secret: String,
    private val issuer: String = "arena",
    private val ttlSeconds: Long = 24 * 60 * 60
) {
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)
    private val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()

    fun issue(user: User): String = JWT.create()
        .withIssuer(issuer)
        .withSubject(user.login)
        .withClaim("uid", user.id)
        .withClaim("name", user.displayName)
        .withIssuedAt(Date())
        .withExpiresAt(Date.from(Instant.now().plusSeconds(ttlSeconds)))
        .sign(algorithm)

    /** Возвращает login (sub) при валидном токене, иначе null. */
    fun verify(token: String): JwtPrincipal? = try {
        val decoded = verifier.verify(token)
        JwtPrincipal(
            login = decoded.subject,
            userId = decoded.getClaim("uid").asLong() ?: return null,
            displayName = decoded.getClaim("name").asString() ?: decoded.subject
        )
    } catch (_: JWTVerificationException) {
        null
    }

    companion object {
        fun fromEnv(): JwtIssuer {
            val secret = System.getenv("JWT_SECRET")
                ?: "arena-dev-secret-please-override-in-prod-1234567890"
            return JwtIssuer(secret = secret)
        }
    }
}

data class JwtPrincipal(
    val login: String,
    val userId: Long,
    val displayName: String
)
