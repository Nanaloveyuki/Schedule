# Android 待办日志（2026-05-21）

## 适用范围

本文记录 `apps/android` 在最近一轮三次提交之后的主线状态、分工建议和下一轮建议处理顺序，供继续协作时直接接手。

## 本轮收口结论

- 当前 Android 主线已经从“基础雏形”推进到“文档化课表工作台”。
- `./gradlew assembleDebug` 在提交后已再次通过。
- 当前可运行基线仍然是：
  - `SharedPreferences + JSON + StateFlow`
  - `ScheduleRepository`
  - `ScheduleStore`
  - `ScheduleDocument`
- 最近一轮的三次提交已经分别收口了：
  - 文档存储、撤回 / 重做、导入导出、提醒编排
  - 自适应壳层、动态主题、课表总览重构
  - 课程 / 时间段 / 提醒编辑流增强

## 最近三次提交

1. `b698c85` `feat(android): add document schedule store and reminder orchestration`
2. `7311067` `feat(android): add adaptive shell and dynamic schedule overview`
3. `380c697` `feat(android): improve course, slot and task editor flows`

## 当前已确认的主能力

- 一级导航已经扩展为：
  - `课表`
  - `编辑`
  - `预设`
  - `个性化`
  - `设置`
- 课表总览已经具备：
  - 周视图
  - 列表视图
  - 周次 / 单双周状态提示
  - 课程复制 / 剪切 / 粘贴
  - 撤回 / 重做入口能力
- 编辑流已经具备：
  - 课程颜色样式
  - 单双周
  - 课程级时间覆盖
  - 内联创建时间段
  - 提醒任务课程选择与提醒方式选择
- 主题与设置已经具备：
  - 个性化主题编辑
  - 背景 / 字体 / 尺寸配置
  - 主题与课程模板预设
  - 文件与剪贴板导入导出
- 提醒平台已经具备：
  - 通知调度结构
  - 精确定时能力判断
  - 日历事件写入与同步结构
  - 开机重建入口

## 当前更需要做的事情

当前不建议再把精力放在“快速扩第四轮能力”上，下一轮更应该做：

- 模拟器或真机回归
- UI 细节收口
- 交互点按验证
- 提醒平台设备级验证

## Agent 分工约定

### 当前固定分工

- `agent1~4`：用于编码实现。
- `agent5`：用于修补、集成、回归、杂项处理。

### 当前建议职责

- `agent1`：主页面与课表结构收口。
  - 重点负责 `ScheduleOverviewScreen.kt`。
  - 适合处理网格尺寸、头部密度、课程格可读性、宽屏效率。
- `agent2`：壳层与导航表现修复。
  - 重点负责 `ScheduleAppShell.kt`、`ScheduleNavGraph.kt`。
  - 适合处理底部导航、宽屏适配、选中态、栏高和标签表现。
- `agent3`：个性化 / 预设 / 设置的信息架构收口。
  - 重点负责 `PersonalizationScreens.kt`、`PersonalizationComponents.kt`、相关 viewmodel。
  - 适合处理折叠结构、预览稳定性、页面减压、导入导出体验。
- `agent4`：编辑页与提醒页交互修复。
  - 重点负责 `CourseEditorScreen.kt`、`TimeSlotEditorScreen.kt`、`TaskSettingsScreen.kt`。
  - 适合处理表单选择器、回填链路、点按反馈、文案收口。
- `agent5`：集成与验收。
  - 负责吸收前 1 到 4 的主版本。
  - 负责编译验证、冲突收口、回归清单整理、文档同步。

### 下发任务时建议保持的方式

- 不要让多个 agent 同时改同一个页面的大块结构。
- 优先按文件边界分配写入范围。
- `agent5` 不要重做主版本，只负责集成、回归、验收与补文档。

## 当前仍未完全收口的问题

### 第一优先级

- 课表页需要实际设备确认：
  - 首屏空间是否真的还给了主网格
  - 格子高度是否足够承载课程信息
  - 宽屏下是否真的提升了横向效率
- 底部导航需要确认选中态是否彻底不裁切。
- 课程复制 / 剪切 / 粘贴和撤回 / 重做需要做真实操作回归。

### 第二优先级

- `TaskSettingsScreen.kt` 需要验证：
  - 课程选择是否稳定展开
  - 提醒方式是否稳定展开
  - 编辑既有提醒时是否能正确回填
- `PersonalizationScreens.kt` 需要验证：
  - 实时预览是否还会错位
  - 折叠结构是否真的减轻首屏密度
- 导入导出需要至少实际走一遍：
  - 文件包导出
  - 文件包导入
  - 剪贴板分享包导入导出

### 第三优先级

- 通知、精确定时、日历写入虽然已接线，但仍缺设备级结论。
- 内置预设和课程模板预设还可以继续补内容完成度。

## 下一轮建议处理顺序

1. 先在模拟器或真机回归 `课表` 与 `底部导航`。
2. 如果周视图仍显拥挤，继续收紧 `ScheduleOverviewScreen.kt`：
   - 压缩非核心提示
   - 优先保证课程格信息可读
   - 继续调整自适应尺寸策略
3. 回归 `TaskSettingsScreen.kt` 的选择器和回填链路。
4. 回归 `PersonalizationScreens.kt` 与导入导出流程。
5. 如果前四项通过，再继续打磨预设内容或提醒平台细节。

## 下一轮建议下发方式

- `agent1`：只改 `ScheduleOverviewScreen.kt`。
- `agent2`：只改 `ScheduleAppShell.kt` 与 `ScheduleNavGraph.kt`。
- `agent3`：只改 `PersonalizationScreens.kt`、`PersonalizationComponents.kt`、导入导出相关 viewmodel。
- `agent4`：只改 `CourseEditorScreen.kt`、`TimeSlotEditorScreen.kt`、`TaskSettingsScreen.kt`。
- `agent5`：负责编译、回归、文案复查、文档同步，不直接重做前四者主结构。

## 下一轮优先看的文件

- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/ScheduleOverviewScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/navigation/ScheduleAppShell.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/TaskSettingsScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/PersonalizationScreens.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/platform/scheduler/ReminderOrchestrator.kt`

## 下一轮开工前建议

1. 先读上面几个文件的真实现状，不只看旧摘要。
2. 先跑 `apps/android` 下的 `./gradlew assembleDebug`。
3. 先做回归和收口，不急着再开大功能。
4. 改完后再次编译，再做模拟器或真机点按验证。

## 备注

- 当前很多结论来自“编译通过 + 代码路径核对 + 提交整理”，不等于所有主路径都已做完整设备验收。
- 后续每次再进入新一轮前，优先确认当前工作区边界，避免多轮 agent 改动继续叠加导致收口困难。
