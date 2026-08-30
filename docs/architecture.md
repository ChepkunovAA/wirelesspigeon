# Архитектура Corpsman

## 1. Бизнес-цель и контекст

Закрытое веб-приложение для небольшого закрытого сообщества (до ~500
активных пользователей). Функциональные блоки:

- **Форум** — треды, посты, комментарии, реакции, анонимность, модерация.
- **Вишлисты** — списки подарков с резервированием.
- **Личные блоги / профили** — публичные страницы участников.
- **Кинотеатр** — предложения фильмов, рулетка «следующий для просмотра»,
  оценки, цензурные рейтинги, архив.

## 2. Технологический стек

| Компонент | Технология | Версия | Назначение |
|-----------|-----------|--------|------------|
| JDK | OpenJDK | 17 LTS | Рантайм |
| Фреймворк | Spring Boot | 3.2.5 | Веб-приложение |
| Web | Spring MVC | 6.1.x | Контроллеры, запрос/ответ |
| Безопасность | Spring Security | 6.x | Аутентификация, авторизация, CSRF |
| ORM | Spring Data JPA (Hibernate) | 6.4.x | Доступ к БД |
| БД | PostgreSQL | 16 | Хранилище |
| Миграции | Liquibase | 4.25.1 | Версионирование схемы |
| Маппинг | MapStruct | 1.5.5 | Генерация DTO-мапперов в compile-time |
| Кеш | Caffeine | 3.1.x | In-memory кеш |
| Логирование | SLF4J + Logback | — | Журналирование |
| Тесты | JUnit 5 + Mockito | — | Unit + интеграционные |
| Фронтенд | Thymeleaf 3.1 / Bootstrap 5.3 / Alpine.js 3.13 | — | Серверный рендеринг + интерактив |

## 3. Модули Maven

Граф зависимостей:

```
     common
       │
       ▼
      auth
       │
   ┌───┼───────┬─────────┐
   ▼          ▼         ▼
 cinema    wishlist   forum   users
   └───────────┼─────────┘
               ▼
            webapp  (точка входа)
```

- **`common`** — база: `BaseEntity`, `BusinessException`/`ErrorCode`,
  события (`BaseEvent` + конкретные события), `ErrorDto`, `UserDto`,
  утилиты, общие конфигурации.
- **`auth`** — регистрация по инвайт-ключам, аутентификация, сессии,
  роли, настройки пользователя (`User`, `InviteToken`, `UserSettings`),
  `SecurityConfig`.
- **`cinema`** — фильмы, статусы жизненного цикла, оценки, цензурные
  рейтинги, рулетка.
- **`wishlist`** — подарки и резервирование.
- **`forum`** — треды, посты, комментарии, реакции, теги, белые списки,
  баны, подписки, уведомления.
- **`users`** — каталог участников, promotion, баны аккаунтов.
- **`webapp`** — точка входа (`CorpsmanApplication`), главная страница,
  глобальные атрибуты модели, все Thymeleaf-шаблоны и статика, миграции.

> Архитектура изначально предполагала отдельный модуль `blog`; на практике
> блоги/профили реализованы как `users` + профильные страницы в `webapp`.

## 4. Слои внутри модуля (MVCS)

Каждый доменный модуль придерживается единой структуры пакетов:

```
org.corpsman.<domain>/
├── config/        # @Configuration, @EventListener
├── model/         # JPA-сущности (наследуют BaseEntity)
├── repository/    # Spring Data JPA репозитории
├── service/       # бизнес-логика
├── dto/           # транспортные объекты
├── mapper/        # MapStruct-мапперы сущность ↔ DTO
├── web/           # контроллеры (@Controller + @RequestMapping)
└── security/      # (auth) Spring Security интеграция
```

Правила:
- Сущности наследуют `BaseEntity` → имеют `id`, `createdAt`, `updatedAt`,
  `version` (оптимистичная блокировка).
- Доменная логика — только в `service`-классах, контроллеры остаются
  тонкими (`param → service → model → view`).
- DTO преобразуются через MapStruct (`*Mapper`), мапперы регистрируются
  как Spring-bean (`componentModel = "spring"`).
- Ошибки бизнес-правил — `BusinessException(ErrorCode, ...)`,
  обрабатываются `GlobalExceptionHandler` → `ErrorDto`.

### Стиль публичных методов сервиса
```java
// 1) публичные методы (API сервиса)
public FilmDto addFilm(FilmDto dto, Long userId) { ... }
// 2) приватные методы (внутренняя логика)
private Film saveFilm(FilmDto dto) { ... }
// 3) обработчики событий (@EventListener)
public void onUserDeleted(UserDeletedEvent event) { ... }
```

## 5. Идентификация текущего пользователя

`SecurityUtils.currentUser()` / `SecurityUtils.currentUserId()` возвращают
текущего аутентифицированного пользователя (`CorpsmanUserDetails`) или
`null` для анонима. Используется во всех контроллерах и сервисах.

Примеры:
```java
CorpsmanUserDetails user = SecurityUtils.currentUser(); // может быть null
Long userId = SecurityUtils.currentUserId();            // может быть null
```

## 6. Кеширование

`common/config/CacheConfig.java` настраивает **Caffeine**. Используется,
например, для рулетки кинотеатра и подсчётов рейтингов.

## 7. Конфигурация приложения (application.yml)

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/appdb}
    username: ${SPRING_DATASOURCE_USERNAME:app_user}
    password: ${SPRING_DATASOURCE_PASSWORD:secret}
  jpa:
    hibernate:
      ddl-auto: validate          # схема управляется только Liquibase
    open-in-view: false           # запрет Lazy-load за пределами транзакции
  servlet:
    multipart:
      max-file-size: 100MB        # вложения форума
      max-request-size: 100MB
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.xml
  cache:
    type: caffeine
  thymeleaf:
    cache: false                  # dev: шаблоны перечитываются без рестарта

app:
  upload-dir: ${APP_UPLOAD_DIR:uploads}   # каталог файлов-вложений

server:
  port: 8080
  servlet:
    session:
      timeout: 30m

logging:
  level:
    org.corpsman: DEBUG
    org.hibernate.SQL: INFO
```

Ключевые особенности:
- `ddl-auto: validate` — Hibernate только **проверяет** схему, изменения
  делаются строго через Liquibase.
- `open-in-view: false` — важно: доступ к ленивым связям вне транзакции
  даст `LazyInitializationException`; сервисы должны быть `@Transactional`.

## 8. Точка входа

`webapp/.../CorpsmanApplication.java`:
```java
@SpringBootApplication(scanBasePackages = "org.corpsman")
@EntityScan(basePackages = "org.corpsman")
@EnableJpaRepositories(basePackages = "org.corpsman")
```
Сканирует все модули (`org.corpsman.*`), все сущности и репозитории
подхватываются автоматически без ручной регистрации.
