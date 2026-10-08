package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.AdminMessageFormatter;
import com.mkx.ranked.service.AdminService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.Permission;
import com.mkx.ranked.model.dto.PlayerProfileDto;
import com.mkx.ranked.model.dto.SeasonDto;
import com.mkx.ranked.model.enums.SeasonStatus;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminCommandListenerTest {

    @Test
    void forgedRemovalButtonCannotBeUsedByNonAdministrator() {
        AdminService service = mock(AdminService.class);
        var listener = new AdminCommandListener(service, new AdminMessageFormatter(), new DiscordErrorMessageMapper());
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn("admin:button:player_remove_confirm:1:11");
        when(event.getMember().hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

        listener.onButtonInteraction(event);

        verify(event).reply("Команда доступна только администраторам сервера.");
        verifyNoInteractions(service);
    }

    @Test
    void confirmedRemovalUsesBoundSeasonAndClickingAdministrator() {
        AdminService service = mock(AdminService.class);
        var listener = new AdminCommandListener(service, new AdminMessageFormatter(), new DiscordErrorMessageMapper());
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn("admin:button:player_remove_confirm:10:11");
        when(event.getMember().hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(event.getUser().getIdLong()).thenReturn(999L);

        listener.onButtonInteraction(event);

        verify(service).removePlayerFromSeason(10L, 11L, 999L);
        verify(event.editMessage("Игрок <@11> удалён из сезона. Матчи и рейтинг соперников сохранены.")
                .setEmbeds(List.of())).setComponents(List.of());
    }

    @Test
    void selectingPlayerOnlyPreviewsRemovalWithSeasonBoundConfirmation() {
        AdminService service = mock(AdminService.class);
        var formatter = new AdminMessageFormatter();
        var listener = new AdminCommandListener(service, formatter, new DiscordErrorMessageMapper());
        var event = mock(EntitySelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn("admin:select:player_remove:10");
        when(event.getMember().hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        var user = mock(User.class);
        when(user.getIdLong()).thenReturn(11L);
        when(event.getMentions().getUsers()).thenReturn(List.of(user));
        var season = new SeasonDto(10L, 3, "Season", SeasonStatus.ACTIVE, null, null, null);
        var player = new PlayerProfileDto(1L, 11L, "Scorpion", 1000, 0, null, "Без ранга", "⚪", season, null, null);
        when(service.getPlayerRemovalPreview(11L)).thenReturn(player);

        listener.onEntitySelectInteraction(event);

        ArgumentCaptor<ActionRow> row = ArgumentCaptor.forClass(ActionRow.class);
        verify(event.replyEmbeds(formatter.playerRemovalConfirmation(player))).setComponents(row.capture());
        assertEquals(List.of("admin:button:player_remove_confirm:10:11", "admin:button:player_remove_cancel"),
                row.getValue().getComponents().stream()
                        .map(component -> ((Button) component).getCustomId()).toList());
        verify(service, never()).removePlayerFromSeason(anyLong(), anyLong(), anyLong());
    }

    @Test
    void cancellingRemovalDoesNotCallService() {
        AdminService service = mock(AdminService.class);
        var listener = new AdminCommandListener(service, new AdminMessageFormatter(), new DiscordErrorMessageMapper());
        var event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn("admin:button:player_remove_cancel");
        when(event.getMember().hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);

        listener.onButtonInteraction(event);

        verify(event.editMessage("Удаление игрока отменено.").setEmbeds(List.of())).setComponents(List.of());
        verifyNoInteractions(service);
    }

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
