# Внутренняя шина событий (pub/sub)

Модули **не зависят друг от друга напрямую** на уровне доменов. Связь —
через **внутренние события Spring** (`ApplicationEventPublisher`).
События и их типы объявлены в `common` (`org.corpsman.common.event`),
чтобы не создавать циклических зависимостей.

## 1.  Базовые классы

`common/.../event/BaseEvent.java`:
```java
public abstract class BaseEvent {
    // Конструктор принимает occurredAt (LocalDateTime)
}
```

Класс `AsyncConfig` (`@EnableAsync`) и `CacheConfig` включены в `common`.
Обработчики событий в доменах размещаются в пакете `<module>/config/`
(например, `auth/config/AuthEventListener.java`).

## 2. Каталог событий (common)

| Событие | Описание |
|---------|----------|
| `UserRegisteredEvent` | Новый пользователь зарегистрирован |
| `UserDeletedEvent` | Пользователь удалён/деактивирован |
| `FilmAddedEvent` | Добавлен фильм |
| `FilmSelectedEvent` | Выбран фильм к просмотру |
| `FilmWatchedEvent` | Фильм просмотрен |
| `GiftReservedEvent` | Подарок зарезервирован |
| `WishlistChangedEvent` | Вишлист изменён |
| `PostCreatedEvent` | Создан пост/комментарий |
| `TopicCreatedEvent` | Создан тред форума |
| `BaseEvent` | Абстрактный предок всех событий |

Каждое событие несёт релевантные поля (например, `FilmAddedEvent` —
`filmId`, `filmTitle`; `PostCreatedEvent` — `postId`, `forumId`,
`isAnonymous`; `TopicCreatedEvent` — `topicId`, `isAnonymous`, `userId`).

## 3. Публикация

Издатель вызывает `eventPublisher.publishEvent(new XxxEvent(...))`
(или реализует `ApplicationEventPublisherAware` / внедряет
`ApplicationEventPublisher`).

Пример (`ForumService.createTopic`):
```java
eventPublisher.publishEvent(new TopicCreatedEvent(
        saved.getId(), saved.isAnonymous(), userId, LocalDateTime.now()));
```

## 4. Подписка

Подписчики — Spring-бины с методом, помеченным `@EventListener`
(для асинхронности — `@Async` + `@EventListener`).

Пример обработчика действий пользователя:
```java
@EventListener
public void onUserDeleted(UserDeletedEvent event) { ... }
```

### Существующие обработчики

| Модуль | Класс | Что обрабатывает / делает |
|--------|-------|---------------------------|
| `auth` | `AuthEventListener` | Пользовательские события, инициализация |
| `cinema` | `CinemaEventListener` | Фильмовые события (статистика, выбор, просмотр) |
| `forum` | `NotificationsEventListener` | События → уведомления/подписки |
| `wishlist` | (в `WishlistService`) | `UserDeletedEvent` → чистка подарков |

## 5. Как добавить новое событие

1. Создать класс события в `common/.../common/event/XxxEvent.java`
   (наследует `BaseEvent`, поля + конструктор).
2. В модуле-издателе: `publishEvent(new XxxEvent(...))`.
3. В модуле-подписчике: метод с `@EventListener`.
4. Если обработка должна идти асинхронно — добавить `@Async` (включено
   `@EnableAsync`; имена пулов/дефолты — `AsyncConfig`).

> События — это механизм **слабого связывания** между доменами. Домен не
> должен знать о получателях своих событий; получатель сам решает, что
> делать.
