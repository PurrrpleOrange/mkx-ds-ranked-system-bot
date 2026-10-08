package com.mkx.ranked.exception;

public class PlayerRemovedFromSeasonException extends BusinessException {

    public PlayerRemovedFromSeasonException(long discordId, long removedBy) {
        super("Игрок <@%d> удалён из текущего сезона пользователем <@%d>. "
                .formatted(discordId, removedBy)
                + "Участие и повторная регистрация в этом сезоне недоступны. Просмотр статистики доступен.");
    }
}
