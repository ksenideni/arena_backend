package ru.mirea.robocompetition.web

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import ru.mirea.robocompetition.auth.JwtIssuer
import ru.mirea.robocompetition.auth.JwtPrincipal

/**
 * Достаёт `Authorization: Bearer <token>` из запроса, валидирует, возвращает
 * principal — или сразу пишет 401 и возвращает null, чтобы хендлер мог return@get.
 */
suspend fun ApplicationCall.requirePrincipal(jwt: JwtIssuer): JwtPrincipal? {
    val header = request.headers[HttpHeaders.Authorization]
    val token = header
        ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring("Bearer ".length)
        ?.trim()
    if (token.isNullOrBlank()) {
        respond(HttpStatusCode.Unauthorized, "missing bearer token")
        return null
    }
    val principal = jwt.verify(token)
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, "invalid token")
        return null
    }
    return principal
}
