package com.mkx.ranked.config;

import com.mkx.ranked.discord.listeners.MatchConfirmationListener;
import com.mkx.ranked.discord.listeners.ModalInteractionListener;
import com.mkx.ranked.discord.listeners.RankedCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
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
                        )
                )
                .queue();

        return jda;
    }
}
