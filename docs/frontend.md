# Фронтенд

Фронтенд — **серверный рендеринг** на стороне Spring MVC. JS/CSS
подключается через CDN, интерактивность — **Alpine.js** прямо в HTML.

## 1. Стек

| Технология | Назначение |
|------------|-----------|
| Thymeleaf 3.1 | Шаблоны, выражения `th:*` |
| Bootstrap 5.3 + Bootstrap Icons | Стили, сетка, иконки, компоненты |
| Alpine.js 3.13 | Модалки, раскрытие, аякс-реакции (`x-data`, `x-show`, `x-on`, `@click`) |
| Встроенный CSS | `webapp/src/main/resources/static/css/app.css` |

## 2. Расположение

- Шаблоны: `webapp/src/main/resources/templates/`
  - `home.html`, `profile.html`, `profile-user.html`, `settings.html`, `keys.html`
  - `auth/login.html`, `auth/register.html`
  - `forum/` (list, create, topic, post-фрагмент, attachments-фрагмент)
  - `notifications.html`
  - `cinema/`, `gifts/`, `users/`, `admin/`
  - `fragments/nav.html` — общая навигация
- Статика: `webapp/src/main/resources/static/` (`css/`, `assets/`)
- `thymeleaf.cache: false` в dev — шаблоны перечитываются без рестарта.

## 3. Соглашения

- Все URL в шаблонах **относительные** — `@{...}` / `th:href` (это
  критично для работы через HTTPS-прокси без mixed-content, см.
  `https-migration.md`).
- Формы, изменяющие данные, — `method="post"` с `_csrf` (Thymeleaf
  добавляет автоматически в `th:action`-формах):
  ```html
  <form th:action="@{/forum/topic/{id}/comment(id=${topic.id})}" method="post">
      <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
      ...
  ```
- Русская локаль UI; бейджи/чипы типов тредов используют **отображаемые**
  лейблы (OPEN и GUEST → «Открытый», GUEST дополнительно — «доступен гостям»,
  см. `security.md`).
- `fragments/nav.html` — единая навигация, подставляется во всех
  страницах через `th:replace`.

## 4. Alpine.js — примеры применения

**Сворачивание поддерева комментариев** (topic.html):
```html
<div x-data="{open: window.__subtreeOpen(topicId, postId)}"
     data-topic-id data-post-id>
    <button @click="open = !open; window.__setSubtree(...)">
        <template x-if="!open"><span>Раскрыть N комментариев</span></template>
        <template x-if="open"><span>Свернуть</span></template>
    </button>
    <div x-show="open" x-cloak> ... поддерево ... </div>
</div>
```
Состояние раскрытия сохраняется через `window.__subtreeOpen/__setSubtree`
(в `sessionStorage`/`localStorage`), чтобы не сбрасываться при навигации.

**Реакции (лайки/дизлайки) без перезагрузки** (post/фрагмент):
```html
<span x-data="react()" data-liked data-likes data-disliked data-dislikes
      data-own data-like-url data-unlike-url data-dislike-url data-undislike-url>
    <button :class="liked ? 'btn-primary' : 'btn-outline-primary'"
            :disabled="own" @click="toggleLike()">
        <i class="bi" :class="liked ? 'bi-hand-thumbs-up-fill' : 'bi-hand-thumbs-up'"></i>
        <span x-text="likes">0</span>
    </button>
    ...
</span>
```

**Показ/скрытие элементов** — `sec:authorize` для серверной проверки ролей
и `x-show`/`x-if` для клиентской.

## 5. Структура страницы треда (forum/topic.html)

1. Карточка **стартового поста (OP)**: заголовок, автор, дата, содержание,
   вложения, лайки/дизлайки треда.
2. Комментарии:
   - без продолжений — хронологическая лента;
   - с продолжениями — свёрнутое поддерево OP («Раскрыть N комментариев»)
     + посты-продолжения + обычные комментарии **ниже** (багфикс 6.1).
3. Пагинация `/forum/topic/{id}?page=N`.
4. Форма «Оставить комментарий» (multipart — поддержка файлов).

## 6. Лейблы типов тредов (чипы/бейджи/options)

Используются единые отображаемые названия:
- PUBLIC → «Публичный»
- OPEN → «Открытый»
- GUEST → «Открытый» + «доступен гостям»
- CLOSED → «Закрытый»

Эти лейблы используются в `list.html` (чипы/бейджи), `topic.html` (бейджи),
`create.html` (options формы + чекбокс «доступен гостям»), `settings.html`
(options настройки), и в списке «Скрывать открытые треды, доступные гостям»
(гостевые = «Открытый» + «доступен гостям»). Вкладка «Открытые» в списке
форума фильтрует OPEN и GUEST вместе.

## 6.2. Отображение времени

Все времена — `LocalDateTime` в локальном времени JVM (**системное время
сервера**), рендерятся Thymeleaf `#temporals.format(...)` без сдвига.
Таймзона делается явной через `app.time-zone` в `webapp/.../config/TimeZoneConfig.java`
(пустое значение — системная таймзона хоста). Форматы: «дата+время»
`dd.MM.yyyy HH:mm` (посты, комментарии, токены, уведомления, участники),
«только дата» `dd.MM.yyyy` (карточка поста на главной, дата просмотра в
кино, дата регистрации).

## 7. Проверка адаптивности

Используется Bootstrap-сетка (`container`, `row g-4`, `col-md-6` и т.д.).
При изменении разметки проверяйте страницу на узком экране (mobile-first).

## 8. Полезные команды

```bash
# Проверить, что шаблон отдаётся (после правок без рестарта, т.к. cache:false)
curl -s -b jar http://localhost:8080/<path> | grep -i "ошибка\|Exception"
```
