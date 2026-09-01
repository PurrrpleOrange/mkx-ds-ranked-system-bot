package com.mkx.ranked.service;

import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.model.SeasonPlayerEntity;
import com.mkx.ranked.model.dto.LeaderboardEntryDto;
import com.mkx.ranked.model.dto.PageDto;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.RegistrationProfileDto;
import com.mkx.ranked.model.dto.RegistrationReviewDto;
import com.mkx.ranked.model.enums.RankTier;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.repository.SeasonPlayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
public class PlayerService {

    private static final int DEFAULT_RATING = 1000;

    private final PlayerRepository playerRepository;
    private final SeasonPlayerRepository seasonPlayerRepository;
    private final SeasonService seasonService;

    public PlayerService(
            PlayerRepository playerRepository,
            SeasonPlayerRepository seasonPlayerRepository,
            SeasonService seasonService
    ) {
        this.playerRepository = playerRepository;
        this.seasonPlayerRepository = seasonPlayerRepository;
        this.seasonService = seasonService;
    }

    @Transactional(readOnly = true)
    public boolean isRegistered(long discordId) {
        return playerRepository.existsByDiscordId(discordId);
    }

    @Transactional(readOnly = true)
    public PlayerProfileDto getProfile(long discordId) {
        PlayerEntity player = findPlayerByDiscordId(discordId);
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        SeasonPlayerEntity seasonPlayer = findSeasonPlayer(season, player);
        int rank = calculateRank(season, seasonPlayer);
        RankTier tier = RankTier.getTierByRank(rank);

        return new PlayerProfileDto(
                player.getId(),
                player.getDiscordId(),
                player.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                rank,
                tier.getName(),
                tier.getEmoji(),
                seasonService.toDto(season)
        );
    }

    @Transactional(readOnly = true)
    public RegistrationReviewDto reviewRegistration(String requestedNickname) {
        String nickname = requestedNickname.trim();

        Optional<RegistrationProfileDto> candidate = Optional.empty();
        if (!playerRepository.existsByDisplayNameIgnoreCaseAndDiscordIdIsNotNull(nickname)) {
            candidate = playerRepository.findFirstByDisplayNameIgnoreCaseAndDiscordIdIsNull(nickname)
                    .map(this::toRegistrationProfile);
        }

        return new RegistrationReviewDto(nickname, candidate);
    }

    @Transactional(readOnly = true)
    public boolean isClaimedDisplayName(String requestedNickname) {
        return playerRepository.existsByDisplayNameIgnoreCaseAndDiscordIdIsNotNull(requestedNickname.trim());
    }

    @Transactional
    public boolean claimProfile(long discordId, String discordUsername, long playerId) {
        Optional<PlayerEntity> profileOpt = playerRepository.findById(playerId);

        if (profileOpt.isEmpty() || profileOpt.get().getDiscordId() != null) {
            return false;
        }

        PlayerEntity profile = profileOpt.get();
        profile.setDiscordId(discordId);
        profile.setUsername(discordUsername);
        playerRepository.save(profile);
        ensureActiveSeasonPlayer(profile);
        return true;
    }

    @Transactional
    public PlayerProfileDto createNewPlayer(
            long discordId,
            String discordUsername,
            String displayName
    ) {
        PlayerEntity player = new PlayerEntity(discordUsername, displayName.trim());
        player.setDiscordId(discordId);
        playerRepository.save(player);
        ensureActiveSeasonPlayer(player);
        return getProfile(discordId);
    }

    @Transactional(readOnly = true)
    public PageDto<LeaderboardEntryDto> getLeaderboard(int page, int pageSize) {
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                pageSize,
                Sort.by(Sort.Direction.DESC, "rating")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Page<SeasonPlayerEntity> standings =
                seasonPlayerRepository.findAllBySeason(season, pageRequest);

        int offset = standings.getNumber() * standings.getSize();
        List<LeaderboardEntryDto> content = IntStream.range(0, standings.getContent().size())
                .mapToObj(i -> toLeaderboardEntry(standings.getContent().get(i), offset + i))
                .toList();

        return new PageDto<>(
                content,
                standings.getNumber(),
                standings.getTotalPages(),
                standings.getTotalElements(),
                standings.getSize()
        );
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDto> getFullLeaderboard() {
        SeasonEntity season = seasonService.getCurrentSeasonEntity();
        List<SeasonPlayerEntity> standings =
                seasonPlayerRepository.findAllBySeasonOrderByRatingDesc(season);

        return IntStream.range(0, standings.size())
                .mapToObj(i -> toLeaderboardEntry(standings.get(i), i))
                .toList();
    }

    private void ensureActiveSeasonPlayer(PlayerEntity player) {
        SeasonEntity season = seasonService.getCurrentSeasonEntity();

        if (!seasonPlayerRepository.existsBySeasonAndPlayer(season, player)) {
            seasonPlayerRepository.save(new SeasonPlayerEntity(player, season));
        }
    }

    private RegistrationProfileDto toRegistrationProfile(PlayerEntity player) {
        Optional<SeasonPlayerEntity> latestSeasonPlayer =
                seasonPlayerRepository.findAllByPlayerOrderBySeason_SeasonNumberDesc(player)
                        .stream()
                        .findFirst();

        return new RegistrationProfileDto(
                player.getId(),
                player.getDisplayName(),
                latestSeasonPlayer.map(SeasonPlayerEntity::getRating).orElse(DEFAULT_RATING),
                latestSeasonPlayer.map(SeasonPlayerEntity::getGamesPlayed).orElse(0)
        );
    }

    private LeaderboardEntryDto toLeaderboardEntry(SeasonPlayerEntity seasonPlayer, int zeroBasedRank) {
        int rank = zeroBasedRank + 1;
        RankTier tier = RankTier.getTierByRank(rank);
        PlayerEntity player = seasonPlayer.getPlayer();

        return new LeaderboardEntryDto(
                rank,
                player.getId(),
                player.getDiscordId(),
                player.getDisplayName(),
                seasonPlayer.getRating(),
                seasonPlayer.getGamesPlayed(),
                tier.getName(),
                tier.getEmoji()
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

    private int calculateRank(SeasonEntity season, SeasonPlayerEntity seasonPlayer) {
        return (int) seasonPlayerRepository.countBySeasonAndRatingGreaterThan(season, seasonPlayer.getRating()) + 1;
    }
}
