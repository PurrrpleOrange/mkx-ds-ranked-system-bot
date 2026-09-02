package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.AdminMessageFormatter;
import com.mkx.ranked.service.AdminService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCommandListenerTest {

    @Test
    void nonAdministratorCannotReachAnyAdminUseCase() {
        AdminService adminService = mock(AdminService.class);
        AdminCommandListener listener = new AdminCommandListener(
                adminService,
                mock(AdminMessageFormatter.class),
                mock(DiscordErrorMessageMapper.class)
        );
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getName()).thenReturn("admin");
        when(event.getGuild()).thenReturn(null);

        listener.onSlashCommandInteraction(event);

        verify(event).reply("Команда доступна только администраторам сервера.");
        verifyNoInteractions(adminService);
    }

    @Test
    void listenerDependsOnServiceLayerAndHasNoRepositoryDependency() {
        Field[] fields = AdminCommandListener.class.getDeclaredFields();

        assertTrue(Arrays.stream(fields).noneMatch(field ->
                field.getType().getSimpleName().endsWith("Repository")
        ));
        assertTrue(Arrays.stream(AdminCommandListener.class.getConstructors()[0].getParameterTypes())
                .anyMatch(AdminService.class::equals));
    }
}
