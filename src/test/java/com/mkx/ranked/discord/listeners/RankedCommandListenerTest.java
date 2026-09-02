package com.mkx.ranked.discord.listeners;

import com.mkx.ranked.discord.DiscordErrorMessageMapper;
import com.mkx.ranked.discord.formatter.RankedMessageFormatter;
import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.model.dto.MatchHistoryEntryDto;
import com.mkx.ranked.service.LeaderboardService;
import com.mkx.ranked.service.MatchService;
import com.mkx.ranked.service.PlayerService;
import com.mkx.ranked.service.RegistrationService;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankedCommandListenerTest {

    private PlayerService playerService;
    private RegistrationService registrationService;
    private LeaderboardService leaderboardService;
    private MatchService matchService;
    private RankedMessageFormatter formatter;
    private DiscordErrorMessageMapper errorMessageMapper;
    private RankedCommandListener listener;

    @BeforeEach
    void setUp() {
        playerService = mock(PlayerService.class);
        registrationService = mock(RegistrationService.class);
        leaderboardService = mock(LeaderboardService.class);
        matchService = mock(MatchService.class);
        formatter = mock(RankedMessageFormatter.class);
        errorMessageMapper = mock(DiscordErrorMessageMapper.class);
        listener = new RankedCommandListener(
                playerService,
                registrationService,
                leaderboardService,
                matchService,
                formatter,
                errorMessageMapper
        );
    }

    @Test
    void historyButtonOnlyOpensHistoryChoiceMenu() {
        ButtonInteractionEvent event = buttonEvent("btn:match_history");

        listener.onButtonInteraction(event);

        verify(event).reply("Какую историю матчей показать?");
        verify(matchService, never()).getFullMatchHistory(anyLong());
    }

    @Test
    void selfHistoryUsesClickingUsersDiscordIdAndExistingEmptyMessage() {
        ButtonInteractionEvent event = buttonEvent("btn:history:self");
        when(event.getUser().getIdLong()).thenReturn(11L);
        when(matchService.getFullMatchHistory(11L)).thenReturn(List.of());

        listener.onButtonInteraction(event);

        verify(matchService).getFullMatchHistory(11L);
        verify(event).reply("У вас пока нет сыгранных матчей в текущем сезоне.");
    }

    @Test
    void selectedPlayerHistoryUsesDiscordUserSelectValue() {
        EntitySelectInteractionEvent event = entitySelectEvent("select:history_player");
        User selectedPlayer = mock(User.class);
        when(selectedPlayer.getIdLong()).thenReturn(22L);
        when(event.getMentions().getUsers()).thenReturn(List.of(selectedPlayer));
        when(matchService.getFullMatchHistory(22L)).thenReturn(List.of());

        listener.onEntitySelectInteraction(event);

        verify(matchService).getFullMatchHistory(22L);
        verify(event).reply("У выбранного игрока пока нет сыгранных матчей в текущем сезоне.");
    }

    @Test
    void unregisteredSelectedPlayerIsMappedThroughDiscordErrorMapper() {
        EntitySelectInteractionEvent event = entitySelectEvent("select:history_player");
        User selectedPlayer = mock(User.class);
        BusinessException exception = new PlayerNotRegisteredException(22L);
        when(selectedPlayer.getIdLong()).thenReturn(22L);
        when(event.getMentions().getUsers()).thenReturn(List.of(selectedPlayer));
        when(matchService.getFullMatchHistory(22L)).thenThrow(exception);
        when(errorMessageMapper.toUserMessage(exception)).thenReturn("Игрок не зарегистрирован.");

        listener.onEntitySelectInteraction(event);

        verify(errorMessageMapper).toUserMessage(exception);
        verify(event).reply("Игрок не зарегистрирован.");
    }

    @Test
    void opponentReportSelectKeepsItsExistingFlow() {
        EntitySelectInteractionEvent event = entitySelectEvent("select:opponent_report");
        User opponent = mock(User.class);
        when(opponent.getId()).thenReturn("22");
        when(opponent.getName()).thenReturn("opponent");
        when(event.getMentions().getUsers()).thenReturn(List.of(opponent));

        listener.onEntitySelectInteraction(event);

        verify(event).replyModal(any(Modal.class));
        verify(matchService, never()).getFullMatchHistory(anyLong());
    }

    private ButtonInteractionEvent buttonEvent(String componentId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn(componentId);
        return event;
    }

    private EntitySelectInteractionEvent entitySelectEvent(String componentId) {
        EntitySelectInteractionEvent event = mock(EntitySelectInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(event.getComponentId()).thenReturn(componentId);
        return event;
    }
}
