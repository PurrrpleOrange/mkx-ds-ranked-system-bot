package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.model.dto.MatchResult;
import com.mkx.ranked.service.MatchService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * Слушатель публичных кнопок подтверждения и отклонения результатов матчей FT5.
 */
public class MatchConfirmationListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(MatchConfirmationListener.class);
    private final MatchService matchService;

    public MatchConfirmationListener(MatchService matchService) {
        this.matchService = matchService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("confirm_match:")) {
            handleConfirm(event, componentId);
        } else if (componentId.startsWith("reject_match:")) {
            handleReject(event, componentId);
        }
    }

    /**
     * Обработка нажатия кнопки «✅ Подтвердить».
     */
    private void handleConfirm(ButtonInteractionEvent event, String componentId) {
        // Формат ID: confirm_match:{reporterDiscordId}:{opponentDiscordId}:{myScore}:{opponentScore}
        String[] parts = componentId.split(":");
        if (parts.length < 5) {
            log.error("MATCH CONFIRM ERROR: Неверный формат confirmCustomId: {}", componentId);
            event.reply("❌ Внутренняя ошибка обработки кнопки.").setEphemeral(true).queue();
            return;
        }

        long reporterDiscordId = Long.parseLong(parts[1]);
        long opponentDiscordId = Long.parseLong(parts[2]);
        int myScore = Integer.parseInt(parts[3]);
        int opponentScore = Integer.parseInt(parts[4]);

        long clickerDiscordId = event.getUser().getIdLong();

        // 1. ЗАЩИТА: Нажать подтверждение может СТРОГО заявленный оппонент
        if (clickerDiscordId != opponentDiscordId) {
            log.warn("MATCH CONFIRM WARN: Игрок {} попытался подтвердить чужой матч против {}",
                    clickerDiscordId, reporterDiscordId);
            event.reply("❌ **Отказано.** Подтвердить этот результат может только <@" + opponentDiscordId + ">!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        log.info("MATCH CONFIRM: Соперник {} подтверждает матч против {}. Заявленный счет: {}-{}",
                opponentDiscordId, reporterDiscordId, myScore, opponentScore);

        // 2. Определяем кто победитель, а кто проигравший
        long winnerId;
        String winnerUsername;
        long loserId;
        String loserUsername;
        int winnerScore;
        int loserScore;

        if (myScore == 5) {
            winnerId = reporterDiscordId;
            winnerUsername = "Reporter"; // Имя подтянется из базы данных в сервисе
            loserId = opponentDiscordId;
            loserUsername = event.getUser().getName();
            winnerScore = myScore;
            loserScore = opponentScore;
        } else {
            winnerId = opponentDiscordId;
            winnerUsername = event.getUser().getName();
            loserId = reporterDiscordId;
            loserUsername = "Reporter";
            winnerScore = opponentScore;
            loserScore = myScore;
        }

        try {
            // 3. Вызываем сервис для проведения матча и перерасчета MMR
            MatchResult result = matchService.processMatchResult(
                    winnerId, winnerUsername,
                    loserId, loserUsername,
                    winnerScore, loserScore
            );

            // 4. Формируем финальный зеленый Embed без кнопок
            EmbedBuilder successEmbed = new EmbedBuilder()
                    .setTitle("🏆 Матч официально зарегистрирован!")
                    .setColor(Color.GREEN)
                    .setDescription(String.format(
                            "**Результат подтверждён <@%d>!**\n\n" +
                                    "📊 **Итоговый счет:** FT5 (%d : %d)\n\n" +
                                    "🥇 **Победитель:** <@%d> (`+%d MMR` 📈 Новый рейтинг: **%d**)\n" +
                                    "🥈 **Проигравший:** <@%d> (`%d MMR` 📉 Новый рейтинг: **%d**)",
                            opponentDiscordId,
                            winnerScore, loserScore,
                            result.getWinnerDiscordId(), result.getDeltaWinner(), result.getNewWinnerRating(),
                            result.getLoserDiscordId(), result.getDeltaLoser(), result.getNewLoserRating()
                    ))
                    .setFooter("Матч успешно занесен в историю ладдера.");

            // 5. Редактируем исходное сообщение: убираем кнопки и меняем Embed
            event.editMessageEmbeds(successEmbed.build())
                    .setComponents() // Стирает кнопки
                    .queue(
                            success -> log.info("MATCH SUCCESS: Результат матча #{} записан в базу.", result.getMatchId()),
                            error -> log.error("MATCH ERROR: Ошибка обновления Embed-сообщения.", error)
                    );

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("MATCH WARN: Ошибка при обработке бизнес-логики матча: {}", e.getMessage());
            event.reply("❌ Ошибка записи матча: " + e.getMessage()).setEphemeral(true).queue();
        } catch (Exception e) {
            log.error("MATCH ERROR: Критический сбой при сохранении матча", e);
            event.reply("❌ Произошла внутренняя ошибка сервера при сохранении результата.")
                    .setEphemeral(true)
                    .queue();
        }
    }

    /**
     * Обработка нажатия кнопки «❌ Отклонить».
     */
    private void handleReject(ButtonInteractionEvent event, String componentId) {
        // Формат ID: reject_match:{reporterDiscordId}:{opponentDiscordId}
        String[] parts = componentId.split(":");
        if (parts.length < 3) {
            event.reply("❌ Ошибка обработки формы.").setEphemeral(true).queue();
            return;
        }

        long reporterDiscordId = Long.parseLong(parts[1]);
        long opponentDiscordId = Long.parseLong(parts[2]);
        long clickerDiscordId = event.getUser().getIdLong();

        // Отклонить может либо сам соперник, либо подавший заявку репортер (если спутал счет)
        if (clickerDiscordId != opponentDiscordId && clickerDiscordId != reporterDiscordId) {
            event.reply("❌ **Отказано.** Вы не являетесь участником этого сета!")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        log.info("MATCH REJECTED: Игрок {} отклонил заявку на матч (Репортер: {}, Соперник: {})",
                clickerDiscordId, reporterDiscordId, opponentDiscordId);

        EmbedBuilder rejectEmbed = new EmbedBuilder()
                .setTitle("❌ Запись матча отменена")
                .setColor(Color.RED)
                .setDescription(String.format(
                        "Заявленный результат матча между <@%d> и <@%d> был **отклонен** (<@%d>).\n\n" +
                                "*Результат не учтен в статистике и рейтинг игроков не изменился.*",
                        reporterDiscordId, opponentDiscordId, clickerDiscordId
                ));

        // Редактируем сообщение: меняем цвет на красный и убираем кнопки
        event.editMessageEmbeds(rejectEmbed.build())
                .setComponents() // Стирает кнопки
                .queue();
    }
}