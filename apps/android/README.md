# Android App

This directory contains the Android-first implementation for the Schedule project.

## Current State

- The project opens in Android Studio and imports correctly.
- The current build baseline is verified with `./gradlew assembleDebug`.
- The UI stack is `Jetpack Compose + Material 3 + Navigation Compose`.
- The current app includes:
  - home entry screen
  - schedule overview
  - course editor
  - time slot editor
  - reminder task settings

## Build Baseline

- Android Gradle Plugin: `9.2.1`
- Gradle Wrapper: `9.4.1`
- JDK: `C:/Users/miaom/scoop/apps/jabba/current/jdk/zulu@21.0`
- `compileSdk`: `36`
- `buildToolsVersion`: `36.1.0`

## Repository Mirrors

The project prefers China mirrors in `settings.gradle.kts` and uses a mirrored Gradle distribution URL in `gradle-wrapper.properties`.

Current mirror choices include:

- Aliyun
- Huawei Cloud
- Tencent Cloud
- `google()` fallback
- `mavenCentral()` fallback

## Persistence Note

The intended long-term local storage solution is Room, but the current working build uses a lightweight `SharedPreferences + JSON + StateFlow` repository implementation.

This is a temporary tradeoff to avoid blocking feature work on the current `AGP 9.x + built-in Kotlin + KSP/Room` compatibility issue encountered in this repository.

## Next Steps

1. Continue improving editing flows and schedule usability.
2. Implement in-app reminder scheduling.
3. Add optional system calendar event creation after permission.
4. Re-evaluate a Room-based persistence layer once the build toolchain is stable.
