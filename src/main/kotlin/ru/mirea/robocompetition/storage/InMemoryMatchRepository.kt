package ru.mirea.robocompetition.storage

import ru.mirea.robocompetition.model.MatchResult

class InMemoryMatchRepository : MatchRepository {

    private val store: MutableList<MatchResult> = mutableListOf()

    override fun save(result: MatchResult) {
        store.add(result)
    }

    override fun findAll(): List<MatchResult> = store.toList()

    override fun findById(matchId: String): MatchResult? =
        store.find { it.matchId == matchId }
}
