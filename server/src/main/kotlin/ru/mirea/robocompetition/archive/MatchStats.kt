package ru.mirea.robocompetition.archive

/**
 * Агрегатная статистика по матчам. Отдельный интерфейс от [MatchArchive],
 * чтобы не смешивать ленточное чтение деталей матча и сводные запросы.
 */
interface MatchStats {
    /**
     * Лидерборд: по строке на пару (бот, игра). Учитываются только завершённые матчи.
     * Сортировка: по wins DESC, totalScore DESC.
     */
    fun leaderboard(): List<LeaderboardRow>
}

data class LeaderboardRow(
    val player: String,
    val gameId: String,
    val matches: Int,
    val wins: Int,
    val draws: Int,
    val totalScore: Int,
    val avgScore: Double,
    val winRate: Double
)
