# База данных

## 1. Обзор

- **PostgreSQL 16**, БД `appdb` (локально user `app_user` / password `secret`).
- Схема управляется **только через Liquibase**; Hibernate настроен
  `ddl-auto: validate` (проверяет, не создаёт).
- Master-файл миграций:
  `webapp/src/main/resources/db/changelog/db.changelog-master.xml`
- Все изменения — в `webapp/src/main/resources/db/changelog/v1.0/`
  (22 наборов изменений на момент 2.2.0).

## 2. Список миграций

| # | Файл | Суть |
|---|------|------|
| 01 | `01-create-users` | Таблица `users` |
| 02 | `02-create-invite-tokens` | Таблица `invite_tokens` |
| 03 | `03-create-films` | Таблица `films` |
| 04 | `04-create-film-ratings` | Таблица `film_ratings` |
| 05 | `05-normalize-ratings` | Нормализация/денормализация оценок |
| 06 | `06-create-gift-tables` | Таблицы `gifts` (+ резервирование) |
| 07 | `07-create-forum-tables` | Таблицы `forum_topics`, `forum_posts`, `post_likes`, `topic_tags`, `forum_tags`, `topic_bans` |
| 08 | `08-forum-reactions` | Реакции: `post_dislikes`, `topic_likes`, `topic_dislikes` + счётчики |
| 09 | `09-drop-topic-views` | Удаление ненужных сущностей просмотров |
| 10 | `10-topic-bans-display-name` | `topic_bans` + `display_name` |
| 11 | `11-forum-posts-reply-to` | `forum_posts.reply_to_id` (ответ на комментарий) |
| 12 | `12-forum-topics-public-post` | `forum_topics.public_post` (позже удалён в 16) |
| 13 | `13-forum-topics-content-long` | Увеличение `forum_topics.content` до 1 000 000 |
| 14 | `14-forum-attachments` | Таблица `forum_attachments` (файлы-вложения) |
| 15 | `15-create-user-settings` | Таблица `user_settings` |
| 16 | `16-topic-visibility-continuations` | `forum_topics.visibility`; `forum_posts.is_op`, `is_continuation`; `forum_topic_whitelist` |
| 17 | `17-default-visibility` | Значение по умолчанию `visibility = OPEN` |
| 18 | `18-read-only-admin-delete` | `read_only` тредов; `deleted_by_admin` |
| 19 | `19-roles-guest-keys` | Роли GUEST, гостевые ключи (`guest_key`) |
| 20 | `20-default-read-only` | `default_read_only` в настройках |
| 21 | `21-notifications-subscriptions` | `notifications`, `subscriptions`, `hide_guest_threads` |
| 22 | `22-deleted-by-owner` | `forum_posts.deleted_by_owner` |

## 3. Таблицы

### users (`auth/model/User.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGINT PK | |
| username | VARCHAR(100) unique NOT NULL | |
| password_hash | VARCHAR(255) NOT NULL | BCrypt |
| role | VARCHAR(20) NOT NULL | GUEST/RESIDENT/MASTER |
| active | BOOLEAN NOT NULL | |
| last_login_at | TIMESTAMP | |

### invite_tokens (`auth/model/InviteToken.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| code_hash | VARCHAR unique | BCrypt-хэш одноразового ключа |
| expires_at | TIMESTAMP NOT NULL | TTL 7 дней |
| used | BOOLEAN NOT NULL | |
| created_by | BIGINT | админ-создатель |
| guest_key | BOOLEAN NOT NULL | если true — регистрация роли GUEST |

### user_settings (`auth/model/UserSettings.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| user_id | BIGINT PK | |
| default_thread_anonymous | BOOLEAN | анонимность новых тредов |
| default_comment_anonymous | BOOLEAN | анонимность комментариев |
| default_visibility | VARCHAR(20) | PUBLIC/OPEN/CLOSED (по умолчанию OPEN) |
| default_read_only | BOOLEAN | новые треды read-only |
| hide_guest_threads | BOOLEAN | скрывать гостевые треды |
| show_public_posts | BOOLEAN | |

### films (`cinema/model/Film.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| title | VARCHAR(200) NOT NULL | |
| release_year | INTEGER | |
| trailer_url | VARCHAR(500) | |
| user_id | BIGINT NOT NULL | автор предложения |
| status | VARCHAR(20) NOT NULL | PROPOSED/SELECTED/WATCHED/DISCARDED |
| watched_at | TIMESTAMP | |
| censorship | VARCHAR(20) | CHASTE/HINTS/EROTICA/PORNOGRAPHY/NIGHTMARE |

### film_ratings (оценки фильмов)
| Колонка | Тип | Описание |
|---------|-----|----------|
| film_id | BIGINT | |
| user_id | BIGINT | уникально (film_id, user_id) — upsert |
| rating | INTEGER (1–10) | |
| review | VARCHAR | необязательный отзыв |

### gifts (`wishlist/model/Gift.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| title | VARCHAR(200) NOT NULL | |
| description | VARCHAR(1000) | |
| owner_id | BIGINT NOT NULL | владелец вишлиста |
| reservation_limit | INTEGER NOT NULL | максимум резервов |
| reserved_count | INTEGER NOT NULL | текущее число резервов |

Резервирование: таблица `gift_reservations` (`06-create-gift-tables`) —
связь подарка с пользователем-резервом `(gift_id, user_id)`; конкуренция
решается атомарно (тест `WishlistConcurrencyTest`).

### forum_topics (`forum/model/ForumTopic.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| title | VARCHAR(200) NOT NULL | |
| content | VARCHAR(1000000) NOT NULL | |
| author_id | BIGINT | null при анониме |
| is_anonymous | BOOLEAN NOT NULL | |
| is_frozen | BOOLEAN NOT NULL | |
| is_deleted | BOOLEAN NOT NULL | |
| visibility | VARCHAR(20) NOT NULL | PUBLIC/OPEN/GUEST/CLOSED |
| read_only | BOOLEAN NOT NULL | |
| likes / dislikes | INTEGER NOT NULL | |
| last_activity_at | TIMESTAMP NOT NULL | сортировка списка |

Связи: `topic_tags` (ManyToMany с `forum_tags`).

### forum_posts (`forum/model/ForumPost.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| content | VARCHAR(5000) NOT NULL | |
| author_id | BIGINT | null при анониме |
| is_anonymous | BOOLEAN NOT NULL | |
| is_deleted | BOOLEAN NOT NULL | |
| likes / dislikes | INTEGER NOT NULL | |
| topic_id | BIGINT NOT NULL | |
| parent_post_id | BIGINT | null для корневых; дерево комментариев |
| reply_to_id | BIGINT | ответ «на комментарий» (цитата) |
| is_edited | BOOLEAN NOT NULL | |
| is_op | BOOLEAN NOT NULL | стартовый пост треда |
| is_continuation | BOOLEAN NOT NULL | пост-продолжение |
| deleted_by_admin | BOOLEAN NOT NULL | заглушка «удалено администратором» |
| deleted_by_owner | BOOLEAN NOT NULL | заглушка «удалено владельцем треда» |

Индексы: `topic_id`, `parent_post_id`, `reply_to_id`.

### Таблицы реакций форума
- `post_likes` (создана в `07`), `post_dislikes` (`08`) — (post_id, user_id)
  unique — реакции на пост.
- `topic_likes`, `topic_dislikes` (`08`) — (topic_id, user_id) unique —
  реакции на тред.
- Счётчики `likes`/`dislikes` денормализованы в `forum_posts`/`forum_topics`.

### topic_bans (`07` + `10-topic-bans-display-name`)
`topic_id` + `user_id` unique — бан в конкретном треде; `banned_by`
(модератор), `display_name`, `reason`.

### forum_attachments (`forum/model/ForumAttachment.java`)
| Колонка | Тип | Описание |
|---------|-----|----------|
| post_id | BIGINT | |
| topic_id | BIGINT | |
| file_name | VARCHAR(255) NOT NULL | |
| stored_name | VARCHAR(255) unique NOT NULL | имя файла на диске |
| content_type | VARCHAR(255) NOT NULL | |
| file_size | BIGINT NOT NULL | |

Файлы лежат в `app.upload-dir` (`uploads/` по умолчанию).

### forum_topic_whitelist (`16`)
| Колонка | Описание |
|---------|----------|
| topic_id + user_id | unique; кому разрешён доступ/запись в `CLOSED` тред |

### topic_bans (`10`)
| Колонка | Описание |
|---------|----------|
| topic_id + user_id | unique; бан в конкретном треде |
| banned_by | модератор |
| display_name / reason | отображение и причина |

### notifications (`21`)
| Колонка | Описание |
|---------|----------|
| user_id | получатель |
| type | REPLY / TOPIC_COMMENT / FILM_SELECTED / RESERVATION_CANCELLED / SUBSCRIPTION |
| text | текст |
| url | ссылка перехода |
| is_read | прочитано? |

### subscriptions (`21`)
| Колонка | Описание |
|---------|----------|
| subscriber_id | кто подписан |
| target_user_id | на пользователя (или null) |
| topic_id | на тред (или null) |

Уникальны `(subscriber_id, target_user_id)` и `(subscriber_id, topic_id)`.

## 4. Правила работы с миграциями

1. Никогда не редактируйте уже применённые `changeSet` (Liquibase хранит
   контрольные суммы в `DATABASECHANGELOG`).
2. Новую миграцию добавляйте отдельным файлом `2x-<name>.xml`, подключайте
   его в `db.changelog-master.xml`.
3. Ставьте `id="<number>"` с уникальным `author`.
4. После добавления миграции локально проверьте: `docker compose up -d app`
   (Liquibase применит смены автоматически при старте).
5. Если нужно пересоздать БД с нуля: `docker compose down -v && docker compose up -d --build`
   (удалит том с данными).
