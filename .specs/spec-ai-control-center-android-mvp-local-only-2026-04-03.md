# Спецификация для сжатого Android MVP AI Control Center в local-only архитектуре

## Summary

Собрать **Android-only local-first MVP** нативного приложения, которое решает 5 базовых задач:

- хранит все AI-разговоры локально в одном месте
- позволяет выбрать модель под задачу
- позволяет сравнить 2 ответа на один prompt
- позволяет продолжить тот же разговор другой моделью без потери контекста
- показывает cost и latency для каждого ответа

Целевой MVP должен быть реалистичен для короткого срока и solo-разработки, поэтому продукт строится вокруг:

- одного Android-клиента
- прямой интеграции с OpenRouter API
- локального хранения всех projects, threads и messages на устройстве

Опорный пользовательский сценарий:
1. Пользователь открывает приложение.
2. Один раз вставляет свой OpenRouter API key.
3. Создаёт или выбирает project.
4. Запускает thread и отправляет prompt.
5. Получает ответ с model / latency / estimated cost.
6. Запускает compare для 2 моделей на том же prompt.
7. Выбирает лучший ответ.
8. Продолжает этот же thread другой моделью.

## Key Changes

### Продуктовый scope MVP

Обязательные функции:
- Локальная работа **без auth** и без аккаунта.
- On-device хранение всех projects, threads и сообщений.
- Экран первичной настройки с вводом OpenRouter API key.
- Список проектов.
- Создание project с `title`.
- Список thread внутри project.
- Создание нового thread.
- Single-model chat.
- Compare для **ровно 2 моделей**.
- Switch model внутри существующего thread.
- Отображение `model name`, `provider`, `latency`, `estimated cost` у каждого AI-ответа.
- Базовый экран настроек приложения.
- Полная очистка локальных данных и API key из приложения.

Явно вне MVP:
- любой auth/sign-in
- backend
- cloud sync
- iOS-клиент
- direct integrations с OpenAI / DeepSeek вне OpenRouter
- provider connection management для нескольких провайдеров
- attachments
- routing presets
- отдельный usage analytics screen
- spend alerts / hard caps
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

Первый запуск:
- Если API key ещё не задан:
  - открыть `Setup API Key`
  - объяснить, что приложение работает локально и использует OpenRouter key пользователя
  - не пускать в chat flow, пока ключ не сохранён
- Если API key сохранён:
  - открывать `Projects`

Поведение root sections:
- `Projects`:
  - список проектов
  - создание нового проекта
  - переход в project detail
- `New Chat`:
  - быстрый вход в последний project или выбор project
  - создание нового thread
- `Settings`:
  - управление OpenRouter API key
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
- compare на телефоне показывается:
  - либо через **horizontal pager A/B**
  - либо через **stacked cards**
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
- ошибки API key и сетевые ошибки должны объясняться простым языком

### Архитектура

Общая схема:
- Android app
- OpenRouter API
- Local database
- Secure local storage

Распределение ответственности:
- Android:
  - UI
  - local state
  - хранение projects / threads / messages
  - хранение API key в secure storage
  - orchestration chat / compare / switch model flows
  - расчёт и отображение local metadata для latency / estimated cost
- OpenRouter:
  - model catalog
  - ответы моделей
  - usage metadata, если доступна

### Android implementation

Стек:
- Kotlin
- Jetpack Compose
- Coroutines + Flow
- Navigation Compose
- ViewModel
- Room как основное локальное хранилище
- DataStore для app preferences
- Android Keystore + EncryptedSharedPreferences или аналог для безопасного хранения OpenRouter API key
- Retrofit / Ktor client для OpenRouter API

Рекомендуемая модульность:
- `core`
- `feature-setup`
- `feature-projects`
- `feature-chat`
- `feature-compare`
- `feature-settings`
- `data-network`
- `data-storage`

### OpenRouter integration

Минимальные клиентские операции:
- сохранить API key
- загрузить список доступных моделей
- отправить single chat request
- отправить compare как 2 последовательных или параллельных запроса
- получить usage metadata, если OpenRouter её возвращает

Если usage metadata недоступна:
- estimated cost считается локально по известной pricing table
- UI явно помечает значение как `estimated`

Streaming:
- Если хватает времени, использовать streaming response.
- Если срок совсем жёсткий, допустим non-streaming ответ в первой сборке.

### Данные и типы

Минимальные сущности:
- `Project`
  - `id`
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

Локальные правила данных:
- каждый assistant-message обязан хранить `provider` и `model`
- compare не создаёт отдельную сложную доменную подсистему; достаточно сохранить 2 assistant-message, связанные с одним user prompt
- thread history всегда остаётся внутри одного project
- switch model влияет только на следующий assistant turn, а не меняет историю задним числом
- удаление всех данных из Settings удаляет:
  - API key
  - projects
  - threads
  - messages
  - cached model catalog

## Test Plan

### Ключевые acceptance scenarios

Setup:
- пользователь открывает приложение без API key и попадает на экран настройки
- пользователь сохраняет валидный OpenRouter API key
- после relaunch ключ остаётся доступным приложению
- пользователь может заменить или удалить ключ

Projects:
- пользователь создаёт project
- пользователь видит список своих projects
- пользователь открывает project и видит его threads

Chat:
- пользователь создаёт thread
- пользователь отправляет prompt
- приложение получает ответ от выбранной модели через OpenRouter
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
- невалидный API key показывает понятную ошибку и recovery path
- повторная отправка не должна создавать очевидные дубли по вине клиента
- история thread после relaunch остаётся консистентной

### Нефункциональная проверка

- touch targets >= 48dp
- one-handed usage для основных CTA
- читаемость длинных ответов
- стабильный scroll в thread
- crash-free smoke test на Android emulator / device
- OpenRouter API key не хранится в plaintext
- очистка локальных данных действительно удаляет всё чувствительное

## Assumptions

- MVP целится в **короткий срок и минимальный риск**, поэтому backend полностью убирается.
- Базовая ценность продукта доказывается уже через **один provider path + несколько моделей**, поэтому OpenRouter достаточно.
- Требование “все разговоры в одном месте” в MVP означает локальное on-device хранилище, а не синхронизацию между устройствами.
- Видимость cost и latency в MVP достаточно реализовать **на уровне сообщения**, без агрегированной аналитики.
- Project context обязателен, потому что без него теряется одно из базовых требований продукта.
- Если срок ещё сильнее сжимается, первым упрощением после этого плана должен быть отказ от streaming, а не отказ от compare или switch model.
- Первый post-MVP приоритет:
  - backend и cloud sync
  - auth
  - attachments
  - usage screen
  - второй provider path
  - iOS client
