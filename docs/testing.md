# Тестирование

## 1. Слои тестов

| Слой | Где живёт | Инструменты | Цель |
|------|-----------|-------------|------|
| Unit | `*/src/test/java` рядом с сервисом | JUnit 5 + Mockito | Логика сервисов в изоляции |
| Интеграционные | `webapp/src/test` | `@SpringBootTest` + MockMvc (+ реальная БД через Docker) | Сквозные сценарии, MVC-шаблоны, CSRF, роли |

Подход (AGENTS.md): «unit-тесты сервисного слоя до реализации», стиль —
как в `wishlist/src/test`.

## 2. Карта тестов

### Unit (по модулям)
| Модуль | Класс | Покрытие |
|--------|-------|----------|
| common | `PasswordGeneratorTest` | Генератор паролей |
| auth | `AuthServiceTest`, `TokenServiceTest`, `SettingsServiceTest` | Регистрация по ключу, вход/выход, ключи, настройки |
| cinema | `CinemaServiceTest`, `RatingServiceTest` | Фильмы, рулетка, оценки |
| wishlist | `WishlistServiceTest` | Подарки, резервы, лимиты |
| forum | `ForumServiceTest`, `PostServiceTest`, `RatingStatsServiceTest`, `ForumPostDtoTest` | Треды, посты, реакции, рейтинги |
| users | `MemberServiceTest` | Каталог, promotion, баны |

### Интеграционные (webapp)
| Класс | Сценарии |
|-------|----------|
| `RegistrationFlowIntegrationTest` | Регистрация по ключу, вход, доступ к главной (в т.ч. аноним) |
| `ForumIntegrationTest` | Доступ к форуму (аноним: GET 200, POST 302/403), треды, комментарии |
| `ForumVisibilityIntegrationTest` | Видимость тредов по ролям/visibility |
| `RolesAndGuestThreadsIntegrationTest` | Роли, гостевые треды |
| `CinemaIntegrationTest` | Кинотеатр (+ скрытие для гостей) |
| `WishlistIntegrationTest` | Вишлисты |
| `WishlistConcurrencyTest` | Конкурентный резерв подарка |
| `MembersIntegrationTest` | Каталог участников |
| `SettingsIntegrationTest` | Настройки |
| `DesignStyleIntegrationTest` | Единообразие стилей/шаблонов |

## 3. БД под интеграционными тестами

- Интеграционные тесты используют реальный PostgreSQL (обычно — поднятый
  Docker-контейнер `corpsman-postgres`). Скрипты/файлы тестов пишут
  временные данные в `/home/albert/Project/tmp/` (например, `tmp/test.log`).
- Тесты полагаются на автоматическую инициализацию схемы через Liquibase.

## 4. Команды

```bash
mvn test                                  # все модули (≈ 5,5 мин)
mvn clean install                         # сборка + тесты
mvn test -pl webapp -am                   # webapp + зависимости
mvn -q install -DskipTests && mvn test -pl forum   # быстрая точечная
```

Слишком долго (342 с)? — см. `getting-started.md` → «Сборка и тестирование»
(правило stale-jar, `-T 1C` не использовать).

## 5. Проверка успеха

Успешный прогон: `[INFO] BUILD SUCCESS`, при этом должно быть
`Tests run: 460, Failures: 0, Errors: 0` (на релиз 2.2.0).

```bash
mvn test -pl webapp -am > /home/albert/Project/tmp/test.log 2>&1
grep "BUILD" /home/albert/Project/tmp/test.log | tail -1
```

## 6. Правила добавления тестов

1. Тестируйте **сервисный слой** (unit) до/вместе с реализацией.
2. Интеграционные сценарии — в `webapp` (там есть MVC-шаблоны и
   подключение БД).
3. Не дублируйте тесты (см. AGENTS.md «Избегай дублирования тестов»).
4. Добейтесь зелёных unit-тестов до запуска интеграционных из webapp.
5. Если тест требует доступа к анонимным/гостевым сценариям — учитывайте
   новую матрицу (аноним GET /forum = 200, POST = 302, без CSRF = 403).
