package com.mkx.ranked.service;

import com.mkx.ranked.config.DatabaseManager;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.repository.PlayerRepository;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeasonService {

    private static final Logger log =
            LoggerFactory.getLogger(SeasonService.class);

    private final PlayerRepository playerRepository;

    public SeasonService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Получает текущий активный сезон.
     */
    public SeasonEntity getCurrentSeason() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "FROM SeasonEntity s WHERE s.endDate IS NULL ORDER BY s.seasonNumber DESC";
            Query<SeasonEntity> query = session.createQuery(hql, SeasonEntity.class);
            query.setMaxResults(1);

            return query.uniqueResultOptional()
                    .orElseThrow(() -> new IllegalStateException("Активный сезон не найден! Создайте новый сезон через админ-панель."));
        }
    }

    /**
     * Создает и открывает НОВЫЙ сезон с плановой датой окончания.
     */
    public SeasonEntity createNewSeason(String name, LocalDateTime plannedEndDate) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            String hql = "SELECT MAX(s.seasonNumber) FROM SeasonEntity s";
            Query<Integer> query = session.createQuery(hql, Integer.class);
            Integer maxSeasonNumber = query.uniqueResult();
            int nextSeasonNumber = (maxSeasonNumber == null) ? 1 : maxSeasonNumber + 1;

            SeasonEntity newSeason = new SeasonEntity(nextSeasonNumber, name, plannedEndDate);
            session.persist(newSeason);

            tx.commit();
            log.info("SEASON SUCCESS: Создан новый Сезон #{} ('{}'), Плановый конец: {}",
                    nextSeasonNumber, name, plannedEndDate);
            return newSeason;
        } catch (Exception e) {
            log.error("SEASON ERROR: Ошибка при создании нового сезона", e);
            throw e;
        }
    }

    /**
     * Позволяет админу изменить планируемую дату окончания текущего сезона.
     */
    public boolean updatePlannedEndDate(LocalDateTime newPlannedEndDate) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            SeasonEntity currentSeason = getCurrentSeason();
            currentSeason.setPlannedEndDate(newPlannedEndDate);
            session.merge(currentSeason);

            tx.commit();
            log.info("SEASON SUCCESS: Изменена планируемая дата окончания сезона #{} на {}",
                    currentSeason.getSeasonNumber(), newPlannedEndDate);
            return true;
        } catch (Exception e) {
            log.error("SEASON ERROR: Не удалось обновить плановую дату окончания", e);
            return false;
        }
    }

    /**
     * Завершает текущий активный сезон:
     * 1. Фиксирует дату закрытия
     * 2. Архивирует результаты игроков в season_history
     * 3. Сбрасывает статистику активных игроков
     */
    public boolean endCurrentSeason() {
        log.warn("ADMIN ACTION: Запущен процесс закрытия текущего сезона!");

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            SeasonEntity currentSeason = getCurrentSeason();

            // 1. Фиксируем дату закрытия сезона
            currentSeason.setEndDate(LocalDateTime.now());
            session.merge(currentSeason);

            // 2. Сохраняем снимки рейтинга в Зал Славы (season_history)
            archiveSeasonHistory(session, currentSeason);

            // 3. Вызываем отдельный метод сброса статистики игроков
            playerRepository.resetAllPlayerStats(session);

            tx.commit();
            log.info("SEASON SUCCESS: Сезон #{} успешно закрыт.", currentSeason.getSeasonNumber());
            return true;
        } catch (Exception e) {
            log.error("SEASON ERROR: Ошибка при завершении сезона", e);
            return false;
        }
    }

    /**
     * Вспомогательный приватный метод для архивации профилей игроков текущего сезона.
     */
    private void archiveSeasonHistory(Session session, SeasonEntity currentSeason) {
        List<PlayerEntity> sortedPlayers = playerRepository.getAllPlayersSorted();
        int currentRank = 1;

        for (PlayerEntity player : sortedPlayers) {
            SeasonHistoryEntity history = new SeasonHistoryEntity();
            history.setSeason(currentSeason);
            history.setDiscordId(player.getDiscordId());
            history.setUsername(player.getDisplayName());
            history.setFinalRating(player.getRating());
            history.setGamesPlayed(player.getGamesPlayed());
            history.setFinalRank(currentRank++);

            session.persist(history);
        }
    }
}