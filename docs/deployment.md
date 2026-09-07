# Развёртывание

## Текущая схема

Репозиторий содержит multi-stage Dockerfile и Docker Compose-конфигурацию из двух сервисов.

```text
VPS
└── Docker Compose
    ├── bot
    │   ├── Spring Boot
    │   ├── JDA
    │   └── Flyway
    └── PostgreSQL 16
        └── persistent volume mkx_postgres_data
```

`postgres` имеет healthcheck. `bot` запускается после перехода базы в healthy state и имеет `restart: unless-stopped`. Build stage использует Maven/Temurin 21 и выполняет `mvn clean package -DskipTests`; runtime stage использует Temurin 21 JRE.

## `.env`

Создайте `.env` рядом с `docker-compose.yml`:

```dotenv
DB_URL=jdbc:postgresql://postgres:5432/mkx_ranked
DB_PASSWORD=replace-with-a-strong-password
DISCORD_TOKEN=replace-with-the-production-bot-token
```

Compose жёстко задаёт `DB_USERNAME=mkx`; PostgreSQL создаётся с базой `mkx_ranked` и тем же пользователем. `DB_URL` должен использовать service name `postgres` и внутренний порт `5432`, а не host-порт `5433`.

Храните `.env` только на сервере с ограниченными правами. Он исключён из Git и Docker build context. Не помещайте token или пароль в Dockerfile, compose-файл, логи или документацию.

## Первый запуск

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f bot
```

Ожидаемое состояние: `postgres` — healthy, `bot` — running. При старте bot Flyway применяет миграции, затем Hibernate валидирует схему и JDA подключается к Discord.

Текущий compose публикует PostgreSQL как `5433:5432` на host. На VPS ограничьте доступ firewall’ом или сетевой политикой; приложению внутри Compose публикация порта не нужна.

## Эксплуатационные команды

Просмотр статуса и последних логов:

```bash
docker compose ps
docker compose logs --tail=200 bot
docker compose logs --tail=200 postgres
```

Поток логов приложения:

```bash
docker compose logs -f bot
```

Перезапуск приложения без удаления данных PostgreSQL:

```bash
docker compose restart bot
```

## Обновление

Перед обновлением сделайте backup. После получения новой версии исходного кода:

```bash
docker compose build bot
docker compose up -d bot
docker compose ps
docker compose logs --tail=200 bot
```

Новые Flyway migrations применятся при старте нового контейнера. Если обновляются параметры Compose или образ PostgreSQL, используйте `docker compose up -d --build` для всего проекта.

## Persistent volume

Данные находятся в named volume `mkx_postgres_data` (фактическое Docker-имя обычно получает prefix Compose project). Пересоздание контейнера и `docker compose down` volume не удаляют. Команда `docker compose down -v` удаляет volume и данные, поэтому в штатной эксплуатации её использовать нельзя.

Volume не заменяет backup: он защищает только от пересоздания контейнера, но не от ошибочного удаления, повреждения данных или потери VPS.

## Backup

Следующие команды рассчитаны на shell VPS и существующие имена сервиса, пользователя и базы из `docker-compose.yml`.

Создание custom-format dump:

```bash
mkdir -p backups
docker compose exec -T postgres pg_dump \
  -U mkx \
  -d mkx_ranked \
  -Fc > backups/mkx_ranked_$(date +%Y%m%d_%H%M%S).dump
```

Проверьте, что файл существует и имеет ненулевой размер, затем храните копию вне VPS. Backup содержит данные и Flyway history, но не `.env` и не Discord token.

## Restore

Restore изменяет содержимое базы. Сначала остановите bot и сохраните отдельный backup текущего состояния:

```bash
docker compose stop bot
docker compose exec -T postgres pg_restore \
  --clean \
  --if-exists \
  --no-owner \
  --exit-on-error \
  -U mkx \
  -d mkx_ranked < backups/mkx_ranked_YYYYMMDD_HHMMSS.dump
docker compose start bot
docker compose logs --tail=200 bot
```

`--clean --if-exists` удаляет восстанавливаемые объекты перед их созданием. Выполняйте restore только из проверенного dump, совместимого с развернутой версией приложения. После запуска убедитесь, что Flyway validation проходит и контейнер не перезапускается циклически.
