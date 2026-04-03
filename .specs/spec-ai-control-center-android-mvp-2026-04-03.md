# Android-First MVP Спецификация для AI Control Center с расширенным UI/UX

## Summary

Целевой продукт: **Android-first MVP** нативного AI control center с backend, готовым к будущему iOS-клиенту без пересборки контрактов.

Опорный value loop MVP: **Ask → Compare → Switch → Understand Cost**.

Зафиксированные решения:
- Платформа релиза: **Android-first**
- Модель доступа: **BYOK-first**
- Auth: **Google Sign-In + email magic link**
- Провайдеры MVP: **OpenRouter + OpenAI + DeepSeek**
- Attachments: **TXT / MD / PDF only**
- Compare outcome: **winner + save**
- Cost controls: **estimated cost + warnings + настраиваемый hard cap**
- Offline: **read-only cache**
- Retention: **cloud sync + user-controlled deletion**
- Навигация: **4 tabs**, `Compare` запускается из composer и thread controls, не отдельным табом
- Главный экран: **hybrid dashboard**
- Device scope: **phone-first + basic tablet/foldable support**
- Визуальный характер: **calm premium utility**
- Compare на телефонах: **horizontal pager**, на более широких экранах split view

Рыночный вывод:
- [Poe](https://poe.com/about) и их [multi-bot chat](https://poe.com/blog/multi-bot-chat-on-poe) подтверждают ценность multi-model thread.
- [ChatHub](https://chathub.gg/) подтверждает спрос на “one prompt, multiple answers”.
- [Monica](https://monica.im/) подтверждает спрос на “all models in one place”, но слишком широка по scope.
- [OpenRouter](https://openrouter.ai/) подтверждает спрос на unified access, routing, privacy policies и cost transparency.
- [LMArena](https://lmarena.ai/) подтверждает baseline-ожидание: сравнение моделей и явное disclosure про передачу данных провайдерам.

## MVP Scope

Обязательные фичи:
- Onboarding на 2-3 экрана с value framing и disclosure по privacy/provider visibility.
- Вход через Google и email magic link.
- Подключение OpenRouter, OpenAI, DeepSeek.
- `Home / Ask dashboard` с quick actions, recent projects, recent threads, quick composer и быстрым входом в compare.
- `Projects` с созданием, переключением и project-specific context.
- Single-model chat со streaming, provider/model badge, latency и cost.
- Compare на 2 модели с preflight estimate, cost warning, partial failure handling, winner selection и сохранением ответа.
- Switch model внутри thread с явным указанием смены модели.
- Routing presets: `Fast`, `Best Quality`, `Cheap`, `Private`, `Study`, `Writing`, `Research`, `Coding`.
- Usage screen с per-message cost и агрегатами по времени/провайдерам.
- Настраиваемый hard cap и spend warnings.
- Report response flow.
- Settings для privacy mode, fallback policy, provider management и data deletion.
- Read-only offline cache последних проектов, тредов и usage summary.

Вне MVP:
- iOS client
- managed wallet / managed inference
- synthesis третьей моделью
- image/audio/video input
- compare 3+ моделей
- voice mode
- agents / tool-calling UX
- shared workspaces
- prompt marketplace

## UI/UX Requirements

### 1. Информационная архитектура

Root navigation:
- `Ask` — root открывается как **hybrid dashboard**, а не пустой чат
- `Projects`
- `Usage`
- `Settings`

Навигационные правила:
- Bottom navigation на compact width.
- `Compare` не живёт как root destination; он вызывается из composer, quick actions и thread header controls.
- Каждый root-tab сохраняет своё внутреннее состояние.
- Android system back всегда ведёт вверх по стеку, не используется для кастомных действий.
- Модальные действия только для self-contained задач: connect provider, compare preflight, report response, hard-cap edit.

### 2. Экранная модель

Onboarding:
- Screen 1: value proposition “one place for multiple AI models”
- Screen 2: compare / switch / cost visibility
- Screen 3: privacy disclosure + CTA к auth
- После входа: быстрый путь либо к provider connect, либо в demo shell без отправки

Home / Ask dashboard:
- Верх: project switcher, active preset, connection status
- Средина: quick actions `Ask`, `Compare`, `Connect provider`, `Open recent thread`
- Блок `Recent projects`
- Блок `Recent threads`
- Блок `Usage snapshot` за день/месяц
- Нижняя sticky зона: quick composer с одной строкой ввода и явной кнопкой compare

Project screens:
- Project list: title, last activity, provider preference summary
- Project detail: threads, pinned prompts, attachments overview
- Создание project: title required, description optional

Thread screen:
- Sticky header: project name, current model/preset, overflow actions
- Message list: user / assistant turns, provider chip, model name, latency, cost
- Composer pinned к низу экрана
- Actions у assistant message: save, copy, report, retry with another model
- Видимый transition marker при смене модели внутри thread

Compare flow:
- Entry points: composer toggle, quick action с dashboard, thread action “Compare next reply”
- Step 1: preflight bottom sheet с route A/B, supported attachments, estimated combined cost, budget warning
- Step 2 compact width: **horizontal pager** с быстрым переключением `A/B`, sticky compare summary сверху
- Step 2 medium/expanded width: **2-pane split view**
- Compare controls: winner selection, save winner, retry failed side
- Если одна сторона упала, успешный ответ не скрывается

Usage screen:
- Summary cards: today, month, top provider
- Spend progress against hard cap
- Provider breakdown
- Recent costly requests
- Filters по provider / period / project

Settings:
- Профиль
- Auth methods
- Provider connections
- Privacy mode
- Fallback toggle
- Spend controls / hard cap
- Data export / deletion entry points
- Legal / policy texts

### 3. Layout Rules

Compact width:
- Один столбец
- Все primary actions находятся в нижней зоне экрана
- Compare results не рендерятся в 2 узкие колонки
- Composer и key CTAs доступны одной рукой

Medium / expanded width:
- Базовая адаптация без отдельного tablet UX
- Projects могут переходить в list-detail
- Compare может использовать split view
- Navigation rail допустим только как adaptive enhancement, не как отдельная MVP-ветка

Spacing / sizing:
- 8dp grid
- Горизонтальные отступы: 16dp на телефонах
- Touch targets: минимум 48dp
- Composer и нижние CTA: 56dp+ по высоте

### 4. Visual System

Стиль: **calm premium utility**
- Спокойные поверхности, высокая читаемость, минимум декоративного шума
- Акцент не на “AI wow”, а на доверии, ясности и контроле
- Material 3 как база
- Dynamic color поддерживается, но нужен стабильный fallback brand palette
- Основной визуальный контраст строится через surface hierarchy, не через агрессивные gradients

Приоритеты визуальной иерархии:
- 1 уровень: active task / current model / project context
- 2 уровень: answer content
- 3 уровень: cost / latency / provider metadata
- 4 уровень: advanced controls

Типографика:
- Android-native type scale
- Заголовки умеренные, body-text доминирует
- Для model/provider metadata использовать compact label style
- Длинные ответы не должны выглядеть “как стена логов”

Компоненты, обязательные для design system:
- Provider chip
- Model chip
- Preset chip
- Cost badge
- Warning banner
- Empty state card
- Compare summary header
- Message action sheet
- Attachment capability warning
- Spend progress card

### 5. Interaction Rules

Touch-first:
- Primary CTA всегда в thumb zone
- Никаких gesture-only сценариев
- Любой swipe имеет button fallback
- Любой destructive action требует подтверждение или безопасную дистанцию

Composer:
- Один основной composer для Ask и Compare entry
- Compare не должен требовать перехода в отдельный root раздел
- Перед отправкой видны active preset, target model/provider и attachment state
- Budget breach блокирует send до подтверждённого изменения лимита

Loading / feedback:
- Tap feedback <50ms
- Если действие >100ms, показывать loader / skeleton / stream state
- Streaming ответ не должен прыгать по layout
- Partial failures в compare и provider errors показывать как recoverable states, не как silent fail

Haptics / motion:
- Только смысловая анимация: tab transitions, compare mode enter, save winner, warning reveal
- Motion должна поддерживать continuity, не играть роль декора
- Respect reduced motion settings

### 6. Accessibility & Trust UX

Обязательные требования:
- Dynamic type / font scaling without layout breakage
- Screen reader labels для provider, model, cost, warnings, compare state
- Contrast не ниже WCAG AA на ключевых текстах и controls
- Error text не полагается только на цвет
- Compare controls доступны без horizontal precision gestures
- Report flow и privacy disclosures написаны простым языком

Trust signals:
- Перед compare и send ясно указано, какие провайдеры получат запрос
- Cost помечается как `estimated`, если final accounting может отличаться
- Если вложение не поддерживается, блокировка происходит до отправки
- При privacy mode UI должен явно показывать, что fallback отключён или ограничен

## Architecture / Interfaces

Android stack:
- Kotlin + Jetpack Compose + Coroutines/Flow
- UDF, state hoisting, feature modules
- Credential Manager для auth UX
- Android Keystore для app credentials
- SAF/file picker для TXT/MD/PDF
- WorkManager для фоновой синхронизации некритичных задач
- Room/DataStore для read-only cache и lightweight local preferences

Backend responsibilities:
- Auth
- Provider relay
- Secret vault
- Routing engine
- Usage/cost source of truth
- Compare orchestration
- Moderation/reporting
- Data deletion flows

Минимальные API:
- `POST /auth/signin/google`
- `POST /auth/signin/email-link`
- `GET /me`
- `GET/POST /projects`
- `GET/POST /projects/:id/threads`
- `GET /threads/:id`
- `POST /threads/:id/messages`
- `POST /threads/:id/compare-estimate`
- `POST /threads/:id/compare`
- `GET /usage/summary`
- `GET /providers`
- `POST /providers/connect`
- `DELETE /providers/:id`
- `POST /reports/messages/:id`
- `GET /routing-presets`
- `PATCH /settings`

## Validation / Test Plan

Продуктовые сценарии:
- Пользователь входит, подключает provider и получает первый ответ за <3 минут.
- После relaunch приложение открывается на hybrid dashboard, а не в потерянном состоянии.
- Пользователь из dashboard запускает Ask и Compare без поиска скрытых entry points.
- Compare на телефоне читается через pager без потери контекста A/B.
- На wider layout compare корректно переходит в split view.
- Пользователь видит winner flow и может сохранить удачный ответ.
- Переключение модели внутри thread не ломает timeline readability.
- Hard cap настраивается в профиле и реально блокирует отправку.
- Без сети доступны recent projects / recent threads / usage summary в read-only виде.
- Report response доступен на каждом AI-ответе.

UX / quality checks:
- One-handed usability audit для всех primary actions на compact width
- Проверка touch targets >=48dp
- Font scale 1.3x / 1.5x не ломает ключевые экраны
- TalkBack-прохождение для onboarding, thread, compare, usage, settings
- Empty/loading/error states есть на каждом root screen
- No dead ends: в каждом critical failure state есть retry, back или recovery path

Нефункциональные цели:
- Crash-free sessions >99.5%
- p95 first-token latency <6s для streaming paths
- Failed request rate <3% без provider outages
- Provider secrets не хранятся на устройстве в plaintext
- Usage totals совпадают с backend events

## Assumptions

Принятые допущения:
- Android MVP остаётся **phone-first**, но UI сразу не должен ломаться на tablets/foldables.
- Hybrid dashboard считается частью `Ask` root, а не отдельным пятым табом.
- Dynamic color включаем как enhancement, не как обязательную основу бренда.
- Compare на compact width делаем через pager, потому что true split ухудшает читаемость.
- Projects остаются важной единицей контекста, но приложение не должно ощущаться как файловый менеджер.
- `Calm premium utility` важнее, чем максимально плотный power-user UI в первой версии.

Источники:
- [PRODUCT_SPEC_AI_CONTROL_CENTER.md](/Users/ivn_srg/Downloads/AI%20Control%20Center%20Mobile/docs/PRODUCT_SPEC_AI_CONTROL_CENTER.md)
- [IMPLEMENTATION_PLAN.md](/Users/ivn_srg/Downloads/AI%20Control%20Center%20Mobile/docs/IMPLEMENTATION_PLAN.md)
- [TECHNICAL_ARCHITECTURE_SPEC.md](/Users/ivn_srg/Downloads/AI%20Control%20Center%20Mobile/docs/TECHNICAL_ARCHITECTURE_SPEC.md)
- [Poe About](https://poe.com/about)
- [Poe Multi-bot Chat](https://poe.com/blog/multi-bot-chat-on-poe)
- [OpenRouter](https://openrouter.ai/)
- [ChatHub](https://chathub.gg/)
- [Monica](https://monica.im/)
- [LMArena](https://lmarena.ai/)
- [Android Architecture Guidance](https://developer.android.com/topic/architecture)
- [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager)
- [Adaptive layouts / window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
- [What’s New in Jetpack Compose, May 20 2025](https://android-developers.googleblog.com/2025/05/whats-new-in-jetpack-compose.html)
- [Supabase](https://supabase.com/)
- [Firebase](https://firebase.google.com/)
- [RevenueCat](https://www.revenuecat.com/)
