package com.mkx.ranked.repository;

import com.mkx.ranked.config.DatabaseManager;
import com.mkx.ranked.model.PlayerEntity;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для управления данными игроков в СУБД Supabase (PostgreSQL) с использованием Hibernate.
 *
 * <p>Класс предоставляет API для выполнения CRUD-операций с сущностью {@link PlayerEntity},
 * обработки процессов аутентификации/привязки импортированных профилей, изменения игровых никнеймов,
 * а также расчета рангов и сброса показателей перед началом нового сезона.</p>

 * @author MKX Ranked Bot Team
 * @version 1.3
 */
public class PlayerRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerRepository.class);



    /**
     * Выполняет поиск игрока по его реальному Discord Snowflake ID.
     *
     * @param discordId Уникальный идентификатор пользователя в Discord (где discord_id > 0)
     * @return {@link Optional}, содержащий {@link PlayerEntity}, если игрок найден, или пустой {@link Optional}
     */
    public Optional<PlayerEntity> findById(Long discordId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            PlayerEntity player = session.get(PlayerEntity.class, discordId);
            return Optional.ofNullable(player);
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при поиске игрока findById={}", discordId, e);
            return Optional.empty();
        }
    }




    /**
     * Ищет не привязанный импортированный профиль с временно отрицательным ID по игровому никнейму.
     * Сравнение происходит без учёта регистра символов (case-insensitive).
     *
     * @param nickname Игровой никнейм MKX, введенный пользователем при регистрации
     * @return {@link Optional}, содержащий {@link PlayerEntity} с discord_id < 0, если профиль найден
     */
    public Optional<PlayerEntity> findUnclaimedByNickname(String nickname) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "FROM PlayerEntity p WHERE LOWER(p.displayName) = LOWER(:nickname) AND p.discordId < 0";
            Query<PlayerEntity> query = session.createQuery(hql, PlayerEntity.class);
            query.setParameter("nickname", nickname.trim());
            return query.uniqueResultOptional();
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при поиске unclaimed профиля по нику='{}'", nickname, e);
            return Optional.empty();
        }
    }




    /**
     * Проверяет, занят ли игровой никнейм уже зарегистрированным пользователем Discord.
     *
     * @param nickname Проверяемый игровой никнейм
     * @return {@code true}, если игровой ник уже привязан к пользователю с discord_id > 0, иначе {@code false}
     */
    public boolean isDisplayNameTaken(String nickname) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(p) FROM PlayerEntity p WHERE LOWER(p.displayName) = LOWER(:nickname) AND p.discordId > 0";
            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("nickname", nickname.trim());
            return query.getSingleResult() > 0;
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка проверки уникальности ника='{}'", nickname, e);
            return false;
        }
    }




    /**
     * Возвращает общее количество зарегистрированных игроков.
     *
     * @return количество игроков
     */
    public long countAllPlayers() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {

            String hql = "SELECT COUNT(p) FROM PlayerEntity p";

            return session
                    .createQuery(hql, Long.class)
                    .getSingleResult();

        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при подсчёте игроков", e);
            throw e;
        }
    }




    /**
     * Возвращает страницу игроков, отсортированных по рейтингу MMR
     * от наибольшего к наименьшему.
     *
     * @param page номер страницы, начиная с 0
     * @param pageSize количество игроков на странице
     * @return список игроков для указанной страницы
     * @throws IllegalArgumentException если page отрицательный
     *         или pageSize меньше либо равен 0
     */
    public List<PlayerEntity> getPlayersPaged(int page, int pageSize) {
        if (page < 0) {
            throw new IllegalArgumentException("page не может быть отрицательным");
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize должен быть больше 0");
        }

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {

            String hql = """
                FROM PlayerEntity
                ORDER BY rating DESC, gamesPlayed DESC
                """;

            Query<PlayerEntity> query =
                    session.createQuery(hql, PlayerEntity.class);

            query.setFirstResult(page * pageSize);
            query.setMaxResults(pageSize);

            return query.getResultList();

        } catch (Exception e) {
            log.error(
                    "DB ERROR: Ошибка при получении страницы игроков. page={}, pageSize={}",
                    page,
                    pageSize,
                    e
            );

            throw e;
        }
    }




    /**
     * Атомарно привязывает импортированный профиль из Зала Славы (с отрицательным ID)
     * к настоящему Discord ID пользователя.
     *
     * <p>Обновляет идентификатор {@code discord_id} и имя пользователя {@code username} в таблице {@code players},
     * а также каскадно синхронизирует исторические записи в таблице {@code season_history}.</p>
     *
     * @param actualDiscordId Настоящий Discord ID (Snowflake) пользователя
     * @param actualUsername Имя пользователя в Discord (@handle)
     * @param negativeId Временный отрицательный ID импортированного профиля
     * @return {@code true}, если привязка прошла успешно; {@code false} в случае ошибки или отката транзакции
     */
    public boolean claimProfileByNegativeId(Long actualDiscordId, String actualUsername, Long negativeId) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            PlayerEntity unclaimed = session.get(PlayerEntity.class, negativeId);
            if (unclaimed == null || unclaimed.getDiscordId() >= 0) {
                log.warn("CLAIM WARN: Профиль с oldId={} не существует или уже привязан", negativeId);
                tx.rollback();
                return false;
            }

            String mkNickname = unclaimed.getDisplayName();

            // 1. Атомарно обновляем discord_id и username в таблице players
            String updatePlayersSql = "UPDATE players SET discord_id = :newId, username = :username WHERE discord_id = :oldId";
            session.createNativeQuery(updatePlayersSql, Integer.class)
                    .setParameter("newId", actualDiscordId)
                    .setParameter("username", actualUsername)
                    .setParameter("oldId", negativeId)
                    .executeUpdate();

            // 2. Обновляем discord_id в Зале Славы (season_history)
            String updateHistorySql = "UPDATE season_history SET discord_id = :newId " +
                    "WHERE LOWER(mk_nickname) = LOWER(:nickname) AND (discord_id = 0 OR discord_id = :oldId)";
            session.createNativeQuery(updateHistorySql, Integer.class)
                    .setParameter("newId", actualDiscordId)
                    .setParameter("nickname", mkNickname.trim())
                    .setParameter("oldId", negativeId)
                    .executeUpdate();

            tx.commit();
            log.info("CLAIM SUCCESS: Профиль '{}' (old_id={}) привязан к discordId={}", mkNickname, negativeId, actualDiscordId);
            return true;
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при claimProfileByNegativeId (old_id={})", negativeId, e);
            return false;
        }
    }




    /**
     * Создает и сохраняет профиль нового игрока со стартовым рейтингом 1000 MMR.
     *
     * @param discordId Настоящий Discord ID пользователя
     * @param username Discord-хэндл пользователя (@username)
     * @param displayName Выбранный игровой никнейм
     * @return Созданная сущность {@link PlayerEntity}
     */
    public PlayerEntity createNewPlayer(Long discordId, String username, String displayName) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            PlayerEntity newPlayer = new PlayerEntity(discordId, username);
            newPlayer.setDisplayName(displayName.trim());
            newPlayer.setRating(1000);
            newPlayer.setGamesPlayed(0);

            session.persist(newPlayer);
            tx.commit();

            log.info("REGISTER SUCCESS: Создан новый игрок: discordId={}, displayName='{}'", discordId, displayName.trim());
            return newPlayer;
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при создании нового игрока discordId={}", discordId, e);
            throw e;
        }
    }




    /**
     * Обновляет отображаемый игровой никнейм игрока.
     *
     * @param discordId Discord ID пользователя
     * @param newDisplayName Новое имя игрока
     * @return {@code true}, если запись была найдена и обновлена, иначе {@code false}
     */
    public boolean updateDisplayName(Long discordId, String newDisplayName) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            PlayerEntity player = session.get(PlayerEntity.class, discordId);
            if (player != null) {
                String oldName = player.getDisplayName();
                player.setDisplayName(newDisplayName.trim());
                session.merge(player);
                tx.commit();
                log.info("NICKNAME CHANGE: discordId={} изменил игровой ник: '{}' -> '{}'", discordId, oldName, newDisplayName.trim());
                return true;
            }

            tx.rollback();
            log.warn("NICKNAME CHANGE WARN: Игрок с discordId={} не найден для смены ника", discordId);
            return false;
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при смене ника для discordId={}", discordId, e);
            return false;
        }
    }




    /**
     * Сохраняет или обновляет состояние сущности игрока в базе данных.
     *
     * @param player Сущность {@link PlayerEntity} для сохранения
     */
    public void saveOrUpdate(PlayerEntity player) {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.merge(player);
            tx.commit();
            log.debug("DB: Сохранены изменения для discordId={}", player.getDiscordId());
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка сохранения игрока discordId={}", player.getDiscordId(), e);
            throw e;
        }
    }




    /**
     * Возвращает полный список игроков ладдера, отсортированный по спортивному регламенту:
     * <ol>
     * <li>По убыванию MMR ({@code rating DESC})</li>
     * <li>При равном MMR — по игровому никнейму в алфавитном порядке без учета регистра ({@code LOWER(displayName) ASC})</li>
     * </ol>
     *
     * @return Отсортированный список всех {@link PlayerEntity}
     */
    public List<PlayerEntity> getAllPlayersSorted() {
        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "FROM PlayerEntity p ORDER BY p.rating DESC, LOWER(p.displayName) ASC";
            Query<PlayerEntity> query = session.createQuery(hql, PlayerEntity.class);
            return query.getResultList();
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при получении отсортированного списка игроков", e);
            throw e;
        }
    }




    /**
     * Динамически рассчитывает порядковое место (Rank) игрока в общей таблице лидеров.
     *
     * @param discordId Discord ID проверяемого игрока
     * @return Порядковое место (начиная с 1) или {@code 0}, если игрок не найден
     */
    public int calculateRank(Long discordId) {
        Optional<PlayerEntity> targetOpt = findById(discordId);
        if (targetOpt.isEmpty()) {
            return 0;
        }

        PlayerEntity target = targetOpt.get();

        try (Session session = DatabaseManager.getSessionFactory().openSession()) {
            String hql = "SELECT COUNT(p) FROM PlayerEntity p WHERE p.rating > :rating " +
                    "OR (p.rating = :rating AND LOWER(p.displayName) < LOWER(:displayName))";

            Query<Long> query = session.createQuery(hql, Long.class);
            query.setParameter("rating", target.getRating());
            query.setParameter("displayName", target.getDisplayName());

            Long countAbove = query.getSingleResult();
            return countAbove.intValue() + 1;
        } catch (Exception e) {
            log.error("DB ERROR: Ошибка при вычислении ранга для discordId={}", discordId, e);
            return 0;
        }
    }




    /**
     * Сбрасывает показатели всех игроков (MMR = 1000, games_played = 0) при завершении сезона.
     * Метод выполняет SQL-запрос в рамках уже открытой Hibernate-сессии и транзакции.
     *
     * @param session Текущая открытая сессия Hibernate
     */
    public void resetAllPlayerStats(Session session) {
        String resetPlayersSql = "UPDATE players SET rating = 1000, games_played = 0";
        session.createNativeQuery(resetPlayersSql, Integer.class).executeUpdate();
        log.info("DB: Показатели всех игроков успешно сброшены (R=1000, G=0).");
    }
}