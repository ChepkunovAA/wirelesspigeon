# Домен форума

Форум — самый объёмный домен (`forum` модуль). Здесь: треды, посты,
комментарии (дерево), анонимность, реакции, теги, белые списки, баны,
подписки и уведомления.

## 1. Понятия

- **Тред** (`ForumTopic`) — тема с заголовком и содержанием.
  - `visibility`: PUBLIC / OPEN / GUEST / CLOSED (см. `security.md`).
  - `frozen` — заморожен (нельзя писать никому).
  - `readOnly` — только чтение, писать может только владелец треда.
  - `deleted` — удалён.
  - `likes`/`dislikes` — суммарные реакции на сам тред.
- **Стартовый пост (OP)** — первое сообщение треда, `is_op = true`,
  содержит то же `content`, что и тред.
- **Пост-продолжение** (`is_continuation = true`) — дополнительный
  «большой» пост, добавленный владельцем треда (в стиле стартового поста).
- **Комментарий** — пост с `parent_post_id != null` или корневой
  комментарий (`parent_post_id = null` и `is_op = false`). Дерево через
  `parent_post_id`, цитата-ответ через `reply_to_id`.
- **Реакции** — лайки/дизлайки поста или треда (`importance`):
  уникальные записи `post_likes`, `post_dislikes`, `topic_likes`,
  `topic_dislikes`, а счётчики денормализованы.
- **Теги** (`ForumTag`) — ManyToMany с тредом.
- **Белый список** (`forum_topic_whitelist`) — пользователи с доступом к
  `CLOSED` треду. В меню выбора (dropdown с чекбоксами) в форме создания и в
  редакторе треда есть клиентское поле «Поиск по нику» (Alpine, быстрая
  фильтрация по подстроке).
- **Бан в треде** (`TopicBan`) — запрет комментариев в конкретном треде.
- **Подписки** (`Subscription`) — на пользователя или на тред.
- **Уведомления** (`Notification`) — события для пользователя.

## 2. Основные классы

| Слой | Классы |
|------|--------|
| Контроллеры | `ForumController`, `ForumModerationController`, `SubscriptionController` |
| Сервисы | `ForumService`, `PostService`, `NotificationService`, `SubscriptionService`, `RatingStatsService`, `AttachmentService`, `FileStorageService` |
| Репозитории | `ForumTopicRepository`, `ForumPostRepository`, `ForumTagRepository`, `PostLikeRepository`, `PostDislikeRepository`, `TopicLikeRepository`, `TopicDislikeRepository`, `TopicWhitelistRepository`, `TopicBanRepository`, `ForumAttachmentRepository`, `NotificationRepository`, `SubscriptionRepository` |

## 3. API сервисов

### ForumService (основной)
```java
PublicPostDto getLatestVisiblePost(userId)                 // «Последний пост»
boolean isPublicTopic(Long topicId)
Page<TopicSummaryDto> getTopics(pageable, search, tags, userId)
TopicDetailDto getTopic(Long topicId, Long userId, pageable)
ForumTopic createTopic(TopicCreateRequest, Long userId, files)
void editTopic(Long topicId, String newContent, Long userId)
void freezeTopic / unfreezeTopic(...)
void deleteTopic(...)
void banUser / unbanUser(topicId, targetId, moderatorId, isAdmin, ...)
LikeResponse likeTopic/unlikeTopic/dislikeTopic/undislikeTopic(...)
List<...> getPopularTags() / getAllTags()
PublicPostDto getLatestVisiblePost(userId)                // «Последний пост»
PublicPostDto getBestVisiblePost(userId)                  // «Лучший пост»
String getWhitelistCsv(topicId); getWhitelistNames(topicId)
void updateWhitelist(topicId, csv, userId, isAdmin)
ForumPost addContinuation(topicId, content, userId)   // анонимность наследуется от треда
String getTopicVisibility(topicId)
```

### PostService (посты и реакции)
```java
ForumPost createPost(ForumPostRequest, Long userId, files)
void editPost(postId, newContent, userId)
void deletePost(postId, userId, isAdmin)      // каскадно по поддереву
ForumPost getPost(postId)
LikeResponse likePost/unlikePost/dislikePost/undislikePost(...)
boolean isAdmin(userId)
```

### NotificationService
```java
void notify(userId, type, text, url)
void notifyTopicSubscribers(topicId, actorId, type, text, url)
void notifyUserSubscribers(targetUserId, actorId, type, text, url)
List<Notification> listFor(userId)
long unreadCount(userId)
void markAllRead(userId)
void markRead(id, userId)      // прочитать конкретное уведомление
```

### SubscriptionService
```java
boolean toggleUserSubscription(subscriberId, targetUserId)
boolean toggleTopicSubscription(subscriberId, topicId)
boolean isSubscribedToTopic(userId, topicId)
boolean isSubscribedToUser(userId, targetId)
```

## 4. Дерево комментариев

- Посты хранят `parent_post_id` (родитель) и `reply_to_id` (кому адресован
  ответ-цитата).
- `ForumService.getTopic` собирает полное дерево:
  - `allPosts` сортируются по `created_at`;
  - карта `byParent` группирует по `parent_post_id`;
  - корневые посты (`parent_post_id = null AND !is_op`) пагинируются;
  - рекурсивный сбор поддерева `collectAllPostIds`/`toPostDto(depth)`.

### Лента (timeline) на странице треда
`TopicDetailDto.timeline` = дети стартового поста + корневые посты,
отсортированные по `created_at`.

### Два режима отображения (topic.html)
1. **Без продолжений** — единая хронологическая лента всех
   комментариев первого уровня.
2. **С продолжениями** — сжатое поддерево стартового поста (свёрнутое
   «Раскрыть N комментариев»), затем посты-продолжения в стиле OP,
   затем обычные корневые комментарии.
   - Правило (багфикс 6.1): **новые комментарии отображаются ниже
     поста-продолжения** (обычные комментарии рендерятся после секции
     продолжений).

### Удаление поста (каскад по поддереву)
`PostService.markDeletedDeep(..., byAdmin, byOwner)` помечает пост и всё
его поддерево как удалённые:
- администратор → `deleted_by_admin = true`;
- владелец треда → `deleted_by_owner = true`;
- автор (в своём треде) → также `deleted_by_owner`.
Заглушки в `post.html`: «Удалено администратором», «Удалено владельцем
треда», иначе «[Удалено]».

Отображение заглушки (релиз 2.2.1): комментарий, удалённый владельцем
треда или администратором, всегда показывается с соответствующей
заглушкой — даже если у него нет ответов/потомков. Комментарий, удалённый
обычным автором, показывается (с заглушкой «[Удалено]») только при наличии
выживших потомков; полностью удалённое поддерево скрывается. Условия
рендера в `topic.html`/`post.html` учитывают `deletedByAdmin`/`deletedByOwner`.

Счётчики комментариев (релиз 2.2.1) учитывают только неудалённые посты:
- `ForumService.subtreeSize` — «Раскрыть N»;
- `ForumPostRepository.countByTopicIdIn` — «N комментариев» на карточках;
- `TopicDetailDto.nonDeletedCount` — заголовок «Комментарии (N)».

## 5. Анонимность

- Тред и OP: `is_anonymous`; при `true` автор скрыт («Аноним»).
- MASTER видит реального автора (поле `realAuthorName` в DTO, только для
  администратора).
- Пользователь может настроить анонимность по умолчанию в `/settings`.

## 6. Реакции (лайки/дизлайки)

- Уникальны на (user, post) / (user, topic) → toggle-логика
  (`likePost/unlikePost`, `dislikePost/undislikePost`).
- Счётчики денормализованы в `forum_posts.likes/dislikes` и
  `forum_topics.likes/dislikes`.

## 7. Главная: «Лучший пост» и «Последний пост»

На главной всегда выводятся два блока в одинаковой стилистике
(фрагмент `fragments/home-post.html`):
- **Последний пост** (`getLatestVisiblePost(userId)`) — последний пост
  (OP или продолжение) по `created_at DESC`.
- **Лучший пост** (`getBestVisiblePost(userId)`) — пост с максимальным
  рейтингом `likes - dislikes`.

Оба определяются **персонально для каждого пользователя** — по его роли
и настройкам видимости (`getByUserId` из `SettingsService`):
- **аноним** — видит только треды `PUBLIC`;
- роль `GUEST` — не видит треды `OPEN`;
- настройка `showPublicPosts=false` — скрывает треды `PUBLIC`;
- настройка `hideGuestThreads` — скрывает треды `GUEST`;
- треды `CLOSED` — только владелец или участник белого списка (в т.ч.
  `MASTER`; админ без членства в белом списке их не видит).

Под «постом» понимается **стартовый пост (OP) или пост-продолжение**
(`is_op = true OR is_continuation = true`) — бизнес-логически это одно
и то же. НЕ обычные комментарии.

Запросы: `ForumPostRepository.findLatestByTopicIds(ids, Pageable)`,
`findBestByTopicIds(ids, Pageable)`; видимые треды — `ForumService.visibleTopicIds`.

## 8. Модерация треда (ForumModerationController)

База `/forum/topic/{topicId}/moderation`:
- `POST /freeze`, `/unfreeze` — заморозка (владелец/мастер);
- `POST /delete` — удаление треда;
- `POST /ban`, `/unban` — бан пользователя в треде.

## 9. Подписки и уведомления

Источники уведомлений (`NotificationsEventListener` в `forum/config`):
- новый комментарий в треде, на который подписан пользователь;
- действия подписанного пользователя;
- события из других доменов (выбор фильма, отмена резерва и т.д.).

Типы (`Notification.type`): `REPLY`, `TOPIC_COMMENT`, `FILM_SELECTED`,
`RESERVATION_CANCELLED`, `SUBSCRIPTION`.

Страницы: `GET /notifications`, дейстmvie `POST /notifications/{id}/read`
(помечает одно прочитанным и редиректит на `url`), `POST /notifications/read-all`.

## 10. Вложения

- `ForumAttachment` + файл в `app.upload-dir` (по умолчанию `uploads/`).
- `GET /forum/attachment/{id}` — требует авторизации (для авторизованных).
- `GET /forum/public/attachment/{id}` — публичный доступ (для анонимов,
  используется на главной для публичных постов).
- Поддержка загрузки до 100MB, image-превью в карточках постов.

## 11. Ключевые бизнес-правила (сводка)

1. Гость создаёт только GUEST (открытые) и CLOSED треды.
2. Аноним видит только PUBLIC треды; писать/реагировать не может.
3. CLOSED — доступ/запись только владельцу и белому списку (мастер тоже
   только по белому списку).
4. Замороженный тред: никто не пишет.
5. readOnly: пишет только владелец треда.
6. Владелец треда может удалять любые комментарии в своём треде
   (заглушка «удалено владельцем треда»).
7. Посты недели — только OP/продолжения публичных тредов, без дубля
   публичного поста главной.
8. Комментарии с продолжениями: новые комментарии ниже поста-продолжения.
