package com.mkx.ranked.service;

import com.mkx.ranked.config.DatabaseManager;
import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.repository.PlayerRepository;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Сервис для управления проведением FT5 (First to 5) сетов, расчетом изменений рейтингов Elo
 * и административной отмены результатов матчей.
 *
 * <p>Сервис отвечает за целостность данных во время проведения матча и гарантирует,
 * что оба участника зарегистрированы в системе перед началом обработки.</p>

 * @author MKX Ranked Bot Team
 * @version 1.2
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private final PlayerRepository playerRepository;

    public MatchService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Проведение FT5 сета, перерасчет MMR очков и запись результатов матча в историю.
     *
     * <p>Метод выполняет транзакционное обновление рейтингов и сыгранных матчей для обоих игроков.
     * Ожидается, что оба игрока предварительно прошли процедуру регистрации в системе.</p>
     *
     * @param winnerDiscordId Discord ID победителя
     * @param winnerUsername Текущий Discord handle победителя (@username)
     * @param loserDiscordId Discord ID проигравшего
     * @param loserUsername Текущий Discord handle проигравшего (@username)
     * @param winnerScore Количество побед победителя (должно быть строго 5)
     * @param loserScore Количество побед проигравшего (от 0 до 4)
     * @return {@link MatchResult} объект с подробными результатами и ID матча
     * @throws IllegalArgumentException Если валидация счета не пройдена или игрок пытается сыграть сам с собой
     * @throws IllegalStateException Если один из участников матча не зарегистрирован в ладдере
     */
    public MatchResult processMatchResult(Long winnerDiscordId, String winnerUsername,
                                          Long loserDiscordId, String loserUsername,
                                          int winnerScore, int loserScore) {

        // 1. Валидация входных данных
        if (winnerDiscordId.equals(loserDiscordId)) {
            log.warn("MATCH WARN: Попытка сыграть матч с самим собой (discordId={})", winnerDiscordId);
            throw new IllegalArgumentException("Нельзя сыграть матч с самим собой!");
        }

        if (winnerScore != 5 || loserScore < 0 || loserScore > 4) {
            log.warn("MATCH WARN: Невалидный счет FT5 [{}:{}] между {} и {}",
                    winnerScore, loserScore, winnerDiscordId, loserDiscordId);
            throw new IllegalArgumentException("Некорректный счёт FT5! Победитель должен иметь 5 побед, а проигравший от 0 до 4.");
        }

        // 2. Проверка регистрации участников
        Optional<PlayerEntity> winnerOpt = playerRepository.findById(winnerDiscordId);
        Optional<PlayerEntity> loserOpt = playerRepository.findById(loserDiscordId);

        if (winnerOpt.isEmpty()) {
            log.warn("MATCH WARN: Победитель discordId={} не зарегистрирован в базе!", winnerDiscordId);
            throw new IllegalStateException("Вы ещё не зарегистрированы в ладдере! Напишите `/ranked` для регистрации.");
        }

        if (loserOpt.isEmpty()) {
            log.warn("MATCH WARN: Соперник discordId={} не зарегистрирован в базе!", loserDiscordId);
            throw new IllegalStateException("Указанный соперник ещё не зарегистрирован в ладдере!");
        }

        PlayerEntity winner = winnerOpt.get();
        PlayerEntity loser = loserOpt.get();

        // Актуализация Discord Handle на случай, если пользователь сменил ник в Discord
        if (!winner.getUsername().equals(winnerUsername)) {
            winner.setUsername(winnerUsername);
        }
        if (!loser.getUsername().equals(loserUsername)) {
            loser.setUsername(loserUsername);
        }

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            // 3. Расчет изменения MMR через EloCalculator
            int[] deltas = EloCalculator.calculateRatingChange(
                    winner.getRating(), winner.getGamesPlayed(),
                    loser.getRating(), loser.getGamesPlayed(),
                    winnerScore, loserScore
            );

            int deltaWinner = deltas[0];
            int deltaLoser = deltas[1];

            // 4. Обновление показателей игроков
            winner.setRating(winner.getRating() + deltaWinner);
            winner.setGamesPlayed(winner.getGamesPlayed() + 1);

            loser.setRating(loser.getRating() + deltaLoser);
            loser.setGamesPlayed(loser.getGamesPlayed() + 1);

            session.merge(winner);
            session.merge(loser);

            // 5. Фиксация матча в истории
            MatchEntity match = new MatchEntity();
            match.setWinner(winner);
            match.setLoser(loser);
            match.setWinnerScore(winnerScore);
            match.setLoserScore(loserScore);
            match.setDeltaWinner(deltaWinner);
            match.setDeltaLoser(deltaLoser);

            session.persist(match);

            tx.commit();

            log.info("MATCH SUCCESS [#{}]: {} (new MMR: {}) [{} : {}] {} (new MMR: {}). Delta: +{}/{}",
                    match.getId(),
                    winner.getDisplayName(), winner.getRating(),
                    winnerScore, loserScore,
                    loser.getDisplayName(), loser.getRating(),
                    deltaWinner, deltaLoser);

            return new MatchResult(
                    match.getId(), // или как у тебя называется геттер ID в MatchEntity
                    winner.getDiscordId(),
                    loser.getDiscordId(),
                    deltaWinner, // рассчитанное изменение MMR для победителя (положительное число)
                    deltaLoser,  // рассчитанное изменение MMR для проигравшего (отрицательное число)
                    winner.getRating(), // новый рейтинг победителя после сохранения
                    loser.getRating()   // новый рейтинг проигравшего после сохранения
            );
        } catch (Exception e) {
            log.error("MATCH ERROR: Сбой при проведении матча между {} и {}", winnerDiscordId, loserDiscordId, e);
            throw e;
        }
    }

    /**
     * Административный откат внесенного матча по его идентификатору за O(1).
     * Восстанавливает прежний рейтинг и количество игр участников, после чего удаляет запись о матче.
     *
     * @param matchId Уникальный идентификатор отменяемого матча
     * @return {@code true}, если матч был успешно найден и отменен; {@code false} в случае ошибки
     */
    public boolean revertMatch(Long matchId) {
        log.warn("ADMIN ACTION: Попытка отката матча #{}", matchId);

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            MatchEntity match = session.get(MatchEntity.class, matchId);
            if (match == null) {
                log.warn("ADMIN ACTION WARN: Матч #{} не найден для отката", matchId);
                tx.rollback();
                return false;
            }

            PlayerEntity winner = match.getWinner();
            PlayerEntity loser = match.getLoser();

            // Откат рейтингов
            winner.setRating(winner.getRating() - match.getDeltaWinner());
            winner.setGamesPlayed(Math.max(0, winner.getGamesPlayed() - 1));

            loser.setRating(loser.getRating() - match.getDeltaLoser());
            loser.setGamesPlayed(Math.max(0, loser.getGamesPlayed() - 1));

            session.merge(winner);
            session.merge(loser);

            // Удаление записи матча
            session.remove(match);

            tx.commit();

            log.info("ADMIN ACTION SUCCESS: Матч #{} отменен. MMR и статистика игроков {}, {} восстановлены.",
                    matchId, winner.getDisplayName(), loser.getDisplayName());
            return true;
        } catch (Exception e) {
            log.error("ADMIN ACTION ERROR: Ошибка при откате матча #{}", matchId, e);
            return false;
        }
    }
}