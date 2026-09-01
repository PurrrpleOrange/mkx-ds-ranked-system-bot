package com.mkx.ranked.service;

import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.exception.MatchNotFoundException;
import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.model.dto.MatchReportPreviewDto;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    private final PlayerRepository playerRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final MatchRepository matchRepository;
    private final SeasonService seasonService;

    public MatchService(
            PlayerRepository playerRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            MatchRepository matchRepository,
            SeasonService seasonService
    ) {
        this.playerRepository = playerRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.matchRepository = matchRepository;
        this.seasonService = seasonService;
    }

    @Transactional(readOnly = true)
    public MatchReportPreviewDto prepareMatchReport(
            long reporterDiscordId,
            long opponentDiscordId,
            int reporterScore,
            int opponentScore
    ) {
        validateReportedScore(reporterDiscordId, opponentDiscordId, reporterScore, opponentScore);

        PlayerEntity reporter = findPlayerByDiscordId(reporterDiscordId);
        PlayerEntity opponent = findPlayerByDiscordId(opponentDiscordId);
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        findSeasonPlayer(season, reporter);
        findSeasonPlayer(season, opponent);

        return new MatchReportPreviewDto(
                reporterDiscordId,
                opponentDiscordId,
                reporter.getDisplayName(),
                opponent.getDisplayName(),
                reporterScore,
                opponentScore
        );
    }

    @Transactional
    public MatchResult confirmReportedMatch(
            long reporterDiscordId,
            long opponentDiscordId,
            int reporterScore,
            int opponentScore
    ) {
        validateReportedScore(reporterDiscordId, opponentDiscordId, reporterScore, opponentScore);

        if (reporterScore == 5) {
            return processMatchResult(reporterDiscordId, opponentDiscordId, reporterScore, opponentScore);
        }

        return processMatchResult(opponentDiscordId, reporterDiscordId, opponentScore, reporterScore);
    }

    @Transactional
    public MatchResult processMatchResult(
            long winnerDiscordId,
            long loserDiscordId,
            int winnerScore,
            int loserScore
    ) {
        validateWinnerScore(winnerDiscordId, loserDiscordId, winnerScore, loserScore);

        PlayerEntity winnerPlayer = findPlayerByDiscordId(winnerDiscordId);
        PlayerEntity loserPlayer = findPlayerByDiscordId(loserDiscordId);
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        SeasonPlayerEntity winner = findSeasonPlayer(season, winnerPlayer);
        SeasonPlayerEntity loser = findSeasonPlayer(season, loserPlayer);

        validateSameSeason(season, winner, loser);

        EloCalculator.RatingChange ratingChange = EloCalculator.calculate(
                winner.getRating(),
                winner.getGamesPlayed(),
                loser.getRating(),
                loser.getGamesPlayed(),
                winnerScore,
                loserScore
        );

        int deltaWinner = ratingChange.deltaWinner();
        int deltaLoser = ratingChange.deltaLoser();

        winner.setRating(winner.getRating() + deltaWinner);
        winner.setGamesPlayed(winner.getGamesPlayed() + 1);
        loser.setRating(Math.max(0, loser.getRating() + deltaLoser));
        loser.setGamesPlayed(loser.getGamesPlayed() + 1);

        MatchEntity match = new MatchEntity(
                season,
                winner,
                loser,
                winnerScore,
                loserScore,
                deltaWinner,
                deltaLoser
        );

        seasonPlayerRepository.save(winner);
        seasonPlayerRepository.save(loser);
        MatchEntity saved = matchRepository.save(match);

        log.info("MATCH SUCCESS [#{}]: {} {}:{} {}", saved.getId(),
                winnerPlayer.getDisplayName(), winnerScore, loserScore, loserPlayer.getDisplayName());

        return new MatchResult(
                saved.getId(),
                winnerPlayer.getDiscordId(),
                winnerPlayer.getDisplayName(),
                loserPlayer.getDiscordId(),
                loserPlayer.getDisplayName(),
                winnerScore,
                loserScore,
                deltaWinner,
                deltaLoser,
                winner.getRating(),
                loser.getRating()
        );
    }

    @Transactional
    public void revertMatch(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));

        SeasonPlayerEntity winner = match.getWinner();
        SeasonPlayerEntity loser = match.getLoser();

        validateRollbackState(match, winner, loser);

        winner.setRating(winner.getRating() - match.getDeltaWinner());
        winner.setGamesPlayed(winner.getGamesPlayed() - 1);
        loser.setRating(loser.getRating() - match.getDeltaLoser());
        loser.setGamesPlayed(loser.getGamesPlayed() - 1);

        seasonPlayerRepository.save(winner);
        seasonPlayerRepository.save(loser);
        matchRepository.delete(match);

        log.info("ADMIN ACTION SUCCESS: reverted match #{}", matchId);
    }

    @Transactional(readOnly = true)
    public PageDto<MatchHistoryEntryDto> getMatchHistory(
            long discordId,
            int page,
            int pageSize
    ) {
        PlayerEntity player = findPlayerByDiscordId(discordId);
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        SeasonPlayerEntity seasonPlayer = findSeasonPlayer(season, player);

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), pageSize);

        Page<MatchEntity> matches = matchRepository.findBySeasonAndParticipant(
                season,
                seasonPlayer,
                pageRequest
        );

        List<MatchHistoryEntryDto> content = matches.stream()
                .map(match -> toHistoryEntry(match, seasonPlayer))
                .toList();

        return new PageDto<>(
                content,
                matches.getNumber(),
                matches.getTotalPages(),
                matches.getTotalElements(),
                matches.getSize()
        );
    }

    private MatchHistoryEntryDto toHistoryEntry(MatchEntity match, SeasonPlayerEntity currentPlayer) {
        boolean win = match.getWinner().getId().equals(currentPlayer.getId());
        SeasonPlayerEntity opponent = win ? match.getLoser() : match.getWinner();

        return new MatchHistoryEntryDto(
                win,
                opponent.getPlayer().getDisplayName(),
                win ? match.getWinnerScore() : match.getLoserScore(),
                win ? match.getLoserScore() : match.getWinnerScore(),
                win ? match.getDeltaWinner() : match.getDeltaLoser(),
                match.getCreatedAt()
        );
    }

    private PlayerEntity findPlayerByDiscordId(long discordId) {
        return playerRepository.findByDiscordId(discordId)
                .orElseThrow(() -> new PlayerNotFoundException(discordId));
    }

    private SeasonPlayerEntity findSeasonPlayer(SeasonEntity season, PlayerEntity player) {
        return seasonPlayerRepository.findBySeasonAndPlayer(season, player)
                .orElseThrow(() -> new PlayerNotRegisteredException(player.getDiscordId()));
    }

    private void validateReportedScore(
            long reporterDiscordId,
            long opponentDiscordId,
            int reporterScore,
            int opponentScore
    ) {
        validateDiscordId(reporterDiscordId);
        validateDiscordId(opponentDiscordId);

        if (reporterDiscordId == opponentDiscordId) {
            throw new InvalidMatchException("You cannot report a match against yourself.");
        }

        if (!((reporterScore == 5 && opponentScore >= 0 && opponentScore < 5)
                || (opponentScore == 5 && reporterScore >= 0 && reporterScore < 5))) {
            throw new InvalidMatchException("Invalid FT5 score. Exactly one player must have 5 wins.");
        }
    }

    private void validateWinnerScore(
            long winnerDiscordId,
            long loserDiscordId,
            int winnerScore,
            int loserScore
    ) {
        validateDiscordId(winnerDiscordId);
        validateDiscordId(loserDiscordId);

        if (winnerDiscordId == loserDiscordId) {
            throw new InvalidMatchException("You cannot play a ranked match against yourself.");
        }

        if (winnerScore != 5 || loserScore < 0 || loserScore > 4 || winnerScore <= loserScore) {
            throw new InvalidMatchException("Invalid FT5 score. Winner must have 5 wins and loser must have 0-4 wins.");
        }
    }

    private void validateSameSeason(
            SeasonEntity season,
            SeasonPlayerEntity winner,
            SeasonPlayerEntity loser
    ) {
        if (winner.getId().equals(loser.getId())) {
            throw new InvalidMatchException("Winner and loser must be different players.");
        }

        Long seasonId = season.getId();
        if (!seasonId.equals(winner.getSeason().getId()) || !seasonId.equals(loser.getSeason().getId())) {
            throw new InvalidMatchException("Match participants must belong to the active season.");
        }
    }

    private void validateRollbackState(
            MatchEntity match,
            SeasonPlayerEntity winner,
            SeasonPlayerEntity loser
    ) {
        validateSameSeason(match.getSeason(), winner, loser);

        if (winner.getGamesPlayed() <= 0 || loser.getGamesPlayed() <= 0) {
            throw new InvalidMatchException("Cannot rollback match because participant gamesPlayed would become negative.");
        }

        int restoredWinnerRating = winner.getRating() - match.getDeltaWinner();
        int restoredLoserRating = loser.getRating() - match.getDeltaLoser();
        if (restoredWinnerRating < 0 || restoredLoserRating < 0) {
            throw new InvalidMatchException("Cannot rollback match because participant rating would become negative.");
        }
    }

    private void validateDiscordId(long discordId) {
        if (discordId <= 0) {
            throw new InvalidMatchException("Discord ID must be positive.");
        }
    }
}
