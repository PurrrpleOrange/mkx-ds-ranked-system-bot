package com.mkx.ranked.discord;

import com.mkx.ranked.exception.BusinessException;
import com.mkx.ranked.exception.InvalidMatchException;
import com.mkx.ranked.exception.MatchNotFoundException;
import com.mkx.ranked.exception.PlayerNotFoundException;
import com.mkx.ranked.exception.PlayerNotRegisteredException;
import com.mkx.ranked.exception.SeasonNotActiveException;
import com.mkx.ranked.exception.SeasonNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class DiscordErrorMessageMapper {

    public String toUserMessage(BusinessException exception) {
        if (exception instanceof PlayerNotFoundException) {
            return "Ваш Discord-аккаунт не привязан к игровому профилю.";
        }
        if (exception instanceof PlayerNotRegisteredException) {
            return "Вы не зарегистрированы в текущем рейтинговом сезоне.";
        }
        if (exception instanceof SeasonNotActiveException) {
            return "Сейчас нет активного рейтингового сезона.";
        }
        if (exception instanceof SeasonNotFoundException) {
            return "Рейтинговый сезон не найден.";
        }
        if (exception instanceof MatchNotFoundException) {
            return "Матч не найден или уже был обработан.";
        }
        if (exception instanceof InvalidMatchException) {
            return translateMatchMessage(exception.getMessage());
        }

        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Операцию не удалось выполнить. Проверьте данные и попробуйте снова."
                : message;
    }

    public String internalError() {
        return "Произошла внутренняя ошибка сервера. Попробуйте ещё раз позже.";
    }

    private String translateMatchMessage(String message) {
        if (message == null) {
            return "Некорректные данные матча.";
        }
        if (message.contains("against yourself")) {
            return "Нельзя указать самого себя в качестве соперника.";
        }
        if (message.contains("FT5 score")) {
            return "Некорректный счёт FT5: один игрок должен иметь 5 побед, второй — от 0 до 4.";
        }
        return message;
    }
}
