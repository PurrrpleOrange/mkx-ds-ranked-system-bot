package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.repository.PlayerRepository;
import com.mkx.ranked.model.PlayerEntity;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Optional;

/**
 * Слушатель модальных окон для обработки внесения результатов матчей FT5.
 */
@Component
public class ModalInteractionListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ModalInteractionListener.class);
    private final PlayerRepository playerRepository;

    public ModalInteractionListener(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    /**
     * Обработчик отправки модальной формы с результатами FT5 сета.
     * <p>
     * Извлекает Discord ID оппонента напрямую из динамического ID модального окна,
     * производит парсинг и валидацию счёта на соответствие регламенту First to 5,
     * а также выполняет защитные проверки (проверка регистрации в ладдере, защита от игры с самим собой).
     * </p>
     * <p>
     * В случае успешной проверки формирует публичное Embed-сообщение с кнопками
     * {@code [✅ Подтвердить]} и {@code [❌ Отклонить]} и отправляет его в канал
     * для ожидания подтверждения со стороны соперника.
     * </p>
     *
     * @param event Событие отправки модального окна с результатами сета
     */
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        // 1. Проверяем, что это нужная нам модалка (ID теперь динамический)
        if (!modalId.startsWith("modal:report_match:")) {
            return;
        }

        long reporterDiscordId = event.getUser().getIdLong();
        log.info("MATCH REPORT: Игрок {} начал процесс внесения результатов матча.", reporterDiscordId);

        // 2. Извлекаем ID соперника прямо из ID модалки (формат "modal:report_match:123456789")
        String[] parts = modalId.split(":");
        if (parts.length < 3) {
            log.error("MATCH ERROR: Неверный формат ID модального окна: {}", modalId);
            event.reply("❌ Ошибка системы: Неверный формат формы.").setEphemeral(true).queue();
            return;
        }

        long opponentDiscordId;
        try {
            opponentDiscordId = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            log.error("MATCH ERROR: Не удалось прочитать ID соперника из модалки: {}", modalId, e);
            event.reply("❌ Ошибка системы: Не удалось прочитать ID соперника.").setEphemeral(true).queue();
            return;
        }

        // 3. Быстрая защита от игры с самим собой (до запросов к БД)
        if (reporterDiscordId == opponentDiscordId) {
            log.warn("MATCH WARN: Игрок {} попытался записать матч против самого себя.", reporterDiscordId);
            event.reply("❌ Вы не можете записать результат матча против самого себя!").setEphemeral(true).queue();
            return;
        }

        // 4. Извлечение данных из формы (осталось только 2 поля со счетом)
        ModalMapping myScoreMapping = event.getValue("my_score_input");
        ModalMapping opponentScoreMapping = event.getValue("opponent_score_input");

        if (myScoreMapping == null || opponentScoreMapping == null) {
            log.warn("MATCH WARN: Игрок {} отправил неполную форму.", reporterDiscordId);
            event.reply("❌ Ошибка: Не все поля формы были заполнены.").setEphemeral(true).queue();
            return;
        }

        // 5. Парсинг и валидация счета (Формат FT5)
        int myScore;
        int opponentScore;
        try {
            myScore = Integer.parseInt(myScoreMapping.getAsString().trim());
            opponentScore = Integer.parseInt(opponentScoreMapping.getAsString().trim());
        } catch (NumberFormatException e) {
            log.warn("MATCH WARN: Игрок {} ввел нечисловое значение в счет.", reporterDiscordId, e);
            event.reply("❌ Ошибка: Счет должен быть представлен целыми числами!").setEphemeral(true).queue();
            return;
        }

        if (!isValidFT5Score(myScore, opponentScore)) {
            log.warn("MATCH WARN: Игрок {} указал невалидный FT5 счет: {}-{}.", reporterDiscordId, myScore, opponentScore);
            event.reply("❌ **Некорректный счет FT5!**\n" +
                            "Игра идет до 5 побед (First to 5).\n" +
                            "Победитель должен иметь **ровно 5** побед, а проигравший — **от 0 до 4**.")
                    .setEphemeral(true).queue();
            return;
        }

        // 6. Проверка наличия игроков в базе данных
        Optional<PlayerEntity> reporterOpt = playerRepository.findById(reporterDiscordId);
        Optional<PlayerEntity> opponentOpt = playerRepository.findById(opponentDiscordId);

        if (reporterOpt.isEmpty()) {
            log.warn("MATCH WARN: Незарегистрированный игрок {} попытался внести результат.", reporterDiscordId);
            event.reply("❌ Ваш профиль не найден. Пройдите регистрацию через `/ranked`.").setEphemeral(true).queue();
            return;
        }

        if (opponentOpt.isEmpty()) {
            log.warn("MATCH WARN: Игрок {} попытался внести результат с незарегистрированным соперником {}.", reporterDiscordId, opponentDiscordId);
            event.reply("❌ Выбранный соперник не зарегистрирован в рейтинговой системе MKX.")
                    .setEphemeral(true).queue();
            return;
        }

        PlayerEntity reporter = reporterOpt.get();
        PlayerEntity opponent = opponentOpt.get();

        // 7. Формирование публичного сообщения подтверждения
        String confirmCustomId = String.format("confirm_match:%d:%d:%d:%d",
                reporterDiscordId, opponentDiscordId, myScore, opponentScore);
        String rejectCustomId = String.format("reject_match:%d:%d",
                reporterDiscordId, opponentDiscordId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚔️ Подтверждение результатов матча FT5")
                .setColor(Color.ORANGE)
                .setDescription(String.format(
                        "**%s** заявляет о завершении сет-матча!\n\n" +
                                "📊 **Заявленный счет:**\n" +
                                "• **%s**: %d\n" +
                                "• **%s**: %d\n\n" +
                                "⚠️ <@%d>, пожалуйста, подтвердите или отклоните этот результат.",
                        reporter.getDisplayName(),
                        reporter.getDisplayName(), myScore,
                        opponent.getDisplayName(), opponentScore,
                        opponentDiscordId
                ))
                .setFooter("Матч будет учтен в ладдере только после подтверждения соперником.");

        // 8. Отправка
        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(
                        Button.success(confirmCustomId, "✅ Подтвердить"),
                        Button.danger(rejectCustomId, "❌ Отклонить")
                ))
                .queue(
                        success -> log.info("MATCH SUCCESS: Запрос на подтверждение матча {}-{} отправлен ({} vs {}).",
                                myScore, opponentScore, reporter.getDisplayName(), opponent.getDisplayName()),
                        error -> log.error("MATCH ERROR: Сбой при отправке Embed с подтверждением матча.", error)
                );
    }

    /**
     * Валидация счета под регламент First to 5 (FT5).
     */
    private boolean isValidFT5Score(int score1, int score2) {
        if (score1 < 0 || score2 < 0) return false;
        if (score1 == 5 && score2 >= 0 && score2 < 5) return true;
        return score2 == 5 && score1 >= 0 && score1 < 5;
    }
}