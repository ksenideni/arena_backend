package ru.mirea.robocompetition.storage

import ru.mirea.robocompetition.model.MatchResult

/**
 * Контракт хранилища результатов матчей.
 *
 * Реализация по умолчанию — InMemoryMatchRepository (без внешних зависимостей).
 * Чтобы подключить PostgresMatchRepository, достаточно заменить аргумент
 * конструктора в Main.kt — код движка не меняется.
 */
interface MatchRepository {
    fun save(result: MatchResult)
    fun findAll(): List<MatchResult>
    fun findById(matchId: String): MatchResult?
}
