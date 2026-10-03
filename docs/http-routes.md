# Каталог HTTP-эндпоинтов

Все маршруты — **серверный рендеринг** (Thymeleaf-шаблоны), без REST-JSON
(кроме аякс-реакций). UI на русском. BASE = `http://localhost:8080`.

> Примечание: POST-запросы требуют **CSRF**. Анонимный POST → редирект на
> `/login`; авторизованный без CSRF → `403`.

## Аутентификация (auth)

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/login` | Форма входа | `auth/login` |
| POST | `/login` | Вход (`username`, `password`) | редирект `/` или `/login?error` |
| GET | `/register?token=` | Форма регистрации | `auth/register` |
| POST | `/register` | Регистрация по инвайт-ключу (`token`) | редирект `/` |
| POST | `/logout` | Выход (CSRF) | редирект `/` |

## Ключи (RESIDENT/MASTER) и админка (MASTER)

| Метод | URL | Доступ | Описание | Вид |
|-------|-----|--------|----------|-----|
| GET | `/keys` | RESIDENT/MASTER | Список инвайт-ключей, генерация | `keys` |
| POST | `/keys/generate` | RESIDENT/MASTER | Создать ключ (`guestKey` true → гостевой) | `keys` |
| GET | `/admin/tokens` | MASTER | (редирект на `/keys`) | — |
| POST | `/admin/generate` | MASTER | (редирект на `/keys`) | — |
| GET | `/admin/cinema` | MASTER | Панель кинотеатра | — |
| POST | `/admin/cinema/select` | MASTER | Выбрать фильм к просмотру | — |
| POST | `/admin/cinema/mark-watched` | MASTER | Отметить просмотренным | — |
| POST | `/admin/cinema/censorship` | MASTER | Установить цензурный рейтинг | — |

## Настройки (authenticated)

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/settings` | Настройки пользователя | `settings` |
| POST | `/settings/prefs` | Сохранить настройки (`defaultThreadAnonymous`, `defaultCommentAnonymous`, `defaultVisibility`, `defaultReadOnly`, `showPublicPosts`, `hideGuestThreads`) | редирект `/settings` |
| POST | `/settings/password` | Смена пароля (`oldPassword`, `newPassword`) | редирект `/settings` |

## Главная и профили (webapp)

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/` | Главная (публичный пост недели, хайлайты для RESIDENT+; кино-блок скрыт для гостей/анонимов) | `home` |
| GET | `/profile` | Личный кабинет текущего пользователя | `profile` |
| GET | `/profile/{id}` | Публичный профиль участника | `profile-user` |

## Кинотеатр (RESIDENT/MASTER)

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/cinema` | Список/дашборд кинотеатра | |
| GET | `/cinema/stat` | Статистика | |
| GET | `/cinema/add`, POST `/cinema/add` | Добавить фильм | |
| POST | `/cinema/delete/{id}` | Удалить фильм (автор/мастер) | |
| GET | `/cinema/archive` | Архив просмотренного | |
| POST | `/cinema/rate` | Оценить фильм | |
| DELETE | `/cinema/rate/{filmId}` | Удалить оценку | |

## Вишлисты (authenticated)

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/gifts` | Свой вишлист (редирект на `/gifts/user/{id}`) | |
| GET | `/gifts/add`, POST `/gifts/add` | Добавить подарок | |
| GET | `/gifts/edit/{id}`, POST `/gifts/edit/{id}` | Редактировать подарок | |
| POST | `/gifts/delete/{id}` | Удалить подарок (владелец) | |
| GET | `/gifts/users` | Каталог участников с вишлистами | |
| GET | `/gifts/user/{userId}` | Вишлист конкретного пользователя | |
| POST | `/gifts/reserve/{giftId}` | Зарезервировать подарок | |
| POST | `/gifts/unreserve/{giftId}` | Снять резерв | |

## Участники (users)

| Метод | URL | Доступ | Описание |
|-------|-----|--------|----------|
| GET | `/users` | authenticated | Каталог участников (`search`) |
| POST | `/users/{id}/ban` | MASTER | Деактивировать участника |
| POST | `/users/{id}/promote` | MASTER | Гость → резидент |
| POST | `/users/{id}/reset-password` | MASTER | Сброс пароля |

## Форум (допуск см. security.md)

### Треды
| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/forum` | Список тредов (`page`, `search`, `tag`, `type`) | `forum/list` |
| GET | `/forum/topic/create` | Форма создания треда | `forum/create` |
| POST | `/forum/topic/create` | Создать тред (`title`, `content`, `tags`, `anonymous`, `visibility`, `readOnly`, `whitelistItems[]`, `files[]`) | редирект на тред |
| GET | `/forum/topic/{id}` | Просмотр треда (`page`) | `forum/topic` |
| GET | `/forum/topic/{id}/edit` | Форма редактирования | |
| POST | `/forum/topic/{id}/edit` | Сохранить правку содержания | |
| POST | `/forum/topic/{id}/comment` | Добавить комментарий (multipart, `parentId`, `content`, `anonymous`, `files[]`) | |
| POST | `/forum/topic/{id}/continuation` | Добавить **пост-продолжение** (владелец/мастер) | |
| POST | `/forum/topic/{id}/whitelist` | Обновить белый список (`csv`) | |

### Реакции на тред
| Метод | URL |
|-------|-----|
| POST | `/forum/topic/{id}/like` |
| POST | `/forum/topic/{id}/unlike` |
| POST | `/forum/topic/{id}/dislike` |
| POST | `/forum/topic/{id}/undislike` |

### Посты и комментарии
| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/forum/post/{id}/edit` | Форма правки поста |
| POST | `/forum/post/{id}/edit` | Сохранить правку |
| POST | `/forum/post/{id}/delete` | Удалить пост (автор/владелец треда/мастер) |
| POST | `/forum/post/{id}/like` / `unlike` | Лайк/отмена |
| POST | `/forum/post/{id}/dislike` / `undislike` | Дизлайк/отмена |

### Вложения
| Метод | URL | Доступ |
|-------|-----|--------|
| GET | `/forum/attachment/{id}` | authenticated |
| GET | `/forum/public/attachment/{id}` | permitAll (для публичных постов, главная) |

### Модерация треда
| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/forum/topic/{topicId}/moderation/freeze` | Заморозить |
| POST | `/forum/topic/{topicId}/moderation/unfreeze` | Разморозить |
| POST | `/forum/topic/{topicId}/moderation/delete` | Удалить тред |
| POST | `/forum/topic/{topicId}/moderation/ban` | Бан пользователя в треде |
| POST | `/forum/topic/{topicId}/moderation/unban` | Снять бан |

### Уведомления и подписки
| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/notifications` | Список уведомлений |
| POST | `/notifications/{id}/read` | Прочитать одно (`to` = url перехода) |
| POST | `/notifications/read-all` | Прочитать все |
| POST | `/subscriptions/user/{id}/toggle` | Подписка на пользователя |
| POST | `/subscriptions/topic/{id}/toggle` | Подписка на тред |

## Шаблоны

| Шаблон | Назначение |
|--------|-----------|
| `home` | Главная |
| `profile` / `profile-user` | Профили |
| `settings` | Настройки |
| `keys` | Инвайт-ключи |
| `forum/list` | Список тредов |
| `forum/create` | Создание треда |
| `forum/topic` | Просмотр треда + дерево комментариев |
| `forum/post` | Фрагмент карточки поста/комментария |
| `forum/attachments` | Фрагмент вложений |
| `notifications` | Уведомления |
| `fragments/nav` | Общая навигация |
| `auth/login`, `auth/register` | Аутентификация |
| `cinema/*`, `gifts/*`, `users/*`, `admin/*` | Доменные страницы |

## RPG: листы персонажей, списки и ячейки заклинаний (релиз 3.1.0)

Все маршруты под `/rpg/games/{gameId}`. POST требует CSRF и права
владельца листа или ведущего игры.

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/apply` | Форма заявки/нового листа | `rpg/apply` |
| POST | `/apply` | Сохранение заявки и листа (`attributes[i].listJson` для списков) | редирект на игру |
| GET | `/npc/new` | Форма нового NPC | `rpg/apply` |
| POST | `/npc` | Создание NPC | редирект на игру |
| GET | `/characters/{sheetId}` | Просмотр листа | `rpg/character` |
| GET | `/characters/{sheetId}/edit` | Форма редактирования листа | `rpg/apply` |
| POST | `/characters/{sheetId}/edit` | Сохранение листа | редирект на лист |
| POST | `/characters/{sheetId}/spell-slots-reset` | Сброс ячеек: текущее = максимальному для всех непустых уровней | редирект на лист |

**Списки правятся только в режиме редактирования.** Отдельных POST-эндпоинтов
добавления/удаления элементов списка нет: `GET /characters/{sheetId}` показывает
списки только для чтения (карточки с сворачиванием), без кнопок и модальных окон.
Правка выполняется через `POST /characters/{sheetId}/edit` с `listJson`.

### Типы списков листа

| Тип | Хранится | Правка |
|-----|----------|--------|
| «Простой список» (`SIMPLE`) | `["Факел", "Хлеб"]` | карточки + поле ввода + кнопка «+» |
| «Сложный список» (`COMPLEX`) | `[{"header":"...","description":"...","level":1}, ...]` | карточки + модальное окно добавления |

Тип списка задаётся системой в `AttributeSpec.listKind` и хранится в
`rpg_character_attributes.list_kind`. Инвентарь всегда простой; в D&D умения,
черты и заклинания — сложные. Старые листы читаются в обоих форматах: тип
выводится из JSON, если `list_kind` не задан.

### Списки: что доступно в просмотре

Изменять лист из режима просмотра нельзя. Два исключения оговорены отдельно:

- **инвентарь** (простой список) — добавление и удаление прямо в просмотре
  (`inventory-add` / `inventory-remove`);
- **сброс ячеек заклинаний** — `POST .../spell-slots-reset`.

Остальные списки правятся только через `POST /characters/{sheetId}/edit`.


## RPG: игры (релиз 3.1.0)

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/rpg/games` | Список игр; кнопка «Системы» — только авторизованным |
| GET | `/rpg/games/new?system=CUSTOM&customSystemId={id}` | Форма создания игры с предвыбранной системой |
| POST | `/rpg/games` | Создание игры; `customSystemId` переносит анкету системы в параметры листа |
| GET | `/rpg/games/{id}` | Страница игры; «Редактировать лист» ведёт на `characters/{sheetId}/edit`, если лист есть |
| GET | `/rpg/games/{id}/attributes` | Параметры листа ведущего; для `LIST` выбирается вид списка |
| POST | `/rpg/games/{id}/attributes` | Добавить параметр: `name`, `attributeType`, `listKind` |

## Библиотека ролевых систем (релиз 3.1.0)

Кнопка «Системы» на странице `/rpg/games` показывается только авторизованным.

Система публикуется в общую библиотеку с указанием названия, описания, видимости
и **состава параметров листа** (типовая анкета). Для параметра типа `LIST`
автор выбирает вид списка: `SIMPLE` или `COMPLEX`. При создании игры на этой
системе анкета переносится в `rpg_custom_attributes`, поэтому ведущему не нужно
заполнять параметры вручную (уже созданные параметры игры не перезаписываются).

| Метод | URL | Описание | Вид |
|-------|-----|----------|-----|
| GET | `/rpg/systems` | Список доступных систем | `rpg/systems-list` |
| GET | `/rpg/systems/new` | Форма публикации | `rpg/system-form` |
| POST | `/rpg/systems/new` | Публикация (`name`, `description`, `published`, `templateAttributes[i].*`) | редирект на систему |
| GET | `/rpg/systems/new?forkFrom={id}` | **Форма создания, предзаполненная из системы `{id}`** — состав, описание, название с суффиксом «(форк)», приватная видимость. Копия создаётся только после `POST /rpg/systems/new` | `rpg/system-form` |
| GET | `/rpg/systems/{id}` | Страница системы: состав параметров, предпросмотр, авторские правка/форк/удаление | `rpg/system-view` |
| GET | `/rpg/systems/{id}/edit` | Редактирование (автор) | `rpg/system-form` |
| POST | `/rpg/systems/{id}/edit` | Сохранение (автор) | редирект на систему |
| GET | `/rpg/systems/{id}/character` | **Режим создания персонажа по правилам системы** — открыт любому пользователю, показывает состав параметров системы; лист не сохраняется, кнопки отправки нет, форма не отправляется (`onsubmit="return false"`) | `rpg/apply` (`previewMode`) |
| POST | `/rpg/systems/{id}/delete` | Удаление (автор, CSRF) | редирект в библиотеку |
