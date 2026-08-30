# Установка, сборка и запуск

## 1. Требования

- JDK **17+**
- Maven **3.9+**
- Docker + Docker Compose (для запуска PostgreSQL и приложения)
- PostgreSQL 16 (локально, если запускать без Docker)

## 2. Клонирование и ветки

```bash
git clone <repo> corpsman
cd corpsman
```

- **`develop`** — основная рабочая ветка. **Запрещено коммитить в `master`.**
  Каждый спринт/фича завершается коммитом в `develop`.
- **`feature/https`** — отдельная ветка с HTTPS-профилями сборки
  (двухпрофильный compose, Caddy, `SITE_ADDRESS`). Параллельно с develop.

## 3. Локальный запуск через Docker (рекомендуется)

```bash
docker compose up -d --build
```

Что поднимается (`docker-compose.yml`):

| Сервис | Контейнер | Доступ |
|--------|-----------|--------|
| postgres:16-alpine | `corpsman-postgres` | `localhost:5432` (`appdb` / `app_user` / `secret`) |
| app (jar) | `corpsman-app` | `http://localhost:8080` |

Приложение ждёт готовности PostgreSQL (healthcheck `pg_isready`),
база инициализируется автоматически через Liquibase.

Полезные команды:
```bash
docker compose ps                 # статус
docker compose logs -f app        # логи приложения
docker compose logs -f postgres   # логи БД
docker compose restart app
docker compose down               # остановить (данные БД сохраняются в volume)
docker compose down -v            # остановить + удалить том БД (СБРОС данных)
```

> Если контейнер `app` «Restarting» — чаще всего не поднялась БД
> (`UnknownHostException: postgres`). Проверьте `docker compose ps` и
> поднимите стек заново: `docker compose up -d --force-recreate`.

## 4. Запуск без Docker (полная локальная среда)

```bash
# 1. Поднять только PostgreSQL
docker compose up -d postgres

# 2. Собрать и запустить приложение
mvn -q clean package -DskipTests
java -jar webapp/target/webapp-*.jar
# -> http://localhost:8080
```

## 5. Сборка и тестирование

### Полная сборка с тестами

```bash
mvn clean install        # полная, ~6 минут
mvn test                 # только тесты (все модули)
mvn test -pl webapp -am  # webapp + зависимости
```

### Точечная проверка модуля + правило «stale-jar»

`-pl <module>` берёт jar зависимостей из `~/.m2`. Если вы меняли
`common`/`auth`/`forum` и т.п., нужно переустановить артефакты, иначе
получите старое поведение при «зелёном» исходнике
(`ClassNotFoundException` / старая логика).

```bash
mvn -q install -DskipTests   # разовая переустановка всех артефактов
mvn test -pl forum           # точечно, ~15 с
```

### Измерение времени (релиз 2.0.0)

| Способ | Время |
|--------|-------|
| `mvn test` (полный) | ~342 с |
| `mvn test -T 1C` | ~348 с (**не использовать** — ускорения нет) |
| `mvn -q install -DskipTests` (разово) | ~39 с |
| после install: `mvn test -pl <module>` | 16–17 с/модуль |

webapp = ~83% времени (все интеграционные тесты с MVC-шаблонами).

## 6. HTTPS / Продакшен (отдельная ветка feature/https)

HTTPS реализован **вариантом C (Caddy)** в ветке `feature/https`. Сборка
разделена на два профиля:

```bash
# Локальный HTTP (без Caddy)
docker compose up -d --build
# -> http://localhost:8080

# Продакшен HTTPS (домен из окружения, Let's Encrypt автоматически)
docker compose -f docker-compose.prod.yml up -d --build
SITE_ADDRESS=corpsman.example.com docker compose -f docker-compose.prod.yml up -d --build
```

Переменные окружения:
- `SITE_ADDRESS` — домен (по умолчанию `localhost`, internal CA).
- `COOKIE_SECURE=true` — включает `Secure`+`HttpOnly`+`SameSite=Lax`
  (в prod-профиле включена; в локальном HTTP-профиле `false`).

E2E по HTTPS: `curl -sk https://localhost/...` (`-k` из-за internal CA),
HTTP автоматически редиректится на HTTPS (308).

Подробности: `doc/https-migration.md`, сравнение вариантов A/B/C.

### Чеклист проверки после HTTPS-миграции
1. `https://<host>` открывается, замок валиден.
2. `http://<host>` → 301/302 на https.
3. Логин/логаут работают; JSESSIONID: `Secure`+`HttpOnly`, `SameSite=Lax`.
4. CSRF: создание треда, комментарий, лайк, whitelist, ban — работают.
5. Загрузка файлов (до 100MB) проходит через прокси.
6. Вложения форума открываются по https (`/forum/public/attachment/*`).
7. HSTS присутствует (после стабилизации).
8. `mvn test` зелёный.

### Откат HTTPS
Остановить прокси-контейнер, вернуть маппинг `8080:8080` — приложение
работает по HTTP без изменений кода.

## 7. Демо-доступ

После первого запуска (данные инициализируются автоматически) вход:
- `http://localhost:8080/login` — форма логина.
- Регистрация — только по инвайт-ключу (`/register?token=...`). Ключи
  генерирует MASTER на `/keys`.

## 8. Распространённые проблемы

- **`ddl-auto: validate` упал** — схема БД не совпадает с сущностями.
  Не применяйте `ddl-auto: create`; добавляйте миграцию Liquibase.
- **`LazyInitializationException`** — `open-in-view: false`; убедитесь,
  что сервис `@Transactional` и данные загружены в транзакции.
- **Контейнер `app` перезапускается** — смотрите `docker compose logs app`;
  типичная причина — недоступность БД.
