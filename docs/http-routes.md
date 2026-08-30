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
