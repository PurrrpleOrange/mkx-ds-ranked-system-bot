# Внутренняя карта Discord component ID

Этот документ предназначен для разработчиков. Пользовательские сценарии и права описаны в [commands.md](commands.md).

## Пользовательские компоненты

| Component ID | Тип | Обработчик |
| --- | --- | --- |
| `modal:register_user` | modal | регистрация в активном сезоне; `RankedCommandListener` |
| `btn:report_match` | button | открывает выбор соперника |
| `select:opponent_report` | user select | открывает modal счёта |
| `modal:report_match:{opponentId}` | modal | валидирует preview и публикует подтверждение; `ModalInteractionListener` |
| `confirm_match:{reporterId}:{opponentId}:{reporterScore}:{opponentScore}` | button | подтверждение только соперником; `MatchConfirmationListener` |
| `reject_match:{reporterId}:{opponentId}` | button | отклонение любым участником; `MatchConfirmationListener` |
| `btn:match_history` | button | выбор вида истории |
| `btn:history:self` | button | собственная полная история |
| `btn:history:player` | button | открывает выбор пользователя |
| `select:history_player` | user select | полная история выбранного игрока |
| `btn:leaderboard` | button | полный leaderboard активного сезона |
| `btn:edit_profile` | button | modal сезонного display name |
| `modal:edit_profile` | modal | сохраняет сезонный display name |

`RankedCommandListener` обрабатывает все перечисленные компоненты, кроме match report modal и финальных confirm/reject buttons.

## Актуальная административная панель

| Component ID | Действие |
| --- | --- |
| `admin:button:leaderboard_publish` | публичный leaderboard |
| `admin:button:manage_seasons` | меню сезонов |
| `admin:button:manage_matches` | меню матчей |
| `admin:button:manage_players` | меню игроков |
| `admin:button:season_list` | список сезонов |
| `admin:button:season_create` / `admin:modal:season_create` | создание сезона |
| `admin:button:season_activate` / `admin:modal:season_activate` | активация сезона |
| `admin:button:season_finish` | завершение активного сезона |
| `admin:button:season_statistics` / `admin:modal:season_statistics` | статистика завершённого сезона |
| `admin:button:season_update` / `admin:modal:season_update` | изменение активного сезона |
| `admin:button:match_delete` / `admin:modal:match_delete` | rollback и удаление матча |
| `admin:button:player_list` | все участия активного сезона |
| `admin:button:player_info` / `admin:select:player_info` | профиль выбранного игрока |

Все `admin:*` routes проходят runtime-проверку `ADMINISTRATOR`.

## Compatibility routes без кнопок в актуальном меню

Listener распознаёт следующие IDs, но `rootAdminMenuRows()` и дочерние menu rows их не создают:

- `admin:button:season_info` / `admin:modal:season_info`;
- `admin:button:season_planned_end` / `admin:modal:season_planned_end`;
- `admin:button:match_info` / `admin:modal:match_info`.

Они могут обработать interaction из ранее опубликованного сообщения, пока Discord его сохраняет, но не являются частью текущего навигационного UI.
