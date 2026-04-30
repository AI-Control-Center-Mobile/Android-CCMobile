# Android-CCMobile

Android client for a local-first AI control workspace built around OpenRouter. The app lets you save an API key on device, organize work into projects and threads, chat with selected models, compare two model responses side by side, and inspect OpenRouter key diagnostics without relying on an app backend.

## What the app does

- Stores OpenRouter API keys locally in encrypted Android storage
- Organizes conversations into projects and threads
- Sends chat prompts to OpenRouter models and streams assistant responses
- Compares two models side by side for the same prompt
- Shows key diagnostics and free-tier quota status in Settings
- Keeps app state on device using Room and DataStore

## Main user flow

1. On first launch, the app opens the setup screen.
2. The user saves an OpenRouter API key.
3. The app routes to `Projects`.
4. The user can create or open a project.
5. Inside a project, the user can create a thread and open the chat screen.
6. From chat, the user can compare two models on the same prompt and continue with the preferred result.

## Tech stack

- Kotlin 2.0.21
- Android Gradle Plugin 9.0.1
- Jetpack Compose + Material 3
- Android Navigation Compose
- Room for local persistence
- DataStore for app preferences
- EncryptedSharedPreferences + MasterKey for API key storage
- Retrofit + OkHttp + kotlinx.serialization for OpenRouter networking
- JUnit + MockWebServer + coroutine test utilities for tests

## Module layout

- `:app`
  - Android application entry point
  - DI container wiring
  - top-level navigation
- `:core-model`
  - domain contracts and shared models
  - repository interfaces
  - route definitions and shared error types
- `:core-ui`
  - app theme, spacing, reusable cards/buttons/layout primitives
  - markdown rendering helpers used by chat and compare flows
- `:data-storage`
  - Room database, DAOs, entities, repositories
  - encrypted API key storage
- `:data-network`
  - OpenRouter service definitions
  - DTOs, mappers, repositories for models/chat/compare/diagnostics
- `:feature-setup`
  - first-run key setup flow
- `:feature-projects`
  - project list and project detail/thread list
- `:feature-chat`
  - thread chat screen and streaming response flow
- `:feature-compare`
  - side-by-side model comparison UI and winner continuation flow
- `:feature-settings`
  - saved key management, quota diagnostics, destructive local-data actions

## Project structure

```text
Android-CCMobile/
├── app/
├── core-model/
├── core-ui/
├── data-network/
├── data-storage/
├── feature-chat/
├── feature-compare/
├── feature-projects/
├── feature-settings/
├── feature-setup/
├── gradle/
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

## Prerequisites

- Android Studio with a recent AGP 9.x compatible toolchain
- JDK 17
- Android SDK 36
- An emulator or Android device with API 29+
- Internet access for OpenRouter requests

## Getting started

### 1. Clone the repository

```bash
git clone https://github.com/AI-Control-Center-Mobile/Android-CCMobile.git
cd Android-CCMobile
```

### 2. Open in Android Studio

Open the repository root as a Gradle project. The project uses the version catalog in `gradle/libs.versions.toml` and the modules declared in `settings.gradle.kts`.

### 3. Sync dependencies

Android Studio Gradle sync should resolve:

- AndroidX Compose libraries
- Room
- Retrofit / OkHttp
- DataStore
- Android Security Crypto

### 4. Build from terminal

```bash
./gradlew app:assembleDebug
```

### 5. Install on a connected device or emulator

```bash
./gradlew app:installDebug
```

## First-run setup

The app launches into the setup flow if no OpenRouter key is available.

- Enter an OpenRouter API key
- Save it locally on device
- Continue to Projects

The key is stored in encrypted shared preferences via `EncryptedSharedPreferences` and `MasterKey`.

## Local data model

The app persists data with Room using these entities:

- `ProjectEntity`
- `ThreadEntity`
- `MessageEntity`
- `CachedModelEntity`

This supports:

- local project and thread browsing
- persisted conversation history
- cached model catalog data for picker/compare flows

## OpenRouter integration

The app talks directly to OpenRouter from the device.

### Supported network flows

- model catalog fetch
- chat completion / streaming chat
- compare flow using two independent model requests
- current key diagnostics lookup

### Important behavior

- Chat uses streamed assistant responses.
- Compare runs model A and model B independently, then lets the user continue with the preferred winner.
- Settings can query current key diagnostics, including free-tier related metadata.
- Rate limits for `:free` models are still relevant even if the account has credits.

## UI architecture

The app is feature-modular but intentionally simple:

- `app` owns navigation and dependency wiring
- each feature module owns its screen state and ViewModel
- `core-ui` centralizes shared visual language
- `core-model` owns contracts between features and data layers
- repositories abstract storage/network details from features

This keeps the app easy to iterate on while still separating:

- UI composition
- domain contracts
- persistence
- network transport

## Key screens

### Setup

- saves the OpenRouter key
- explains local-only credential handling

### Projects

- create workspace
- browse existing workspaces
- open project detail

### Project detail

- see thread summary for a project
- create a new thread
- browse recent thread previews

### Chat

- select a model
- send prompts
- stream assistant output
- open compare flow

### Compare

- choose model A and model B
- run side-by-side compare
- retry failed candidates
- continue with the winning answer

### Settings

- add/remove/clear saved OpenRouter keys
- inspect quota diagnostics
- wipe local app data

## Build commands

### Build debug APK

```bash
./gradlew app:assembleDebug
```

### Install debug APK

```bash
./gradlew app:installDebug
```

### Run unit tests

```bash
./gradlew test
```

### Run a targeted module test task

```bash
./gradlew feature-compare:testDebugUnitTest
```

## Notes for development

- `MainActivity` enables edge-to-edge and hosts `AiControlCentreRoot`.
- Navigation is driven from `app/navigation/AppNavigation.kt`.
- The initial destination is decided by whether an API key exists.
- The app can seed demo workspace data when local storage is empty and a key becomes available.
- Markdown helpers in `core-ui` are used both for full assistant output and compact previews.

## Security notes

- API keys are stored locally, not on an app-owned backend.
- `allowBackup` is disabled in the Android manifest.
- Local data wipe in Settings clears credentials and local workspace state.

## Current limitations

- Compare reliability on `:free` models is constrained by OpenRouter/provider-side rate limits.
- The app is Android-only.
- There is no cloud sync layer; this is intentionally local-first.

## Repository sanity checks

Useful smoke check:

```bash
./gradlew app:assembleDebug
```

If you are actively changing model/network behavior, also run the relevant module tests before pushing.
