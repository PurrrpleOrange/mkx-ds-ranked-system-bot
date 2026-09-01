package com.mkx.ranked.config;

import com.mkx.ranked.discord.listeners.AdminCommandListener;
import com.mkx.ranked.discord.listeners.MatchConfirmationListener;
import com.mkx.ranked.discord.listeners.ModalInteractionListener;
import com.mkx.ranked.discord.listeners.RankedCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfig {

    @Bean(destroyMethod = "shutdown")
    public JDA jda(
            @Value("${discord.token}") String token,
            RankedCommandListener rankedCommandListener,
            AdminCommandListener adminCommandListener,
            ModalInteractionListener modalInteractionListener,
            MatchConfirmationListener matchConfirmationListener
    ) throws InterruptedException {

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .addEventListeners(
                        rankedCommandListener,
                        adminCommandListener,
                        modalInteractionListener,
                        matchConfirmationListener
                )
                .build()
                .awaitReady();

        jda.updateCommands()
                .addCommands(
                        Commands.slash(
                                "ranked",
                                "Открыть главное рейтинговое меню MKX Season"
                        ),
                        Commands.slash("admin", "Административное управление MKX Ranked")
                                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                                .addSubcommandGroups(
                                        new SubcommandGroupData("season", "Управление сезонами")
                                                .addSubcommands(
                                                        new SubcommandData("create", "Создать сезон в статусе CREATED")
                                                                .addOption(OptionType.STRING, "name", "Название сезона", true)
                                                                .addOption(
                                                                        OptionType.STRING,
                                                                        "planned_end",
                                                                        "Плановое окончание в ISO-формате",
                                                                        false
                                                                ),
                                                        new SubcommandData("activate", "Активировать CREATED сезон")
                                                                .addOption(
                                                                        OptionType.INTEGER,
                                                                        "number",
                                                                        "Номер сезона",
                                                                        true
                                                                ),
                                                        new SubcommandData("finish", "Завершить текущий ACTIVE сезон"),
                                                        new SubcommandData("info", "Показать информацию о сезоне")
                                                                .addOption(
                                                                        OptionType.INTEGER,
                                                                        "number",
                                                                        "Номер сезона; без него — ACTIVE",
                                                                        false
                                                                )
                                                ),
                                        new SubcommandGroupData("match", "Управление матчами")
                                                .addSubcommands(
                                                        new SubcommandData("info", "Показать информацию о матче")
                                                                .addOption(OptionType.STRING, "id", "ID матча", true),
                                                        new SubcommandData("delete", "Откатить и удалить матч")
                                                                .addOption(OptionType.STRING, "id", "ID матча", true)
                                                ),
                                        new SubcommandGroupData("player", "Управление игроками")
                                                .addSubcommands(
                                                        new SubcommandData("info", "Показать профиль игрока ACTIVE сезона")
                                                                .addOption(OptionType.USER, "user", "Discord-пользователь", true)
                                                ),
                                        new SubcommandGroupData("leaderboard", "Публикация рейтинга")
                                                .addSubcommands(
                                                        new SubcommandData(
                                                                "publish",
                                                                "Опубликовать полный рейтинг в текущем канале"
                                                        )
                                                )
                                )
                )
                .queue();

        return jda;
    }
}
