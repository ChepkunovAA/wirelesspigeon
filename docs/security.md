# Безопасность, роли и доступ

## 1. Модель пользователя и роли

Три роли (`auth/model/UserRole.java`):

| Роль | Доступные области | Комментарий |
|------|-------------------|-------------|
| `GUEST` | Форум (только открытые/гостевые и закрытые треды, где добавлен), вишлисты, профили | Ограниченный участник |
| `RESIDENT` | Полный доступ к форуму, кинотеатру, вишлистам | Полноправный участник |
| `MASTER` | Всё, включая `/admin/**`, генерацию ключей, промоушен, баны | Администратор |

Атрибуты пользователя (`auth/model/User.java`):
- `username` (unique), `passwordHash` (BCrypt strength 12),
- `role` (`UserRole`, по умолчанию `RESIDENT`),
- `active` (по умолчанию `true`),
- `lastLoginAt`.

`CorpsmanUserDetails` — Spring Security-обёртка над `User`, выдаёт
единственную authority `ROLE_<ROLE>`.

## 2. Аутентификация

- **Session-based** (не JWT): сессия `JSESSIONID` хранится в памяти,
  таймаут 30 минут.
- Вход: `POST /login` (`username`, `password`) → `AuthenticationManager`
  → `DaoAuthenticationProvider` + `BCryptPasswordEncoder`.
- Регистрация: только по одноразовому **инвайт-ключу**
  (`POST /register` с `token`). Ключ хранится как **BCrypt-хэш**
  (`InviteToken.codeHash`), действует **7 дней**, однократный.
  - Гостевой ключ (`guestKey=true`) регистрирует пользователя с ролью `GUEST`;
  - обычный ключ — с ролью `RESIDENT`.
- Выход: `POST /logout` (CSRF-защищён).

### Поток входа
`AuthController.login` -> `AuthenticationManager.authenticate(...)` ->
при успехе `SecurityContextHolder` + редирект `/`; при неудаче редирект
`/login?error`.

## 3. Каталог доступа (SecurityConfig)

`auth/config/SecurityConfig.java` — цепочка фильтров:

| URL pattern | Требование |
|-------------|------------|
| `/`, `/login`, `/register`, `/error`, `/static/**`, `/css/**`, `/js/**`, `/images/**`, `/assets/**`, `/public/**`, `/forum/public/**` | `permitAll` |
| `GET /forum`, `GET /forum/topic/*` | `permitAll` (аноним видит публичные треды) |
| `/admin/**` | `hasRole("MASTER")` |
| `/keys/**` | `hasAnyRole("RESIDENT","MASTER")` |
| `POST /users/*/promote` | `hasRole("MASTER")` |
| `POST /users/*/ban` | `hasRole("MASTER")` |
| `POST /users/*/reset-password` | `hasRole("MASTER")` |
| `/cinema/**` | `hasAnyRole("RESIDENT","MASTER")` |
| всё остальное | `authenticated` |

- **CSRF** включён (`Customizer.withDefaults()`) для всех POST-запросов.
  Отсутствие CSRF-токена → **403**.
- `formLogin` и `httpBasic` отключены; вход — через ручной контроллер.
- Неавторизованный доступ к защищённой странице → **редирект на `/login`**
  (authenticationEntryPoint `sendRedirect("/login")`).
- `SessionCreationPolicy.IF_REQUIRED`.

## 4. Важные следствия для POST-запросов

- Любое **записывающее** действие требует CSRF-токен.
- **Аноним** (неавторизованный) при POST → редирект на `/login` (302).
- **Авторизованный** без корректного CSRF-токена → `403`.

При curl-тестировании всегда используйте связку cookie-jar + CSRF:
```bash
# 1. Получить CSRF из формы логина
CSRF=$(curl -s -c jar http://localhost:8080/login \
  | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"$//')
# 2. Залогиниться (cookies + csrf)
curl -s -b jar -c jar -o /dev/null \
  --data-urlencode "username=admin" --data-urlencode "password=admin" \
  --data-urlencode "_csrf=$CSRF" http://localhost:8080/login
# 3. Дальше все запросы с -b/-c jar и _csrf где нужно
```

## 5. Гостевой доступ к форуму (анонимы)

- `GET /forum` и `GET /forum/topic/{id}` доступны анониму (`permitAll`).
- Аноним принудительно видит только треды `visibility=PUBLIC`
  (`ForumService.getTopics`, `ForumController.topicList`).
- Аноним не может писать, ставить реакции, создавать треды (POST → редирект
  на `/login`), а защищённые непубличные треды анониму недоступны
  (`getTopic` возвращает `TOPIC_NOT_FOUND` для не-PUBLIC).

## 6. Видимость тредов форума (TopicVisibility)

`forum/model/TopicVisibility.java`:

| Значение | Отображаемый лейбл | Виден кому | Кто может писать |
|----------|--------------------|------------|------------------|
| `PUBLIC` | «Публичный» | всем, включая анонимов; выводится на главной | все (по правилам ролей) |
| `OPEN` | «Открытый» | резидентам и мастерам (гостям скрыт) | резиденты/мастера |
| `GUEST` | «Открытый» + «доступен гостям» | как OPEN, но гости могут писать и модерировать | включая гостей |
| `CLOSED` | «Закрытый» | только владельцу, мастерам и белому списку | по белому списку |

> **Внимание:** это лейблы **отображения** (2.2.0, п.5; терминология
> унифицирована в 2.3.0, п.5). Значения в БД/enum не переименовывались —
> только то, что показывается. Оба OPEN и GUEST показываются как
> «Открытый»; у GUEST добавляется признак «доступен гостям».

Правила создания тредов:
- Гость (`GUEST`) может создавать только **`GUEST`** (открытые для гостей)
  и **`CLOSED`** треды; любые другие значения принудительно
  подменяются на `GUEST` (`ForumService.createTopic`).
- Резидент/мастер создают «Открытый» тред и чекбоксом «доступен гостям»
  выбирают `OPEN`/`GUEST`; типы `PUBLIC` и `CLOSED` остаются отдельными
  опциями формы. Фильтр «Открытые» в списке форума показывает и OPEN, и GUEST
  (`ForumService.getTopics` собирает оба значения).

Правила чтения закрытого треда (`canViewClosed`, `ForumService:1018`):
- виден владельцу, мастерам, пользователям из белого списка треда.

Правила взаимодействия с закрытым (`canInteractClosed`, `ForumService:1050`):
- писать/ставить реакции в `CLOSED` может только владелец, мастер или член
  белого списка.

## 7. Анонимность

- Флаг `is_anonymous` в `forum_topics` и `forum_posts`.
- При `anonymous = true` имя реального автора скрыто: отображается «Аноним».
- **Администратор (MASTER)** может видеть реального автора.

## 8. Модерация

Треды:
- **freeze / unfreeze / delete** — владелец треда или мастер
  (`ForumService.freezeTopic/unfreezeTopic/deleteTopic`, `canModerate :836`).
- **ban / unban** пользователя **в треде** — модератор треда или мастер
  (`TopicBan`, `ForumService.banUser/unbanUser`). Забаненный не может
  писать в этом треде, но видит его.

Посты/комментарии:
- Удаление поста — автор поста, **владелец треда**, или мастер.
  Заглушки: администратор → «Удалено администратором», владелец треда →
  «Удалено владельцем треда», иначе «[Удалено]».

Аккаунты (`users`):
- **ban** аккаунта (`MembersController.ban`, MASTER) — деактивация
  пользователя.
- **promote** (`promoteToResident`, MASTER) — `GUEST` → `RESIDENT`
  (нельзя промоутить `MASTER`).
- **reset-password** (MASTER).

## 9. Кинотеатр — только RESIDENT+

- `/cinema/**` — `hasAnyRole("RESIDENT","MASTER")`.
- На главной и в профилях кино-блок скрыт для гостей и анонимов
  (`filmsHidden` / `ownCinemaHidden` / `cinemaHidden`).

## 10. Рекомендации при добавлении нового эндпоинта

1. Продумайте, к какой роли относится: добавьте правило в
   `SecurityConfig`, если оно не покрывается общим `authenticated`.
2. Не забудьте про CSRF для POST (шаблоны Thymeleaf добавляют
   `_csrf` автоматически через `th:action`-формы).
3. Для анонимов: если страница должна быть доступна без логина —
   добавьте `permitAll` и проверьте, что внутри она обрабатывает
   `user == null`.
4. Проверяйте права в **сервисе** (по `userId`), а не только в контроллере.
