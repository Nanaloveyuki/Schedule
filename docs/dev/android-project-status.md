# Android 项目状态与后续开发建议

## 当前已完成

### 工程与构建基线

- `apps/android` 已可作为独立 Android 项目稳定导入 Android Studio。
- 当前构建基线仍为 `Compose + Material 3 + Navigation Compose`。
- `./gradlew assembleDebug` 在当前主线已再次验证通过。
- 已确认的工具链组合仍是：
  - AGP `9.2.1`
  - Gradle Wrapper `9.4.1`
  - JDK `21`
  - `compileSdk 36`

### 架构主线

- 当前 Android 主线已从早期的零散列表模型收口到 `ScheduleDocument`。
- 当前持久化基线为：
  - `SharedPreferences + JSON + StateFlow`
  - `ScheduleRepository`
  - `ScheduleStore`
- 已补上文档级读写、规范化、旧数据兼容迁移、撤回 / 重做。
- `AppContainer` 现在同时承接：
  - `ScheduleRepository`
  - `ScheduleStore`
  - `ReminderOrchestrator`

### 已落地的主要能力

- 自适应 App Shell 与一级导航。
- 课表总览双视图：
  - 周视图
  - 列表视图
- 课程编辑增强：
  - 单双周
  - 课程级时间覆盖
  - 课程颜色样式
- 时间段编辑增强：
  - 内联创建
  - 更完整的编辑结构
- 提醒任务编辑增强：
  - 课程选择器
  - 提醒方式选择器
  - 权限与状态反馈
- 个性化能力：
  - 主题颜色令牌
  - 背景模式
  - 字体配置
  - 课表尺寸配置
- 预设能力：
  - 内置主题预设
  - 用户主题预设
  - 课程模板预设
- 导入导出能力：
  - 文件包导出 / 导入
  - 剪贴板分享包导出 / 导入
- 提醒平台接线：
  - 应用内通知调度
  - 精确定时能力判断
  - 系统日历写入与同步
  - 开机 / 安装更新后的提醒重建入口

## 最近一次提交归纳

当前 Android 主线最近已形成以下 3 个提交：

1. `b698c85` `feat(android): add document schedule store and reminder orchestration`
2. `7311067` `feat(android): add adaptive shell and dynamic schedule overview`
3. `380c697` `feat(android): improve course, slot and task editor flows`

它们对应的意义分别是：

- 第一批：把数据主线收口到 `ScheduleDocument + ScheduleStore`，并接上提醒编排、导入导出、个性化与预设基础能力。
- 第二批：把应用入口从原本的单页跳转结构推进到自适应壳层，并重做课表总览页。
- 第三批：把课程、时间段、提醒编辑页从“能录入”推进到“更接近可用产品表单”。

## 当前技术折中

原计划存储层仍然是 Room，但当前仓库继续保持以下折中：

- 保留 `ScheduleRepository` 抽象边界。
- 实际运行层先使用 `SharedPreferences + JSON + StateFlow`。
- `ScheduleDocument` 作为统一文档模型先跑主线。

这个折中现在仍然合理，原因没有变化：

- 先把产品主路径做完整。
- 先把 UI、数据结构和提醒链路稳定下来。
- 暂时避免再次被 `AGP 9.x + built-in Kotlin + KSP/Room` 兼容性卡住。

## 当前最值得做的事情

### 第一优先级

- 做模拟器或真机回归，而不是继续叠加新功能。
- 回归 `ScheduleOverviewScreen.kt` 的主网格可读性和首屏空间分配。
- 回归 `ScheduleAppShell.kt` 的底部导航选中态和栏高表现。
- 回归 `TaskSettingsScreen.kt` 的两个选择器、回填和点按反馈。
- 回归 `PersonalizationScreens.kt` 的实时预览稳定性与首屏密度。

### 第二优先级

- 收口提醒平台的设备级行为验证：
  - 通知权限
  - 精确定时权限
  - 开机后重建
  - 日历写入结果
- 继续补课表、编辑页、设置页的空状态与引导文案。
- 补一轮文档状态更新，避免 `docs/dev` 再次落后于真实代码。

### 第三优先级

- 继续完善内置预设的内容完成度。
- 根据真机体验决定是否继续拆分个性化 / 预设 / 设置的信息架构。
- 等主线更稳定后，再评估是否恢复 Room。

## 当前不建议优先做的事情

- 立即恢复 Room + KSP。
- 为了架构整洁再次大规模重构 Android 主线。
- 在尚未做设备级回归前继续追加第四轮大功能。
- 为跨端共享而提前改动 Android UI 或提醒平台实现。

## 当前主要风险与未收口点

### UI 风险

- 课表周视图虽然已经重做，但仍需要真实设备确认：
  - 格子高度是否足够
  - 课程文字是否遮挡
  - 宽屏下横向效率是否真正提升
- 底部导航虽然换成了可控选中态，但还需要确认是否彻底避免裁切。
- 个性化 / 预设 / 设置虽然已经折叠分组，但页面密度仍可能偏高。

### 交互风险

- 课程复制 / 剪切 / 粘贴和撤回 / 重做需要继续做设备级回归。
- 提醒页课程选择、提醒方式选择和既有任务回填仍需要点按验证。
- 导入导出已具备结构，但还需要实际走一遍文件与剪贴板流程。

### 平台风险

- 通知与日历接线已经进代码，但不能把“可编译”当成“设备上已稳定可用”。
- 提醒到达率、熄屏场景、省电模式和国产机后台限制仍未真正验证。
- 日历事件后续仍需要继续关注删除、重复同步和目标日历选择策略。

## 当前稳定工作方式

每次继续改安卓端时，优先按这个顺序：

1. 先读 `apps/android` 当前真实代码，而不是只看旧摘要。
2. 先跑 `apps/android` 下的 `./gradlew assembleDebug`。
3. 优先收口 UI、交互和设备回归，不急着继续扩能力。
4. 改完后再次编译。
5. 再做模拟器或真机验证。

## 当前建议优先看的文件

- `apps/android/app/src/main/java/com/miaom/schedule/domain/model/ScheduleDocument.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/data/repository/PreferencesScheduleRepository.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/platform/scheduler/ReminderOrchestrator.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/navigation/ScheduleAppShell.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/ScheduleOverviewScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/TaskSettingsScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/PersonalizationScreens.kt`

## 结论

当前 Android 分支已经不再是“课表雏形”，而是进入“文档化数据模型 + 自适应壳层 + 编辑工作台 + 提醒平台接线”的阶段。

下一轮的正确重点不是继续快速加功能，而是：

- 做设备级回归
- 收 UI 与交互细节
- 补文档与验收结论
