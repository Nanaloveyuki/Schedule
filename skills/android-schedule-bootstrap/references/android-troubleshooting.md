# Android Troubleshooting Notes

## Environment Decisions Already Validated

- Project root for Android work: `apps/android`
- Preferred JDK: `C:/Users/miaom/scoop/apps/jabba/current/jdk/zulu@21.0`
- Working Gradle wrapper: `9.4.1`
- Working AGP: `9.2.1`
- Working SDK baseline in this repo: `compileSdk 36`, `buildToolsVersion 36.1.0`

## China Mirror Notes

Use mirrors in `settings.gradle.kts`, not old `buildscript { repositories {} }` snippets copied from legacy Gradle docs.

Keep these repository types available:

- Aliyun mirror
- Huawei mirror
- Tencent mirror
- `google()` fallback
- `mavenCentral()` fallback

For Gradle wrapper downloads, set the wrapper URL directly to a mirror if needed, for example Huawei Cloud.

## Issues Already Encountered

### 1. Kotlin plugin no longer required under AGP 9 built-in Kotlin

Observed error:

- `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.`

Resolution in this repo:

- remove `org.jetbrains.kotlin.android` while staying on AGP built-in Kotlin

### 2. `kotlinOptions` unresolved

Observed error:

- `Unresolved reference 'kotlinOptions'`

Resolution in this repo:

- move JVM target config into top-level `kotlin { compilerOptions { ... } }`

### 3. Gradle wrapper too old

Observed error:

- minimum supported version was `9.4.1`

Resolution in this repo:

- update `apps/android/gradle/wrapper/gradle-wrapper.properties`

### 4. `compileSdk` too low for modern AndroidX/Compose artifacts

Observed error:

- multiple AAR metadata failures because the app compiled against `android-30`

Resolution in this repo:

- raise `compileSdk` to `36`
- install SDK platforms `34`, `35`, `36`

### 5. KSP incompatible with AGP built-in Kotlin in this working setup

Observed error:

- `KSP is not compatible with Android Gradle Plugin's built-in Kotlin`

Practical resolution used here:

- remove KSP from the active build path
- avoid blocking feature work on Room code generation
- use temporary local persistence behind `ScheduleRepository`

This was the key repo-specific pivot. Do not lose it by blindly re-adding Room compiler setup.

## Current Product Notes

- User prefers Google-recommended Android style
- Android is the primary delivery target right now
- Reminder features should allow either:
  - in-app notifications
  - or optional system calendar event creation after permission

## Safe Next Steps

Good next tasks for this repo:

- improve schedule overview sorting and display
- replace raw ID entry with picker-based forms
- add actual notification scheduling
- add optional calendar insertion flow

Less safe tasks unless there is time to absorb build risk:

- reintroducing Room + KSP immediately
- broad dependency upgrades without a compile pass
- switching away from Material 3 patterns without product reason
