package com.mkx.ranked.repository;

import com.mkx.ranked.config.DatabaseManager;
import com.mkx.ranked.model.MatchEntity;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для выполнения CRUD-операций с сущностью {@link MatchEntity} в СУБД Supabase (PostgreSQL).
 *
 * <p>Отвечает за сохранение сыгранных FT5 сетов, выборку истории матчей конкретного игрока
 * с поддержкой пагинации, а также поиск матчей по ID для административного отката.</p>
 *
 * @author MKX Ranked Bot Team
 * @version 1.0
 */
@Repository
public class MatchRepository {

    private static final Logger log = LoggerFactory.getLogger(MatchRepository.class);

    /**
     * Сохраняет новый сыгранный матч в базу данных.
     *
     * @param match Сущность {@link MatchEntity} для сохранения
     */
    public void save(MatchEntity match) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.persist(match);
            tx.commit();
            log.info("DB: Сохранен матч ID={} между winnerId={} и loserId={}",
                    match.getId(), match.getWinner().getDiscordId(), match.getLoser().getDiscordId());
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при сохранении матча", e);
            throw e;
        }
    }

    /**
     * Поиск матча по его идентификатору.
     *
     * @param matchId ID матча
     * @return {@link Optional} с найденной записью
     */
    public Optional<MatchEntity> findById(Long matchId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            MatchEntity match = session.get(MatchEntity.class, matchId);
            return Optional.ofNullable(match);
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при поиске матча matchId={}", matchId, e);
            return Optional.empty();
        }
    }

    /**
     * Удаляет запись о матче из базы данных (используется при откате).
     *
     * @param match Сущность {@link MatchEntity} для удаления
     */
    public void delete(MatchEntity match) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.remove(session.contains(match) ? match : session.merge(match));
            tx.commit();
            log.info("DB: Удален матч ID={}", match.getId());
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при удалении матча matchId={}", match.getId(), e);
            throw e;
        }
    }

    /**
     * Подсчитывает общее количество матчей конкретного игрока в текущем сезоне.
     *
     * @param discordId Discord ID игрока
     * @return Количество сыгранных сетов
     */
    public long countTotalMatchesByPlayer(Long discordId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(m) FROM MatchEntity m WHERE m.winner.discordId = :id OR m.loser.discordId = :id";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("id", discordId);
            return query.getSingleResult();
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при подсчете матчей для discordId={}", discordId, e);
            return 0;
        }
    }

    /**
     * Возвращает страницу матчей, в которых указанный игрок принимал участие,
     * отсортированных от самых свежих к самым старым.
     *
     * <p>Вместе с {@link MatchEntity} принудительно загружаются связанные
     * {@link PlayerEntity} победителя и проигравшего. Это необходимо, поскольку
     * связи {@code winner} и {@code loser} имеют {@link FetchType#LAZY},
     * а {@code Session} закрывается после выполнения запроса.</p>
     *
     * <p>Пагинация начинается с нулевой страницы:
     * {@code page = 0} — первая страница.</p>
     *
     * @param discordId Discord ID игрока, чью историю необходимо получить
     * @param page номер страницы, начиная с {@code 0}
     * @param pageSize количество матчей на одной странице
     * @return список матчей указанной страницы; список может быть пустым,
     * если на странице нет матчей
     * @throws IllegalArgumentException если {@code discordId == null},
     *         {@code page < 0} или {@code pageSize <= 0}
     */
    public List<MatchEntity> getPlayerMatchesPaged(
            Long discordId,
            int page,
            int pageSize
    ) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {

            String hql = """
                SELECT m
                FROM MatchEntity m
                JOIN FETCH m.winner
                JOIN FETCH m.loser
                WHERE m.winner.discordId = :id
                   OR m.loser.discordId = :id
                ORDER BY m.createdAt DESC
                """;

            Query<MatchEntity> query =
                    session.createQuery(hql, MatchEntity.class);

            query.setParameter("id", discordId);
            query.setFirstResult(page * pageSize);
            query.setMaxResults(pageSize);

            return query.getResultList();

        } catch (Exception e) {
            log.error(
                    "DB ERROR: Ошибка при получении пагинированных матчей для discordId={}",
                    discordId,
                    e
            );
            throw e;
        }
    }
}