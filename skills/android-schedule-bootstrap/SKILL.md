---
name: android-schedule-bootstrap
description: Android repository bootstrap and troubleshooting guide for this Schedule project. Use when setting up `apps/android`, fixing Gradle or SDK configuration, aligning JDK/AGP/Compose versions, applying China mirrors, handling AGP 9.x Kotlin behavior, or continuing feature work on the Android schedule app scaffold.
---

# Android Schedule Bootstrap

Use this skill for work under `apps/android` in this repository.

## Quick Start

1. Treat `apps/android` as the Android root project.
2. Use the local JDK at `C:/Users/miaom/scoop/apps/jabba/current/jdk/zulu@21.0` via `gradle.properties`.
3. Prefer Android Studio stable with Compose + Material 3.
4. Build with `./gradlew assembleDebug` before changing business logic further.
5. Keep UI style close to standard Google-recommended Material 3 unless the user explicitly asks for a custom visual language.

## Current Known-Good Baseline

- `AGP`: `9.2.1`
- Gradle wrapper: `9.4.1`
- Gradle distribution mirror: Huawei Cloud
- `compileSdk`: `36`
- `buildToolsVersion`: `36.1.0`
- `minSdk`: `28`
- `targetSdk`: `30` for now
- Java/Kotlin JVM target: `21`
- UI stack: Compose + Material 3 + Navigation Compose
- Current persistence fallback: lightweight local repository based on `SharedPreferences + JSON + StateFlow`

Read [references/android-troubleshooting.md](references/android-troubleshooting.md) when build or environment issues appear.

## Workflow

### 1. Environment Check

Verify these files first:

- `apps/android/gradle.properties`
- `apps/android/build.gradle.kts`
- `apps/android/app/build.gradle.kts`
- `apps/android/settings.gradle.kts`
- `apps/android/gradle/wrapper/gradle-wrapper.properties`

Confirm:

- `org.gradle.java.home` points to the local `zulu@21.0`
- wrapper uses Gradle `9.4.1`
- repositories include China mirrors plus `google()` and `mavenCentral()` fallback
- `compileSdk` is not lower than dependency requirements; use `36` in this repo unless there is a specific reason to move higher

### 2. Build-Triage Order

When the Android app breaks, use this order:

1. Run `./gradlew assembleDebug` in `apps/android`.
2. Fix configuration incompatibilities before touching feature code.
3. Fix API or import errors next.
4. Only then continue feature work.

Do not guess from screenshots alone if the repo is available locally. Read the actual files and compile.

### 3. AGP 9.x Rules In This Repo

This repository already hit several AGP 9.x behavior changes:

- `org.jetbrains.kotlin.android` is not required for Kotlin support when using AGP built-in Kotlin.
- old `android { kotlinOptions { ... } }` usage is no longer valid in this setup.
- use:

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```

- `KSP` was not compatible with the working AGP built-in Kotlin setup we reached in this repository.
- Because of that, do not reintroduce `KSP` casually while the project is still moving fast.

### 4. Persistence Rule For This Repo

The intended long-term storage layer is `Room`, but the current working app uses a temporary repository implementation:

- keep app/domain/viewmodel/UI code talking to `ScheduleRepository`
- keep storage details behind `AppContainer`
- if you need to move back to Room later, swap the repository implementation without rewriting screens

Current implementation file:

- `apps/android/app/src/main/java/com/miaom/schedule/data/repository/PreferencesScheduleRepository.kt`

When reintroducing Room later:

1. check AGP/Kotlin/KSP compatibility first
2. keep `ScheduleRepository` API stable
3. migrate `AppContainer` only after a successful compile

## UI And Product Guidance

For this repository, keep the Android app aligned with a practical school schedule product:

- use standard Material 3 patterns
- avoid flashy or experimental styling unless requested
- prioritize usable flows over architecture experiments
- preferred first-wave features:
  - 课表查看
  - 课程编辑
  - 时间段编辑
  - 定时任务设置

Current reminder direction:

- support `应用内通知`
- support optional `系统日历事件` after user grants calendar permission
- model reminder channels in domain first, then wire actual platform capabilities later

## Architecture Guidance

Preserve this split unless there is a strong reason to change it:

- `ui`: Compose screens, navigation, viewmodels, state collection
- `domain`: course, time slot, reminder models and pure rules
- `data`: repository implementations and persistence adapters
- `platform`: later home for notifications, alarms, permissions, background scheduling

If future Rust sharing is explored, only move pure rules and computation into a shared layer. Do not try to share Android UI or Android-specific scheduling code.

## Validation

Use this minimum validation loop after meaningful Android changes:

1. run `./gradlew assembleDebug`
2. verify main navigation still opens
3. verify schedule editing still saves and reloads
4. verify overview and reminder screens still render

Prefer compile verification first, emulator/manual verification second.
