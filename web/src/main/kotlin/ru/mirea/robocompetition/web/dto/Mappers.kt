package ru.mirea.robocompetition.web.dto

import ru.mirea.robocompetition.archive.MatchDetail
import ru.mirea.robocompetition.archive.MatchStatus
import ru.mirea.robocompetition.archive.MatchSummary
import ru.mirea.robocompetition.events.BotView
import ru.mirea.robocompetition.events.ItemView
import ru.mirea.robocompetition.events.MatchSnapshot
import ru.mirea.robocompetition.model.MatchResult

fun MatchSnapshot.toDto(): MatchSnapshotDto = MatchSnapshotDto(
    round = round, width = width, height = height, maxRounds = maxRounds,
    bots = bots.map { it.toDto() },
    items = items.map { it.toDto() }
)

fun BotView.toDto(): BotViewDto = BotViewDto(id, name, x, y, score, active)

fun ItemView.toDto(): ItemViewDto = ItemViewDto(type, x, y)

fun MatchSummary.toDto(): MatchSummaryDto = MatchSummaryDto(
    matchId = matchId,
    gameId = gameId,
    players = players,
    status = when (status) { MatchStatus.ACTIVE -> "active"; MatchStatus.FINISHED -> "finished" },
    startedAt = startedAt.toString(),
    finishedAt = finishedAt?.toString(),
    currentRound = currentRound,
    maxRounds = maxRounds,
    winner = winner
)

fun MatchResult.toDto(): MatchResultDto = MatchResultDto(
    winner = winner,
    finalScores = finalScores,
    rounds = rounds
)

fun MatchDetail.toDto(): MatchDetailDto = MatchDetailDto(
    summary = summary.toDto(),
    width = width,
    height = height,
    snapshots = snapshots.map { it.toDto() },
    result = result?.toDto()
)
