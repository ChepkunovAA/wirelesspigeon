# Corpsman Backend — Документация разработчика

Закрытая веб-платформа для небольшого сообщества (до ~500 активных
пользователей): **анонимный форум**, вишлисты (подарки), личный блог,
локальный кинотеатр (общий просмотр).

Документация описывает приложение **по состоянию на релиз 2.2.0**
(ветка `develop`).

---

## Содержание

| Раздел | О чём |
|--------|-------|
| [architecture.md](architecture.md) | Стек, модули, слои, принципы, термины |
| [getting-started.md](getting-started.md) | Установка, сборка, локальный запуск, Docker, HTTPS |
| [security.md](security.md) | Аутентификация, роли, CSRF, матрица доступа, видимость тредов |
| [database.md](database.md) | Схема БД, миграции Liquibase, сущности, индексы |
| [forum.md](forum.md) | Домен форума: треды, посты, анонимность, модерация, уведомления |
| [http-routes.md](http-routes.md) | Полный каталог HTTP-эндпоинтов |
| [events.md](events.md) | Внутренняя шина событий (pub/sub между модулями) |
| [frontend.md](frontend.md) | Thymeleaf, Alpine.js, Bootstrap, фрагменты, тема |
| [testing.md](testing.md) | Стратегия тестирования, команды, измерение времени |
| [releases.md](releases.md) | История релизов и спринтов |

---

## Быстрый старт (TL;DR)

```bash
# 1. Поднять PostgreSQL + приложение
docker compose up -d --build

# 2. Приложение: http://localhost:8080
#    PostgreSQL: localhost:5432 (appdb / app_user / secret)

# 3. Полные тесты (≈ 5,5 мин, 460 тестов)
mvn test -pl webapp -am

# 4. Быстрые точечные тесты одного модуля (после разовой установки артефактов)
mvn -q install -DskipTests
mvn test -pl forum
```

> **Производительность сборки:** весь набор тестов — ~342 с, из них ~83%
> приходится на webapp (интеграционные тесты с MVC-шаблонами). Для
> точечной проверки модулей используйте правило **stale-jar** (см.
> `getting-started.md` → «Сборка и тестирование»).

---

## Ключевые факты об архитектуре

- **Многомодульный Maven-проект** (`common → auth → {cinema, wishlist,
  forum, users} → webapp`). Доменные модули зависят только от `common`
  и `auth`.
- **MVCS** (Model-View-Controller-Service). Все DTO маппятся через
  **MapStruct**, все сущности наследуют `BaseEntity`, все ошибки —
  наследники `BusinessException`.
- Модули общаются через **внутренние события Spring**
  (`ApplicationEventPublisher`), а не напрямую.
- **Session-based аутентификация** (не JWT), CSRF включён, пароли BCrypt.
- Миграции БД — **Liquibase** (22 изменения в `db/changelog/v1.0/`).
- Фронтенд — серверный рендеринг **Thymeleaf 3 + Bootstrap 5 + Alpine.js**,
  JS-интерактивность пишется прямо в HTML-атрибутах `x-data`/`x-on`.

Три роли: **GUEST** (гость), **RESIDENT** (резидент), **MASTER**
(администратор). Подробная матрица доступа — в `security.md`.
