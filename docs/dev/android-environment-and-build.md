# Android 环境与构建

## 适用范围

本文面向当前仓库 `apps/android` 的开发，不是通用 Android 教程。

## 当前项目已验证基线

- Android 根目录：`apps/android`
- JDK：`C:/Users/miaom/scoop/apps/jabba/current/jdk/zulu@21.0`
- AGP：`9.2.1`
- Gradle Wrapper：`9.4.1`
- Gradle Wrapper 镜像：Huawei Cloud
- `compileSdk`：`36`
- `buildToolsVersion`：`36.1.0`
- `minSdk`：`28`
- `targetSdk`：`30`

## 本项目环境配置要点

### 1. JDK 选择

- 当前仓库已把 `org.gradle.java.home` 固定在 `gradle.properties`。
- Android 主线优先用 `JDK 21`，不要把 `JDK 25` 当首发基线。
- 如果 Android Studio 自己的 Gradle JDK 和项目配置冲突，优先以项目文件和能编译通过的组合为准。

### 2. SDK 安装建议

至少安装以下内容：

- Android SDK Platform `34`
- Android SDK Platform `35`
- Android SDK Platform `36`
- 对应 `Build-Tools`
- `Platform-Tools`
- `Android Emulator`

这次仓库已经验证过，`compileSdk 30` 会直接触发大量现代 AndroidX/Compose 依赖的 AAR metadata 错误，因此这里至少要保持 `36`。

### 3. 国内镜像策略

本项目实践证明，镜像要优先放在 `settings.gradle.kts` 的：

- `pluginManagement.repositories`
- `dependencyResolutionManagement.repositories`

不要优先采用旧式 `buildscript { repositories {} }` / `allprojects { repositories {} }` 教程片段。

当前项目已使用：

- 阿里云 Maven 镜像
- 华为云 Maven 镜像
- 腾讯云 Maven 镜像
- `google()` 兜底
- `mavenCentral()` 兜底

Gradle Wrapper 下载速度慢时，直接把 `gradle-wrapper.properties` 里的 `distributionUrl` 改成镜像源更有效。本项目当前使用：

```properties
distributionUrl=https\://repo.huaweicloud.com/gradle/gradle-9.4.1-bin.zip
```

## Android 官方资料摘要

以下内容来自 Android 官方站点或其中国镜像，结合本项目筛选：

### AGP 9.2 系列

官方 AGP 9.2 发布说明页面表明：

- AGP 9.2 已是当前正式代际之一
- 需要关注其兼容矩阵，而不是沿用旧教程里的 AGP 8.1/8.2 固定写法
- 工具链升级时，必须同步看 Gradle 与 JDK 兼容，而不是只改一个版本号

这与本项目的实战结果一致：Gradle `9.0.0` 不够，必须升到 `9.4.1`。

### Compose 快速入门

官方 Compose setup 页面强调：

- 新项目优先使用 Compose 模板
- 使用 Material 3
- 在 Gradle 中显式启用 Compose 构建能力
- 使用版本对齐策略，避免零散手写不兼容版本

本项目对应做法：

- 单 Activity
- Compose + Material 3
- Navigation Compose
- JVM 目标统一到 21

## 本项目踩过的关键坑

### 1. Kotlin Android 插件提示“不再需要”

实际遇到过：

- `The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.`

说明：

- 在 AGP 9 的 built-in Kotlin 模式下，不能盲目照搬旧模板。

### 2. `kotlinOptions` 写法过时

实际遇到过：

- `kotlinOptions` / `jvmTarget` unresolved

当前项目改成：

```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
```

### 3. `compileSdk` 太低导致依赖整体失配

实际遇到过：

- Compose、Navigation、Lifecycle、Core 等一串依赖要求 `compileSdk >= 34/35/36`

结论：

- 新项目如果用现代 Compose 依赖，不要把 `compileSdk` 卡在 30。

## 当前建议

- 短期内保持现有构建组合稳定，不主动大升级。
- 每次修改依赖或插件后，先跑 `./gradlew assembleDebug` 再继续写业务。
- 如果后续要恢复 Room/KSP，先重新验证 AGP/Kotlin/KSP 兼容矩阵。
