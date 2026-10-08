# Локальная разработка

## Требования

- JDK 17 или новее;
- Maven 3.9 или совместимая версия;
- Docker Engine с Docker Compose — для PostgreSQL и полного набора тестов;
- Discord bot token — только для запуска приложения, не для unit/integration tests.

В `pom.xml` установлен `java.version=17`. Dockerfile использует Maven с Eclipse Temurin 21 на build stage и Temurin 21 JRE в runtime stage. Это допустимое текущее состояние: артефакт компилируется для Java 17, контейнер собирает и запускает его на Java 21.

## Конфигурация

`application.yml` требует четыре environment variables:

| Переменная | Пример для локального запуска |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/mkx_ranked` |
| `DB_USERNAME` | `mkx` |
| `DB_PASSWORD` | локальный пароль из `.env` |
| `DISCORD_TOKEN` | token тестового Discord-бота |

## Запуск PostgreSQL

Создайте `.env` в корне проекта. Для локального запуска Java-процесса удобно сохранить полный набор Compose-переменных:

```dotenv
DB_URL=jdbc:postgresql://postgres:5432/mkx_ranked
DB_PASSWORD=local-development-password
DISCORD_TOKEN=local-development-token
```

Запустите только базу:

```bash
docker compose up -d postgres
docker compose ps
```


## Отдельная база для ветки main

Если существующая локальная база уже использовалась веткой разработки с другими миграциями V4–V9,
для main и основанных на ней веток используйте отдельный контейнер и volume:

```powershell
docker compose -f docker-compose.main.yml up -d --wait
```

Контейнер `mkx-ranked-postgres-main` доступен на `127.0.0.1:5434`, база — `mkx_ranked`,
пользователь — `mkx`, пароль берётся из `DB_PASSWORD` в `.env`. Исходный контейнер и его данные сохраняются.
В конфигурации запуска приложения (в том числе в IntelliJ IDEA) задайте
`DB_URL=jdbc:postgresql://localhost:5434/mkx_ranked`. Остальные переменные подключения остаются прежними.
При первом запуске Flyway применит миграции этой ветки V1–V4 к новой базе.

## Запуск приложения

В PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5433/mkx_ranked'
$env:DB_USERNAME = 'mkx'
$env:DB_PASSWORD = 'local-development-password'
$env:DISCORD_TOKEN = 'local-development-token'
mvn spring-boot:run
```


## Сборка

Полная сборка с тестами:

```bash
mvn clean package
```

Сборка без запуска тестов, аналогичная Docker build stage:

```bash
mvn clean package -DskipTests
```


## Тесты

```bash
mvn test
```

Структура тестов:

- `service/*Test` — unit-тесты lifecycle, регистрации, профиля, leaderboard, истории, Elo, матчей и административного фасада на Mockito;
- `discord/listeners` — маршрутизация пользовательских interactions и отсутствие repository dependency у admin listener;
- `discord/formatter` — содержимое, цвета, таблицы и разбиение длинных сообщений;
- `integration/PostgreSqlSchemaIntegrationTest` — Flyway V1–V4, Hibernate validation и ключевые PostgreSQL constraints;
- `integration/ServicePostgreSqlIntegrationTest` — сквозное поведение сервисов на PostgreSQL;
- `integration/ConcurrencyIntegrationTest` — конкурентные матчи, lifecycle, регистрация, rollback и исключение игроков.

Интеграционный base class запускает `postgres:16-alpine` через Testcontainers и динамически передаёт datasource properties Spring Boot. JDA заменяется mock bean, поэтому настоящий Discord token не нужен. Docker должен быть доступен текущему пользователю; без него полный `mvn test` завершится ошибкой запуска контейнера.

## Flyway workflow

Миграции находятся в `src/main/resources/db/migration/` и применяются по версии. Для изменения схемы:

1. не редактируйте уже применённые миграции;
2. добавьте новую migration с очередной версией и осмысленным именем;
3. синхронизируйте JPA mapping с итоговой схемой;
4. дополните schema integration tests;
5. выполните `mvn test` на доступном Docker daemon.

Редактирование применённой migration меняет checksum и ломает валидацию существующих баз.
