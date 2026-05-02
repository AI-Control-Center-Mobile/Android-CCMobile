# Индивидуальное ТЗ на двоих для local-only Android MVP AI Control Center

## Summary

Цель: разделить разработку **local-only Android MVP** между двумя Android-разработчиками так, чтобы:
- пересечения по коду были минимальными,
- интеграция в конце была простой,
- трудозатраты были примерно равными,
- итоговый продукт полностью покрывал базовый value loop:
  - локальное хранение AI-разговоров,
  - выбор модели,
  - compare 2 моделей,
  - switch model внутри thread,
  - показ cost и latency.

Выбранный режим разделения: **по подсистемам**.  
Интеграционный owner: **второй разработчик**.

Распределение ownership:
- **Разработчик 1 (ты)**: локальные данные, setup/settings, projects, thread list, базовый UI-kit.
- **Разработчик 2**: app shell, сетевой слой OpenRouter, thread screen, chat flow, compare flow, switch model, финальная интеграция.

## Общие контракты и правила

### Общая модульная структура

Создать и придерживаться следующей структуры:
- `app`
- `core-model`
- `core-ui`
- `data-storage`
- `data-network`
- `feature-setup`
- `feature-projects`
- `feature-chat`
- `feature-compare`
- `feature-settings`

### Ownership модулей

**Разработчик 1**
- `core-ui`
- `data-storage`
- `feature-setup`
- `feature-projects`
- `feature-settings`

**Разработчик 2**
- `app`
- `core-model`
- `data-network`
- `feature-chat`
- `feature-compare`

### Жёсткие правила пересечения

- Только разработчик 2 меняет:
  - `core-model`
  - navigation graph
  - общие route names
  - app-level DI wiring
- Только разработчик 1 меняет:
  - Room schema
  - DAO / local repositories
  - encrypted API key storage
  - DataStore preferences
- Разработчик 2 не лезет в реализацию `data-storage`, кроме подключения через интерфейсы.
- Разработчик 1 не лезет в реализацию `data-network`, `feature-chat`, `feature-compare`.
- Любое изменение общего контракта после freeze возможно только через явное согласование.

### Freeze-контракты, которые нужно зафиксировать в самом начале

Разработчик 2 как integration owner первым фиксирует и больше без необходимости не меняет:

Сущности:
- `Project`
- `Thread`
- `Message`
- `ModelCatalogEntry`
- `ChatRequestUiModel`
- `CompareRequestUiModel`

Repository interfaces:
- `ProjectsRepository`
- `ThreadsRepository`
- `SettingsRepository`
- `ModelsRepository`
- `ChatRepository`
- `CompareRepository`

Ключевые интерфейсы:
- `ProjectsRepository`
  - `observeProjects()`
  - `createProject(title)`
  - `getProject(projectId)`
- `ThreadsRepository`
  - `observeThreads(projectId)`
  - `createThread(projectId, title?)`
  - `observeMessages(threadId)`
  - `insertUserMessage(threadId, content, targetModel)`
  - `insertAssistantMessage(...)`
  - `updateThreadMetadata(...)`
- `SettingsRepository`
  - `getApiKey()`
  - `saveApiKey(key)`
  - `clearApiKey()`
  - `clearAllLocalData()`
- `ModelsRepository`
  - `getCachedModels()`
  - `refreshModels(apiKey)`
- `ChatRepository`
  - `sendMessage(threadId, modelId, prompt)`
- `CompareRepository`
  - `compare(threadId, modelA, modelB, prompt)`

Навигационные route:
- `setup`
- `projects`
- `project/{projectId}`
- `thread/{threadId}`
- `settings`
- `compare/{threadId}` или modal/pane route по финальному решению integrator-owner

## ТЗ для Разработчика 1

### Зона ответственности

Ты отвечаешь за локальную платформенную и persistence-часть приложения:
- безопасное хранение OpenRouter API key,
- локальное хранилище projects / threads / messages,
- setup/settings,
- projects flow,
- thread list flow,
- общий базовый UI-kit и состояния.

### Конкретные задачи

#### 1. `core-ui`
Сделать базовый reusable UI слой:
- app theme
- typography
- spacing tokens
- common components:
  - primary button
  - secondary button
  - text field
  - screen scaffold
  - empty state
  - error state
  - loading state
  - chip для metadata
  - confirm dialog
- единые состояния:
  - loading
  - empty
  - recoverable error

Acceptance:
- все компоненты независимы от feature-логики
- feature-модули могут использовать `core-ui` без циклических зависимостей

#### 2. `data-storage`
Сделать локальную data layer:
- Room database
- entities / mappers по frozen `core-model`
- DAO для:
  - projects
  - threads
  - messages
  - cached models, если agreed
- repository implementations:
  - `ProjectsRepository`
  - `ThreadsRepository`
  - часть `SettingsRepository`, относящуюся к local persistence
- хранение timestamps и last activity
- очистка всех локальных данных

Acceptance:
- локальные данные переживают relaunch
- thread history консистентна
- удаление данных очищает БД полностью

#### 3. `feature-setup`
Экран первого запуска:
- ввод OpenRouter API key
- валидация непустого значения
- save key
- переход в основной flow только после успешного сохранения
- экран повторного ввода при отсутствии key

Acceptance:
- пользователь без key всегда попадает в setup
- после сохранения key setup больше не блокирует вход
- удаление key в settings возвращает setup-flow на следующем входе

#### 4. `feature-projects`
Сделать projects subsystem:
- список projects
- создание нового project
- project detail screen
- список thread внутри project
- CTA `New Thread`
- recent activity rendering
- пустые состояния

Acceptance:
- можно создать project и сразу открыть его
- внутри project виден список thread
- можно стартовать новый thread из project detail

#### 5. `feature-settings`
Сделать settings subsystem:
- просмотр статуса API key
- update / replace key
- clear key
- clear all local data
- app info section

Acceptance:
- clear key удаляет только key
- clear all local data удаляет key + projects + threads + messages
- destructive actions подтверждаются через dialog

### Что ты не делаешь

- OpenRouter API integration
- model catalog loading из сети
- thread messaging logic
- compare orchestration
- switch model logic
- app-level navigation wiring
- final integration

### Результат твоего блока

К моменту handoff у тебя должны быть готовы:
- полностью рабочие `feature-setup`, `feature-projects`, `feature-settings`
- рабочий `data-storage`
- общий `core-ui`
- demo flow с локальными тестовыми данными без сети

## ТЗ для Разработчика 2

### Зона ответственности

Второй разработчик отвечает за:
- общие контракты и их freeze,
- app shell,
- OpenRouter integration,
- thread screen,
- single chat flow,
- compare flow,
- switch model flow,
- финальную интеграцию всех модулей.

### Конкретные задачи

#### 1. `core-model`
Зафиксировать и держать стабильными:
- domain models
- UI models
- repository interfaces
- route ids
- error taxonomy

Acceptance:
- после initial freeze модели и интерфейсы не меняются без критической причины
- developer 1 может независимо делать storage against these contracts

#### 2. `app`
Сделать app shell:
- root activity
- app navigation graph
- стартовая логика:
  - если API key есть → `projects`
  - если нет → `setup`
- bottom navigation
- app-level ViewModel / state restoration если нужен
- final DI wiring между `data-storage`, `data-network` и feature-модулями

Acceptance:
- приложение собирается и проходит полный navigation flow end-to-end
- setup / projects / thread / settings подключены в единый shell

#### 3. `data-network`
Сделать сетевой слой OpenRouter:
- API client
- request/response models
- error mapping
- model catalog loading
- single chat request
- compare как 2 запроса
- parsing latency / usage metadata, если доступна
- fallback на local estimated cost, если usage metadata неполная

Acceptance:
- можно получить список моделей
- можно отправить single message
- compare возвращает 2 независимых результата
- ошибки API key / network / provider response mapped to UI-safe errors

#### 4. `feature-chat`
Сделать thread/chat subsystem:
- thread screen
- message list
- composer
- active model selector
- send message flow
- вставка user message + assistant message в storage через contracts
- отображение:
  - provider
  - model
  - latency
  - estimated cost

Acceptance:
- пользователь может открыть thread, отправить prompt и получить ответ
- история сохраняется в локальную БД
- thread после relaunch открывается корректно

#### 5. `feature-compare`
Сделать compare subsystem:
- запуск compare из thread
- выбор 2 моделей
- отправка одного prompt в 2 модели
- UI показа 2 ответов
- action `Choose winner`
- action `Continue with this model`

Acceptance:
- compare не ломает основной thread flow
- оба ответа отображаются с model / latency / cost
- продолжение победившей моделью меняет target model только для следующего turn

#### 6. Switch model flow
Сделать явный механизм смены модели:
- переключение активной модели для следующего user turn
- визуальная индикация смены модели в timeline
- отсутствие ретроактивных изменений у старых сообщений

Acceptance:
- next turn отправляется новой моделью
- старые assistant messages остаются привязанными к старым моделям

#### 7. Финальная интеграция
Как integration owner второй разработчик делает:
- сборку основной ветки интеграции
- подключение storage implementations разработчика 1
- smoke-pass всей навигации
- устранение app-level несовместимостей
- подготовку release candidate branch

### Что он не делает

- реализацию Room/DAO/storage internals
- реализацию setup/settings/projects internals, кроме подключения и мелких integration fixes
- изменение ownership чужих модулей без необходимости

### Результат его блока

К моменту финальной интеграции должны быть готовы:
- рабочий app shell
- network layer
- thread/chat
- compare
- switch model
- единый собранный APK/debug build

## План интеграции и синхронизации

### Phase 0 — Contract Freeze
Срок: первый рабочий день

Разработчик 2:
- фиксирует `core-model`
- фиксирует repository interfaces
- фиксирует route names
- фиксирует module graph

Разработчик 1:
- подтверждает, что storage и feature-экраны могут быть реализованы без уточнений

Результат:
- после этого оба работают параллельно почти без пересечений

### Phase 1 — Параллельная разработка
Срок: основная часть работы

Разработчик 1:
- делает `core-ui`, `data-storage`, `feature-setup`, `feature-projects`, `feature-settings`

Разработчик 2:
- делает `app`, `data-network`, `feature-chat`, `feature-compare`

Правило:
- никакого shared ownership в feature-коде
- любые контрактные изменения сначала обсуждаются, потом меняются

### Phase 2 — Integration
Срок: когда оба feature-блока функционально готовы

Разработчик 2:
- подключает реальные storage implementations
- мержит feature-модули в integration branch
- чинит app-level wiring issues

Разработчик 1:
- оперативно чинит только баги в собственных модулях
- не переписывает app shell и network layer

### Git-правила
- у каждого своя feature-ветка
- интеграционная ветка одна, у owner = разработчик 2
- merge только после локального smoke-check
- shared contracts не меняются в середине feature-этапа без синхронизации

## Test Plan

Общие end-to-end сценарии, которые должны пройти после интеграции:
- first launch without key → setup screen
- save valid key → open projects
- create project → create thread → send first message
- get model response with metadata
- compare same prompt against 2 models
- choose winner → continue thread with winner model
- reopen app → projects/threads/messages preserved
- clear all local data → app returns to clean state

Проверки разработчика 1:
- Room persistence
- setup/settings correctness
- destructive actions
- project/thread list consistency
- local data cleanup

Проверки разработчика 2:
- OpenRouter auth/error handling
- model catalog loading
- send message flow
- compare flow
- switch model behavior
- full navigation and integration

## Assumptions

- Кода ещё нет, поэтому разбиение делается по будущим модулям, а не по существующим файлам.
- Трудозатраты считаются примерно равными:
  - у тебя heavy local foundation + setup/projects/settings + UI-kit
  - у второго разработчика heavy app shell + network/chat/compare + integration
- Второй разработчик владеет интеграцией, поэтому он же владеет контрактами.
- Если интеграция начинает буксовать, первым упрощением должен быть отказ от streaming, а не от compare или switch model.
- Если нужно ещё сильнее снизить риск, compare можно делать сначала как 2 последовательных запроса, а не true parallel execution.
