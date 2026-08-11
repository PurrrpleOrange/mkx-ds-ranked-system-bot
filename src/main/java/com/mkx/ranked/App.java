package com.mkx.ranked;

import com.mkx.ranked.config.DatabaseManager;
import com.mkx.ranked.discord.listeners.MatchConfirmationListener;
import com.mkx.ranked.discord.listeners.ModalInteractionListener;
import com.mkx.ranked.discord.listeners.RankedCommandListener;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.service.MatchService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный класс и точка входа приложения MKX Ranked Bot.
 *
 * <p>Класс отвечает за следующий цикл запуска:</p>
 * <ul>
 * <li>Инициализирует пул соединений с базой данных Supabase через Hibernate {@link DatabaseManager}.</li>
 * <li>Считывает токен бота из переменной окружения {@code DISCORD_BOT_TOKEN}.</li>
 * <li>Авторизует JDA-клиента и регистрирует слушатели событий (listeners).</li>
 * <li>Глобально регистрирует слэш-команду {@code /ranked} в Discord API.</li>
 * <li>Настраивает корректное завершение работы (Shutdown Hook) для закрытия соединений с БД.</li>
 * </ul>
 *
 * @author MKX Ranked Bot Team
 * @version 1.0
 */
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);


    public static void main(String[] args) {
        log.info("Starting MKX Ranked Bot...");

        PlayerRepository playerRepository = new PlayerRepository();
        MatchService matchService = new MatchService();

        // 1. Инициализация подключения к PostgreSQL (Supabase)
        try {
            DatabaseManager.init();
        } catch (Exception e) {
            log.error("CRITICAL: Не удалось подключиться к базе данных. Запуск бота отменён.", e);
            System.exit(1);
        }

        // !!!!!!!!!!!!!!!!!!!!!!!! 2. Потом убрать отсюда !!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        String botToken = System.getenv("DISCORD_TOKEN");

        try {
            // 3. Создание и запуск JDA клиента
            JDA jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
                    .addEventListeners(
                            new RankedCommandListener(), // Регистрируем наш слушатель /ranked
                            new ModalInteractionListener(playerRepository), // Регистрируем наш слушатель модальных окон
                            new MatchConfirmationListener(matchService)
                    )
                    .build();

            // Ожидаем полной загрузки бота и авторизации в Discord
            jda.awaitReady();
            log.info("✅ Бот успешно авторизован под ником: {}", jda.getSelfUser().getAsTag());

            // 4. Регистрация глобальной слэш-команды /ranked в Discord
            jda.updateCommands().addCommands(
                    Commands.slash("ranked", "Открыть главное рейтинговое меню MKX Season")
            ).queue(
                    commands -> log.info("✅ Слэш-команда /ranked успешно зарегистрирована!"),
                    error -> log.error("❌ Ошибка при регистрации слэш-команды /ranked", error)
            );

            // 5. Настройка Graceful Shutdown (закрытие пула БД при остановке программы)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Завершение работы бота... Закрытие ресурсов.");
                DatabaseManager.shutdown();
                jda.shutdown();
            }));

        } catch (InterruptedException e) {
            log.error("Процесс ожидания подключения JDA был прерван", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("CRITICAL: Неизвестная ошибка при запуске Discord бота", e);
        }
    }
}