package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.model.MatchEntity;
import com.mkx.ranked.model.PlayerEntity;
import com.mkx.ranked.model.RankTier;
import com.mkx.ranked.model.SeasonEntity;
import com.mkx.ranked.repository.MatchRepository;
import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.service.SeasonService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Слушатель событий Discord JDA, обрабатывающий главную слэш-команду {@code /ranked},
 * интерактивное приватное Embed-меню, интерактивные кнопки, а также процесс регистрации и авто-привязки профиля.
 *
 * <p>Каждое взаимодействие отправляется в приватном (Ephemeral) режиме для обеспечения удобства пользователя
 * и защиты от замусоривания общего чата.</p>

 * @author MKX Ranked Bot Team
 * @version 1.3
 */
public class RankedCommandListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RankedCommandListener.class);
    private final PlayerRepository playerRepository = new PlayerRepository();
    private final MatchRepository matchRepository = new MatchRepository();
    private final SeasonService seasonService = new SeasonService();




    /**
     * Обработчик вызова слэш-команд Discord.
     * Проверяет регистрацию пользователя: если игрок не зарегистрирован — открывает форму ввода ника,
     * иначе выводит стандартное главное меню ладдера.
     *
     * @param event Событие вызова слэш-команды
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("ranked")) {
            return;
        }

        Long discordId = event.getUser().getIdLong();

        // 🔍 ПРОВЕРКА: Авторизован/Зарегистрирован ли игрок в ладдере?
        Optional<PlayerEntity> playerOpt = playerRepository.findById(discordId);
        if (playerOpt.isEmpty()) {
            openRegistrationModal(event);
            return;
        }

        showRankedMenu(event);
    }




    /**
     * Отправляет незарегистрированному пользователю модальное окно для первичного ввода его игрового ника[cite: 61, 307].
     * <p>
     * Используется в качестве точки входа для новых игроков при вызове слэш-команды {@code /ranked}[cite: 55, 301].
     * В актуальной версии JDA текстовые поля ввода оборачиваются через {@link Label#of(String, Component)}[cite: 277, 284].
     * </p>
     *
     * @param event Событие вызова слэш-команды Discord
     */




    private void openRegistrationModal(SlashCommandInteractionEvent event) {
        TextInput nickInput = TextInput.create("input:reg_nickname", TextInputStyle.SHORT)
                .setPlaceholder("Ваш точный игровой ник (см. актуальный рейтинг)")
                .setRequired(true)
                .setRequiredRange(2, 32)
                .build();

        // В актуальной версии JDA 6.x компоненты оборачиваются через Label.of()
        Modal modal = Modal.create("modal:register_user", "📝 Регистрация в MKX Ranked")
                .addComponents(Label.of("Игровой никнейм (Steam)", nickInput))
                .build();

        event.replyModal(modal).queue();
    }

    /**
     * Формирует и отправляет персональное приватное (Ephemeral) Embed-меню {@code /ranked}[cite: 50, 64, 310].
     * <p>
     * Содержит полную статистику игрока (MMR, позицию в топе, сыгранные игры, ранговый дивизион)[cite: 167, 168],
     * а также сводку о текущем активном сезоне[cite: 165, 168].
     * К сообщению прикрепляются кнопки навигации по функционалу ладдера[cite: 170].
     * </p>
     *
     * @param event Событие вызова слэш-команды Discord
     */
    private void showRankedMenu(SlashCommandInteractionEvent event) {
        try {
            Long discordId = event.getUser().getIdLong();

            // 1. Получаем текущий сезон и данные игрока
            SeasonEntity currentSeason = seasonService.getCurrentSeason();
            PlayerEntity player = playerRepository.findById(discordId).orElseThrow();

            int rank = playerRepository.calculateRank(discordId);
            RankTier tier = RankTier.getTierByRank(rank);

            // 2. Собираем персонализированный Embed
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("⚔️ Mortal Kombat X — Ranked Season #" + currentSeason.getSeasonNumber());
            embed.setColor(new Color(175, 0, 0)); // Тёмно-красный цвет MK

            StringBuilder desc = new StringBuilder();
            desc.append("Привет, **").append(player.getDisplayName()).append("**! 👋\n\n")
                    .append("📊 **Твой MMR:** `").append(player.getRating()).append("`\n")
                    .append("🏆 **Место в топе:** ").append(rank > 0 ? "**#" + rank + "**" : "*Не определено*").append("\n")
                    .append("🎮 **Сыграно игр:** ").append(player.getGamesPlayed()).append("\n")
                    .append("🎖️ **Дивизион:** ").append(tier.getEmoji()).append(" ").append(tier.getName()).append("\n\n")
                    .append("───────────────────────────\n")
                    .append("🏆 **Сезон:** ").append(currentSeason.getName()).append("\n")
                    .append("⏳ **Окончание:** ").append(formatDiscordTimestamp(currentSeason.getPlannedEndDate()));

            embed.setDescription(desc.toString());

            // 3. Кнопки
            Button reportBtn = Button.primary("btn:report_match", "📝 Внести результат");
            Button historyBtn = Button.secondary("btn:match_history", "📜 История матчей");
            Button topBtn = Button.secondary("btn:leaderboard", "📊 Топ игроков");
            Button adminRatingBtn = Button.primary("btn:admin_send_rating", "📢 Отправить актуальный рейтинг");


            if(event.getMember() != null &&
            event.getMember().hasPermission(Permission.ADMINISTRATOR)) {

                event.replyEmbeds(embed.build())
                        .setComponents(
                                ActionRow.of(reportBtn, historyBtn, topBtn),
                                ActionRow.of(adminRatingBtn)
                        )
                        .setEphemeral(true)
                        .queue();

                log.info("DISCORD: Персональное меню /ranked отправлено администратору {}", player.getDisplayName());

                return;
            }

            event.replyEmbeds(embed.build())
                    .setComponents(ActionRow.of(reportBtn, historyBtn, topBtn))
                    .setEphemeral(true)
                    .queue();

            log.info("DISCORD: Персональное меню /ranked отправлено игроку {}", player.getDisplayName());
        } catch (IllegalStateException e) {
            event.reply("⚠️ В данный момент нет активного сезона. Обратитесь к администратору.")
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            log.error("DISCORD ERROR: Ошибка при выполнении /ranked", e);
            event.reply("❌ Произошла внутренняя ошибка сервера при загрузке данных меню.")
                    .setEphemeral(true)
                    .queue();
        }
    }




    /**
     * Обработчик отправки модального окна регистрации.
     * Проверяет никнейм в базе данных и отправляет диалог с подтверждением найденного импортированного
     * профиля из Зала Славы либо предложением создать новый профиль.
     *
     * @param event Событие взаимодействия с модальным окном
     */
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals("modal:register_user")) {
            return;
        }

        String inputNickname = event.getValue("input:reg_nickname").getAsString().trim();

        if (playerRepository.isDisplayNameTaken(inputNickname)) {
            event.reply("❌ Никнейм **" + inputNickname + "** уже привязан к другому пользователю Discord!\n" +
                            "Если это ваш ник, обратитесь к администратору ладдера.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Optional<PlayerEntity> unclaimedOpt = playerRepository.findUnclaimedByNickname(inputNickname);

        if (unclaimedOpt.isPresent()) {
            PlayerEntity unclaimed = unclaimedOpt.get();
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔍 Найден импортированный профиль!")
                    .setColor(Color.CYAN)
                    .setDescription("В базе найден профиль из Зала Славы:\n\n" +
                            "🎮 **Игровой ник:** " + unclaimed.getDisplayName() + "\n" +
                            "📊 **Рейтинг:** " + unclaimed.getRating() + " MMR\n" +
                            "⚔️ **Сыграно игр:** " + unclaimed.getGamesPlayed() + "\n\n" +
                            "**Это ваш профиль?** Привяжем этот рейтинг к вашему Discord?");

            Button confirmBtn = Button.success("btn:confirm_claim:" + unclaimed.getDiscordId(), "✅ Да, это мой профиль");
            Button cancelBtn = Button.danger("btn:cancel_reg", "❌ Нет, ввести ник снова");

            event.replyEmbeds(embed.build())
                    .setComponents(ActionRow.of(confirmBtn, cancelBtn))
                    .setEphemeral(true)
                    .queue();
        } else {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🆕 Создание нового профиля")
                    .setColor(Color.GREEN)
                    .setDescription("Профиль с ником **" + inputNickname + "** не найден в Зале Славы.\n\n" +
                            "Вы действительно новичок и хотите создать **НОВЫЙ** профиль?\n" +
                            "*(Стартовый рейтинг: **1000 MMR**)*");

            Button createBtn = Button.success("btn:confirm_new:" + inputNickname, "✅ Да, создать новый");
            Button cancelBtn = Button.danger("btn:cancel_reg", "❌ Отмена / Ввести заново");

            event.replyEmbeds(embed.build())
                    .setComponents(ActionRow.of(createBtn, cancelBtn))
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Обработчик кликов по интерактивным кнопкам.
     * Обрабатывает как кнопки подтверждения/отмены регистрации, так и кнопки главного меню (/ranked).
     *
     * @param event Событие взаимодействия с кнопкой
     */
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("btn:confirm_claim:")) {
            Long negativeId = Long.parseLong(componentId.replace("btn:confirm_claim:", ""));
            Long actualDiscordId = event.getUser().getIdLong();
            String actualUsername = event.getUser().getName();

            boolean success = playerRepository.claimProfileByNegativeId(actualDiscordId, actualUsername, negativeId);
            if (success) {
                event.editMessage("🎉 **Профиль успешно привязан к вашему Discord аккаунту!**\nТеперь напишите `/ranked`, чтобы войти в меню.")
                        .setComponents()
                        .queue();
            } else {
                event.editMessage("❌ Не удалось привязать профиль. Возможно, его уже кто-то забрал.")
                        .setComponents()
                        .queue();
            }
            return;
        }

        if (componentId.startsWith("btn:confirm_new:")) {
            String nickname = componentId.replace("btn:confirm_new:", "");
            Long actualDiscordId = event.getUser().getIdLong();
            String actualUsername = event.getUser().getName();

            playerRepository.createNewPlayer(actualDiscordId, actualUsername, nickname);
            event.editMessage("🎉 **Новый профиль '" + nickname + "' успешно создан!**\nТеперь напишите `/ranked`, чтобы войти в меню.")
                    .setComponents()
                    .queue();
            return;
        }

        if (componentId.equals("btn:cancel_reg")) {
            event.editMessage("❌ Регистрация отменена. Напишите `/ranked` снова, когда будете готовы.")
                    .setComponents()
                    .queue();
            return;
        }

        if (componentId.startsWith("btn:leaderboard_page:")) {

            int page = Integer.parseInt(
                    componentId.substring("btn:leaderboard_page:".length())
            );

            handleLeaderboardPage(event, page, true);

            return;
        }

        if(componentId.equals("btn:admin_send_rating")) {
            handleAdminSendRating(event);
            return;
        }

        // Переключение страниц истории матчей
        if (componentId.startsWith("btn:history_page:")) {
            int targetPage = Integer.parseInt(componentId.replace("btn:history_page:", ""));
            handleMatchHistoryPage(event, targetPage, true); // true = обновление (editMessage)
            return;
        }

        switch (componentId) {
            case "btn:match_history" -> handleMatchHistoryPage(event);
            case "btn:leaderboard" -> handleLeaderboardButton(event);
            case "btn:report_match" -> handleReportMatchButton(event);
        }
    }




    private void handleAdminSendRating(ButtonInteractionEvent event) {

        if (event.getMember() == null ||
                !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {

            event.reply("❌ Кнопка доступна только администраторам.")
                    .setEphemeral(true)
                    .queue();

            return;
        }

        try {
            List<PlayerEntity> players = playerRepository.getAllPlayersSorted();

            StringBuilder message = new StringBuilder();
            message.append("**АКТУАЛЬНЫЙ РЕЙТИНГ ТРЕТЬЕГО СЕЗОНА!**\n\n");

            int place = 1;

            for (PlayerEntity player : players) {

                message.append(String.format(
                        "%d. %s – %d    (%d %s)%n",
                        place++,
                        player.getDisplayName(),
                        player.getRating(),
                        player.getGamesPlayed(),
                        getGamesWord(player.getGamesPlayed())
                ));
            }

            event.deferEdit().queue();

            List<String> chunks = splitMessage(message.toString(), 1900);

            for (String chunk : chunks) {
                event.getChannel()
                        .sendMessage(chunk)
                        .queue();
            }

        } catch (Exception e) {
            log.error("ADMIN: Ошибка при отправке актуального рейтинга", e);

            event.getHook()
                    .sendMessage("❌ Не удалось отправить актуальный рейтинг.")
                    .setEphemeral(true)
                    .queue();
        }
    }


    /***
     * Вспомогательный метод для определения падежа слова "игра" в методе handleAdminSendRating()
     * @param games
     * @return
     */
    private String getGamesWord(int games) {
        int lastTwoDigits = games % 100;

        if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
            return "игр";
        }

        return switch (games % 10) {
            case 1 -> "игра";
            case 2, 3, 4 -> "игры";
            default -> "игр";
        };
    }




    /***
     * Вспомогательный метод разбивки строки для handleAdminSendRating()
     * @param message
     * @param maxLength
     * @return
     */
    private List<String> splitMessage(String message, int maxLength) {

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : message.split("\n")) {

            if (current.length() + line.length() + 1 > maxLength) {
                chunks.add(current.toString());
                current.setLength(0);
            }

            current.append(line).append("\n");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }




    /**
     * Рендерит и отправляет/обновляет интерактивное Embed-сообщение с историей сыгранных матчей[cite: 92, 93, 338, 339].
     * <p>
     * Поддерживает HQL-пагинацию на стороне базы данных[cite: 560]. Генерирует кнопки навигации
     * {@code [◀️ Назад]}, индикатор текущей страницы и {@code [Вперед ▶️]}[cite: 107, 108, 109, 353, 354, 355].
     * В зависимости от флага {@code isUpdate} производит первичную отправку (reply)
     * или редактирование существующего сообщения (editMessage)[cite: 93, 110, 111, 339, 356, 357].
     * </p>
     *
     * @param event Событие взаимодействия с интерактивной кнопкой
     * @param page Номер запрашиваемой страницы (начиная с 0) [cite: 93, 339]
     * @param isUpdate {@code true} — если происходит переключение страницы; {@code false} — если первое открытие [cite: 93, 339]
     */
    private void handleMatchHistoryPage(ButtonInteractionEvent event) {
        handleMatchHistoryPage(event, 0, false);
    }




    /**
     * Рендерит Embed-сообщение со страницей истории матчей и кнопками пагинации.
     *
     * @param event Событие кнопки
     * @param page Номер запрошенной страницы (0-based)
     * @param isUpdate true если это переключение страницы (editMessage),
     *                 false если первое нажатие (reply)
     */
    private void handleMatchHistoryPage(
            ButtonInteractionEvent event,
            int page,
            boolean isUpdate
    ) {
        try {
            Long discordId = event.getUser().getIdLong();
            int pageSize = 5;

            long totalMatches =
                    matchRepository.countTotalMatchesByPlayer(discordId);

            if (totalMatches == 0) {
                if (isUpdate) {
                    event.editMessage("📜 У вас пока нет сыгранных матчей.")
                            .setComponents()
                            .queue();
                } else {
                    event.reply("📜 У вас пока нет сыгранных матчей в текущем сезоне.")
                            .setEphemeral(true)
                            .queue();
                }
                return;
            }

            int totalPages = (int) Math.ceil((double) totalMatches / pageSize);
            int currentPage = Math.max(
                    0,
                    Math.min(page, totalPages - 1)
            );

            List<MatchEntity> matches =
                    matchRepository.getPlayerMatchesPaged(
                            discordId,
                            currentPage,
                            pageSize
                    );

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(
                    "📜 История матчей (Страница "
                            + (currentPage + 1)
                            + " из "
                            + totalPages
                            + ")"
            );
            embed.setColor(Color.CYAN);

            StringBuilder sb = new StringBuilder();

            for (MatchEntity m : matches) {
                boolean isWinner =
                        m.getWinner().getDiscordId().equals(discordId);

                String icon = isWinner ? "✅" : "❌";

                String opponentName = isWinner
                        ? m.getLoser().getDisplayName()
                        : m.getWinner().getDisplayName();

                int scoreWon = isWinner
                        ? m.getWinnerScore()
                        : m.getLoserScore();

                int scoreLost = isWinner
                        ? m.getLoserScore()
                        : m.getWinnerScore();

                int mmrDelta = isWinner
                        ? m.getDeltaWinner()
                        : m.getDeltaLoser();

                String deltaString = mmrDelta > 0
                        ? "+" + mmrDelta
                        : String.valueOf(mmrDelta);

                sb.append(icon)
                        .append(" **VS ")
                        .append(opponentName)
                        .append("** (")
                        .append(scoreWon)
                        .append(":")
                        .append(scoreLost)
                        .append(") | `")
                        .append(deltaString)
                        .append(" MMR` | ")
                        .append(formatDiscordTimestamp(m.getCreatedAt()))
                        .append("\n");
            }

            embed.setDescription(sb.toString());

            Button prevBtn = Button.primary(
                    "btn:history_page:" + (currentPage - 1),
                    "◀️ Назад"
            ).withDisabled(currentPage == 0);

            Button pageIndicator = Button.secondary(
                    "btn:noop",
                    (currentPage + 1) + " / " + totalPages
            ).asDisabled();

            Button nextBtn = Button.primary(
                    "btn:history_page:" + (currentPage + 1),
                    "Вперед ▶️"
            ).withDisabled(currentPage >= totalPages - 1);

            ActionRow actionRow =
                    ActionRow.of(prevBtn, pageIndicator, nextBtn);

            if (isUpdate) {
                event.editMessageEmbeds(embed.build())
                        .setComponents(actionRow)
                        .queue();
            } else {
                event.replyEmbeds(embed.build())
                        .setComponents(actionRow)
                        .setEphemeral(true)
                        .queue();
            }

        } catch (Exception e) {
            log.error(
                    "BUTTON ERROR: Ошибка при генерации истории матчей",
                    e
            );

            if (isUpdate) {
                event.editMessage(
                                "❌ Произошла ошибка при загрузке истории матчей."
                        )
                        .setComponents()
                        .queue(
                                success -> {},
                                error -> log.error(
                                        "Ошибка при отправке сообщения об ошибке",
                                        error
                                )
                        );
            } else {
                event.reply(
                                "❌ Произошла ошибка при загрузке истории матчей."
                        )
                        .setEphemeral(true)
                        .queue(
                                success -> {},
                                error -> log.error(
                                        "Ошибка при reply",
                                        error
                                )
                        );
            }
        }
    }




    private void handleLeaderboardButton(ButtonInteractionEvent event) {
        handleLeaderboardPage(event, 0, false);
    }



    /**
     * Рендерит страницу таблицы лидеров с навигацией по страницам.
     *
     * @param event событие нажатия кнопки
     * @param page номер страницы, начиная с 0
     * @param isUpdate true, если необходимо обновить существующее сообщение;
     *                 false, если необходимо отправить новое сообщение
     */
    private void handleLeaderboardPage(
            ButtonInteractionEvent event,
            int page,
            boolean isUpdate
    ) {
        try {
            int pageSize = 10;

            long totalPlayers = playerRepository.countAllPlayers();

            if (totalPlayers == 0) {
                if (isUpdate) {
                    event.editMessage(
                                    "📊 Таблица лидеров пока пуста. Будьте первыми, кто сыграет матч!"
                            )
                            .setComponents()
                            .queue();
                } else {
                    event.reply(
                                    "📊 Таблица лидеров пока пуста. Будьте первыми, кто сыграет матч!"
                            )
                            .setEphemeral(true)
                            .queue();
                }

                return;
            }

            int totalPages =
                    (int) Math.ceil((double) totalPlayers / pageSize);

            int currentPage =
                    Math.max(0, Math.min(page, totalPages - 1));

            List<PlayerEntity> players =
                    playerRepository.getPlayersPaged(
                            currentPage,
                            pageSize
                    );

            EmbedBuilder embed = new EmbedBuilder();

            embed.setTitle(
                    "📊 Таблица лидеров (Страница "
                            + (currentPage + 1)
                            + " из "
                            + totalPages
                            + ")"
            );

            embed.setColor(Color.YELLOW);

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < players.size(); i++) {
                PlayerEntity player = players.get(i);

                int currentRank = currentPage * pageSize + i + 1;

                RankTier tier =
                        RankTier.getTierByRank(currentRank);

                sb.append(tier.getEmoji())
                        .append(" **#")
                        .append(currentRank)
                        .append("** ")
                        .append(player.getDisplayName())
                        .append(" — `")
                        .append(player.getRating())
                        .append(" MMR` ")
                        .append("(*игры: ")
                        .append(player.getGamesPlayed())
                        .append("*)\n");
            }

            embed.setDescription(sb.toString());

            Button prevBtn = Button.primary(
                    "btn:leaderboard_page:" + (currentPage - 1),
                    "◀️ Назад"
            ).withDisabled(currentPage == 0);

            Button pageIndicator = Button.secondary(
                    "btn:noop",
                    (currentPage + 1) + " / " + totalPages
            ).asDisabled();

            Button nextBtn = Button.primary(
                    "btn:leaderboard_page:" + (currentPage + 1),
                    "Вперёд ▶️"
            ).withDisabled(currentPage >= totalPages - 1);

            ActionRow actionRow =
                    ActionRow.of(
                            prevBtn,
                            pageIndicator,
                            nextBtn
                    );

            if (isUpdate) {
                event.editMessageEmbeds(embed.build())
                        .setComponents(actionRow)
                        .queue();
            } else {
                event.replyEmbeds(embed.build())
                        .setComponents(actionRow)
                        .setEphemeral(true)
                        .queue();
            }

        } catch (Exception e) {
            log.error(
                    "BUTTON ERROR: Ошибка при генерации таблицы лидеров",
                    e
            );

            if (isUpdate) {
                event.editMessage(
                                "❌ Произошла ошибка при загрузке таблицы лидеров."
                        )
                        .setComponents()
                        .queue(
                                success -> {},
                                error -> log.error(
                                        "Ошибка при отправке сообщения об ошибке",
                                        error
                                )
                        );
            } else {
                event.reply(
                                "❌ Произошла ошибка при загрузке таблицы лидеров."
                        )
                        .setEphemeral(true)
                        .queue(
                                success -> {},
                                error -> log.error(
                                        "Ошибка при reply",
                                        error
                                )
                        );
            }
        }
    }




    /**
     * Обработчик нажатия кнопки «📝 Внести результат» в меню ладдера[cite: 1186].
     * <p>
     * Инициирует первый шаг двухшагового процесса фиксации результатов FT5 сета[cite: 1187].
     * Вместо прямого вызова модального окна отправляет пользователю нативное выпадающее
     * меню выбора участников ({@link net.dv8tion.jda.api.components.selections.EntitySelectMenu})[cite: 1188].
     * Это гарантирует получение точного Discord ID оппонента и исключает опечатки[cite: 1189].
     * </p>
     *
     * @param event Событие взаимодействия с кнопкой в главном меню
     */
    private void handleReportMatchButton(ButtonInteractionEvent event) {
        // Создаем нативное меню Discord для выбора ТОЛЬКО пользователей
        EntitySelectMenu opponentSelect = EntitySelectMenu.create("select:opponent_report", EntitySelectMenu.SelectTarget.USER)
                .setPlaceholder("Кликни, чтобы выбрать соперника...")
                .build();

        event.reply("👤 С кем был сыгран матч?")
                .setComponents(ActionRow.of(opponentSelect))
                .setEphemeral(true)
                .queue();
    }




    /**
     * Обработчик взаимодействия с выпадающим меню выбора пользователя[cite: 1191].
     * <p>
     * Выполняет второй шаг процесса записи результатов[cite: 1192]. Извлекает точный Discord Snowflake ID
     * выбранного оппонента и инкапсулирует его напрямую в идентификатор генерируемого
     * модального окна (формата {@code "modal:report_match:<opponentDiscordId>"})[cite: 1193, 1194].
     * Затем вызывает модальную форму, содержащую исключительно поля для ввода счета[cite: 1195].
     * </p>
     *
     * @param event Событие выбора оппонента в интерактивном EntitySelectMenu
     */
    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (event.getComponentId().equals("select:opponent_report")) {
            // Получаем 100% точный ID выбранного пользователя
            User opponent = event.getMentions().getUsers().get(0);

            // Поля только для счета (СТРОГО 2 аргумента: ID и стиль)
            TextInput myScoreInput = TextInput.create("my_score_input", TextInputStyle.SHORT)
                    .setPlaceholder("Например: 5")
                    .setRequired(true)
                    .setRequiredRange(1, 1)
                    .build();

            TextInput oppScoreInput = TextInput.create("opponent_score_input", TextInputStyle.SHORT)
                    .setPlaceholder("Например: 2")
                    .setRequired(true)
                    .setRequiredRange(1, 1)
                    .build();

            // Вшиваем ID соперника прямо в ID модалки
            String modalId = "modal:report_match:" + opponent.getId();

            // Собираем модалку, оборачивая TextInput в Label.of() для текста над полем
            Modal modal = Modal.create(modalId, "Счет против " + opponent.getName())
                    .addComponents(
                            Label.of("Ваши победы (0 - 5)", myScoreInput),
                            Label.of("Победы соперника (0 - 5)", oppScoreInput)
                    )
                    .build();

            event.replyModal(modal).queue(); // Открываем форму
        }
    }




    /**
     * Вспомогательный метод для форматирования даты и времени в стандарт динамических таймстемпов Discord.
     *
     * @param dateTime Дата и время {@link java.time.LocalDateTime}
     * @return Строка формата {@code <t:epoch:F> (<t:epoch:R>)}
     */
    private String formatDiscordTimestamp(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return "Не указана";
        long epochSecond = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return "<t:" + epochSecond + ":F> (<t:" + epochSecond + ":R>)";
    }
}