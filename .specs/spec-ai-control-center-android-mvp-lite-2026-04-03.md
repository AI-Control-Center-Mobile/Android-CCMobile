# Сжатый standalone-план для MVP Android-приложения AI Control Center без Google OAuth

## Summary

Собрать **Android MVP** нативного приложения, которое решает 5 базовых задач:

- хранит все AI-разговоры в одном месте
- позволяет выбрать модель под задачу
- позволяет сравнить 2 ответа на один prompt
- позволяет продолжить тот же разговор другой моделью без потери контекста
- показывает cost и latency для каждого ответа

Целевой MVP должен быть реалистичен для короткого срока и solo-разработки, поэтому продукт строится вокруг **одного Android-клиента**, **одного backend relay** и **одного provider path через OpenRouter**, который даёт доступ к нескольким моделям без отдельной интеграции каждого вендора.

Опорный пользовательский сценарий:
1. Пользователь открывает приложение.
2. Попадает в локальную сессию без обязательной регистрации.
3. Создаёт или выбирает project.
4. Запускает thread и отправляет prompt.
5. Получает ответ с model / latency / estimated cost.
6. Запускает compare для 2 моделей на том же prompt.
7. Выбирает лучший ответ.
8. Продолжает этот же thread другой моделью.

## Key Changes

### Продуктовый scope MVP

Обязательные функции:
- **Локальная сессия без auth**.
- Список проектов.
- Создание project с `title`.
- Список thread внутри project.
- Создание нового thread.
- Single-model chat.
- Compare для **ровно 2 моделей**.
- Switch model внутри существующего thread.
- Отображение `model name`, `provider`, `latency`, `estimated cost` у каждого AI-ответа.
- Хранение истории разговоров в рамках project context.
- Базовый экран настроек приложения.

Явно вне MVP:
- Google OAuth
- любой другой auth/sign-in
- iOS-клиент
- direct integrations с OpenAI / DeepSeek
- provider connection management для нескольких провайдеров
- attachments
- routing presets
- отдельный usage analytics screen
- spend alerts / hard caps
- offline mode как отдельный продуктовый capability
- moderation / report flow
- privacy mode / fallback policy UI
- push notifications
- tablet-specific UX
- team/shared workspaces
- voice / image / video features

### UI / UX

Навигация:
- Bottom navigation из **3 root sections**:
  - `Projects`
  - `New Chat`
  - `Settings`

Поведение root sections:
- `Projects`:
  - список проектов
  - создание нового проекта
  - переход в project detail
- `New Chat`:
  - быстрый вход в последний project или выбор project
  - создание нового thread
- `Settings`:
  - информация о приложении
  - очистка локальных данных
  - базовые app preferences

Экран `Project Detail`:
- заголовок project
- список thread с last activity
- CTA `New Thread`

Экран `Thread`:
- header с названием project и активной моделью
- список сообщений
- у каждого assistant-message:
  - provider/model chip
  - latency
  - estimated cost
- composer внизу
- action `Compare`
- action `Switch model for next turn`

Экран / flow `Compare`:
- пользователь выбирает 2 модели
- один prompt отправляется в обе модели
- на телефоне compare показывается либо:
  - через **horizontal pager A/B**, либо
  - через **stacked cards**
- обязательные элементы:
  - оба ответа
  - подписи моделей
  - latency и cost для каждой стороны
  - action `Choose winner`
  - action `Continue with this model`

UX-правила:
- никаких скрытых критических действий только через gesture
- все primary actions доступны одной рукой
- touch targets не меньше 48dp
- состояние загрузки, ошибки и пустого экрана есть на каждом экране
- thread не должен терять читаемость при смене модели
- compare не должен требовать перехода в отдельный раздел приложения

### Архитектура

Общая схема:
- Android app
- Backend API
- OpenRouter relay
- Database

Распределение ответственности:
- Android:
  - UI
  - local session state
  - rendering history
  - compare presentation
  - локальное хранение идентификатора устройства/сессии
- Backend:
  - выдача и поддержка guest-session
  - доступ к model catalog
  - orchestration chat/comparison requests
  - persistence projects/threads/messages
  - unified cost/latency metadata
- OpenRouter:
  - доступ к нескольким моделям через один provider path

### Android implementation

Стек:
- Kotlin
- Jetpack Compose
- Coroutines + Flow
- Navigation Compose
- ViewModel
- Secure local storage для session token / guest identifier
- Room для локального хранения проектов, thread и сообщений как основного MVP-хранилища или cache-first слоя
- DataStore для лёгких app preferences

Рекомендуемая модульность:
- `core`
- `feature-projects`
- `feature-chat`
- `feature-compare`
- `feature-settings`
- `data-network`
- `data-storage`

### Backend implementation

Стек:
- Один простой backend на TypeScript
- Fastify или NestJS
- PostgreSQL
- Без Redis, queues и отдельного usage-service в MVP, если не требуется для минимального масштаба

Минимальные API endpoints:
- `POST /session/guest`
- `GET /me`
- `GET /projects`
- `POST /projects`
- `GET /projects/:id/threads`
- `POST /projects/:id/threads`
- `GET /threads/:id`
- `POST /threads/:id/messages`
- `POST /threads/:id/compare`
- `GET /models`
- `GET /settings`
- `PATCH /settings`

Streaming:
- Если хватает времени, использовать один streaming transport для chat response.
- Если срок совсем жёсткий, допустим non-streaming ответ в первой сборке при сохранении одинакового API contract.

### Данные и типы

Минимальные сущности:
- `Session`
  - `id`
  - `deviceId`
  - `createdAt`
  - `updatedAt`
- `Project`
  - `id`
  - `sessionId`
  - `title`
  - `createdAt`
  - `updatedAt`
- `Thread`
  - `id`
  - `projectId`
  - `title`
  - `createdAt`
  - `updatedAt`
- `Message`
  - `id`
  - `threadId`
  - `role`
  - `content`
  - `provider`
  - `model`
  - `latencyMs`
  - `estimatedCost`
  - `createdAt`
- `ModelCatalogEntry`
  - `id`
  - `provider`
  - `model`
  - `label`
  - `supportsChat`
  - `supportsCompare`

Критические правила данных:
- каждый assistant-message обязан хранить `provider` и `model`
- compare не создаёт отдельную сложную доменную подсистему; достаточно сохранить 2 assistant-message, связанные с одним user prompt
- thread history всегда остаётся внутри одного project
- switch model влияет только на следующий assistant turn, а не меняет историю задним числом
- данные принадлежат guest-session и могут быть полностью очищены пользователем из Settings

## Test Plan

### Ключевые acceptance scenarios

Session:
- пользователь открывает приложение и автоматически получает guest-session
- после relaunch сессия сохраняется
- очистка локальных данных сбрасывает session и историю

Projects:
- пользователь создаёт project
- пользователь видит список своих projects
- пользователь открывает project и видит его threads

Chat:
- пользователь создаёт thread
- пользователь отправляет prompt
- backend возвращает ответ от выбранной модели
- в UI у ответа видны model / provider / latency / estimated cost

Compare:
- пользователь выбирает 2 модели
- один prompt отправляется в обе модели
- пользователь видит 2 ответа с метаданными
- пользователь может выбрать winner
- compare не ломает основной thread flow

Switch model:
- пользователь меняет модель для следующего turn
- следующий ответ приходит от новой модели
- timeline явно показывает смену модели

Надёжность:
- ошибка сети показывает recoverable UI state
- повторная отправка не должна создавать очевидные дубли по вине клиента
- история thread после relaunch остаётся консистентной

### Нефункциональная проверка

- touch targets >= 48dp
- one-handed usage для основных CTA
- читаемость длинных ответов
- стабильный scroll в thread
- crash-free smoke test на Android emulator / device
- session token или guest identifier не хранится в plaintext

## Assumptions

- MVP целится в **короткий срок и минимальный риск**, поэтому auth полностью выносится за пределы первой версии.
- Базовая ценность продукта доказывается уже через **один provider path + несколько моделей**, поэтому direct provider integrations не нужны в MVP.
- Требование “все разговоры в одном месте” в MVP означает единое хранилище внутри приложения и backend guest-session, а не полноценный account sync между устройствами.
- Видимость cost и latency в MVP достаточно реализовать **на уровне сообщения**, без агрегированной аналитики.
- Project context обязателен, потому что без него теряется одно из базовых требований продукта.
- Если срок ещё сильнее сжимается, первым упрощением после этого плана должен быть отказ от streaming, а не отказ от compare или switch model.
- Первый post-MVP приоритет:
  - Google OAuth
  - account-based cloud sync
  - direct OpenAI integration
  - attachments
  - usage screen
  - второй provider path
  - iOS client
