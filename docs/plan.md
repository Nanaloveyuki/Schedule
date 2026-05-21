# Android-First Schedule App Plan

## Summary

- 首发路线定为：`Windows` 主机开发、`Kotlin` 优先、`Android` 原生先行，但在架构上预留未来共享边界。
- 当前本地 JDK 可直接复用，已确认可读目录为 `C:\Users\miaom\scoop\apps\jabba\current\jdk`，其中包含 `zulu@17.0.18`、`zulu@21.0.10`、`zulu@25.0.2` 等版本。
- 安卓主开发 JDK 默认使用 `JDK 21`，优先指向 `C:\Users\miaom\scoop\apps\jabba\current\jdk\zulu@21.0`。
- 未来如果做 `Rust + Tauri 2` 的移动端实验，保留 `JDK 17` 作为兼容回退，路径使用 `C:\Users\miaom\scoop\apps\jabba\current\jdk\zulu@17.0`。

## Latest Direction

- Android 官方当前已处于 `AGP 9.2.x` 这一代，`Google Maven metadata` 也能确认 `9.2.1` 已发布，因此新项目不应按旧教程锁在 `AGP 8.1` 或 `8.2`。
- Kotlin Gradle 插件元数据已到 `2.4.0-RC`，但首发项目不追 `RC`，应以 Android Studio 当前稳定模板生成的 Kotlin/Compose 组合为准。
- Tauri 2 官方先决条件页已明确：Android 侧仍需要 `Android Studio`、`Android SDK`、`NDK`、`ANDROID_HOME` / `NDK_HOME` 与 `rustup` Android targets；iOS 仍然只能在 `macOS + Xcode` 上开发。
- 结论：`Rust + Tauri 2` 适合作为后续桌面与移动统一层，不应反过来约束安卓首发架构与交付节奏。

## Android Environment

- 安装 `Android Studio` 最新稳定版，不单独手工配置 Gradle，直接使用 Studio 或模板生成的 `Gradle Wrapper`。
- 在 Android Studio 中把 `Gradle JDK` 明确设为 `C:\Users\miaom\scoop\apps\jabba\current\jdk\zulu@21.0`，不要依赖漂移的全局 `JAVA_HOME`。
- 通过 `SDK Manager` 安装以下组件：
  - 最新稳定 `Android SDK Platform`
  - 对应版本的 `Build-Tools`
  - `Platform-Tools`
  - `Android Emulator`
  - 至少一个 `x86_64` 或 `arm64` 模拟器镜像
- 由于首版“定时任务”偏后台任务优先，必须额外准备真机调试；仅依赖模拟器不足以覆盖厂商后台限制。
- Android 项目模板固定为：
  - `Kotlin`
  - `Jetpack Compose`
  - `Material 3`
  - 单 `Activity`
  - `minSdk` 以现代后台任务能力为准，不兼容过老系统
- 工程首批依赖默认采用：
  - `Room` 管理课表、时间段、任务配置
  - `WorkManager` 处理延迟与周期任务调度
  - `AlarmManager` 处理精确定时场景
  - `Foreground Service` 仅在确有持续后台需求时启用
  - 依赖注入默认使用 `Hilt`
- 当前安卓骨架为了避开 `AGP 9.x` 与 `KSP/Room` 的兼容问题，暂时使用轻量本地存储实现仓库；等基础功能稳定后再切回 `Room` 更稳妥。
- 本地 JDK 使用策略固定为：
  - Android 主线：`zulu@21.0`
  - Tauri / Rust 移动实验：优先试 `zulu@17.0`
  - 不把 `25` 作为首发基线，避免踩生态兼容边缘

## Implementation Shape

- Android 首发先做 4 个功能域：
  - 课表查看
  - 课表编辑
  - 时间段编辑
  - 定时任务设置
- 定时任务策略补充：如果系统能力允许，提醒任务应支持二选一，既可以仅使用应用内通知，也可以在用户授权日历权限后改为写入系统日历事件。
- 代码分层固定为：
  - `app/ui`：Compose 页面、导航、状态收集
  - `domain`：课程、时间段、提醒、排课规则模型与用例
  - `data`：Room、Repository、系统调度适配
  - `platform`：通知、闹钟、权限、后台服务封装
- 为未来跨端预留的共享边界只放在 `domain`：
  - 课程结构
  - 时间段规则
  - 排课计算
  - 提醒生成规则
- 暂不共享的平台能力：
  - Android 通知
  - WorkManager / AlarmManager
  - 前台服务
  - 权限处理
- 如果后续引入 Rust，共享范围仅迁移纯规则与计算，不迁移 UI、存储适配、后台任务执行器。

## Test Plan

- 环境验证：
  - Android Studio 能识别 `zulu@21.0`
  - 新建 Compose 工程能同步、编译、运行
  - 模拟器与真机都能安装调试包
- 功能验证：
  - 课表增删改查
  - 时间段编辑后能立即反映到课表展示
  - 提醒任务创建、更新、取消后系统调度同步变化
  - 设备重启后任务能恢复
- 后台任务验证：
  - 熄屏场景
  - 省电模式
  - 应用被系统回收后
  - 厂商后台限制机型上的提醒到达率
- 回归验证：
  - 时区切换
  - 夏令时边界
  - 跨天课程
  - 重复课程与冲突时间段

## Assumptions

- 默认目标是先交付一个真正可用的安卓版本，而不是优先做跨端技术验证。
- 默认后台任务是强需求，因此从一开始就纳入 `WorkManager + AlarmManager + 真机测试`。
- 默认不在首版把 Rust 引入安卓 App 主线；Rust 共享层放到第二阶段再评估。
- 默认直接复用现有 `jabba` JDK 安装，不重新下载 Oracle 或 Temurin。
