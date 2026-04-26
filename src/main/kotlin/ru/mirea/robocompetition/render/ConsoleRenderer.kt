package ru.mirea.robocompetition.render

import ru.mirea.robocompetition.match.MatchListener
import ru.mirea.robocompetition.model.GameState
import ru.mirea.robocompetition.model.MatchResult
import ru.mirea.robocompetition.model.Position

/**
 * Подписчик на события матча CoinCollector, который рисует поле в терминал.
 *
 * Чтобы заменить на веб-рендерер, достаточно добавить ещё одну реализацию
 * MatchListener&lt;GameState&gt; и передать её в MatchRunner.
 */
class ConsoleRenderer(
    private val stepDelayMs: Long = 300L
) : MatchListener<GameState> {

    override fun onMatchStarted(state: GameState) {
        render(state)
        Thread.sleep(stepDelayMs)
    }

    override fun onRoundComplete(state: GameState) {
        render(state)
        Thread.sleep(stepDelayMs)
    }

    override fun onMatchOver(result: MatchResult) {
        println("=== МАТЧ ОКОНЧЕН ===")
        println("Match ID: ${result.matchId}")
        result.finalScores.entries
            .sortedByDescending { it.value }
            .forEach { (name, score) -> println("  $name: $score монет") }
        if (result.winner != null) println("Победитель: ${result.winner}")
        else println("Результат: ничья")
    }

    private fun render(state: GameState) {
        clearScreen()
        printHeader(state)
        printGrid(state)
        printScoreboard(state)
    }

    private fun clearScreen() {
        // ANSI-escape: переместить курсор в левый верхний угол, затем очистить экран
        print("[H[2J")
        System.out.flush()
    }

    private fun printHeader(state: GameState) {
        println("=== COIN COLLECTOR  раунд ${state.round} / ${state.config.maxRounds} ===")
        println()
    }

    private fun printGrid(state: GameState) {
        val botPositions: Map<Position, Int> = state.bots.associate { it.position to (it.id + 1) }

        for (y in 0 until state.config.height) {
            val row = buildString {
                for (x in 0 until state.config.width) {
                    val pos = Position(x, y)
                    val cell = when {
                        botPositions.containsKey(pos) -> botPositions[pos].toString()
                        pos in state.coins            -> "*"
                        else                          -> "."
                    }
                    if (x > 0) append(" ")
                    append(cell)
                }
            }
            println(row)
        }
        println()
    }

    private fun printScoreboard(state: GameState) {
        println("--- Счёт ---")
        state.bots
            .sortedByDescending { it.score }
            .forEach { bot -> println("  [${bot.id + 1}] ${bot.name}: ${bot.score} монет") }
        println("Монет на поле: ${state.coins.size}")
        println()
    }
}
