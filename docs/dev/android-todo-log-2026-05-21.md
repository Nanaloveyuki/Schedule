# Android 待办日志（2026-05-21）

## 适用范围

本文记录 `apps/android` 截至 2026-05-21 收尾时的状态、已确认方向、agent 分工约定和明日待办，供下一轮继续开发时直接接手。

## 今日收口结论

- 当前 Android 主线仍以 `SharedPreferences + JSON + StateFlow + ScheduleDocument` 为可运行基线。
- 多轮改动后，`./gradlew assembleDebug` 在本地已多次通过。
- 第一到第三轮的主要能力已经接上：
  - App Shell 与一级导航
  - 课表总览双视图
  - 课程 / 时间段 / 提醒任务编辑
  - 内联创建时间段
  - 单双周与课程级时间覆盖
  - 课表尺寸配置
  - 撤回 / 重做
  - 个性化 / 预设 / 导入导出
  - 通知 / 日历提醒平台接线
- 当前更需要做的是 UI 收口和设备级回归，不建议马上开第四轮新能力。

## Agent 分工约定

### 当前固定分工

- `agent1~4`：用于编码实现。
- `agent5`：用于修补、集成、回归、杂项处理。

### 当前建议职责

- `agent1`：主页面与课表结构调整。
  - 重点负责 `ScheduleOverviewScreen.kt` 这类核心主路径页面。
  - 适合处理课表网格尺寸、头部信息压缩、主视图排版、可读性问题。
- `agent2`：壳层与导航表现修复。
  - 重点负责 `ScheduleAppShell.kt`、`ScheduleNavGraph.kt` 一类壳层文件。
  - 适合处理底部导航、宽屏适配、选中态、栏高、图标与标签表现。
- `agent3`：个性化 / 预设 / 设置的信息架构与页面整理。
  - 重点负责 `PersonalizationScreens.kt`、`PersonalizationComponents.kt`。
  - 适合处理折叠分组、二级结构、实时预览布局、页面减压。
- `agent4`：编辑页与提醒页交互修复。
  - 重点负责 `TaskSettingsScreen.kt`、编辑页选择器、下拉菜单、回填链路。
  - 适合处理组件交互不稳定、表单项可选性、说明文案收口。
- `agent5`：集成与收尾。
  - 负责吸收前 1 到 4 的主版本。
  - 负责做编译验证、冲突收口、文案清理、风险回归点整理。

### 明天如果继续下发任务，建议保持的方式

- 不要让多个 agent 同时改同一页面的大块结构。
- 优先按“每个 agent 一个明确写入边界”分配。
- `agent5` 不要重做主版本，只负责合并、修补和验收。

## 今天确认过的最新处理方向

### 1. 课表页首屏压缩

- `ScheduleOverviewScreen.kt` 已按最近一轮方案收紧顶部结构。
- 方向是：
  - 标题保留 `课表`
  - 顶部状态改成紧凑信息条
  - 周视图 / 列表视图切换保留，但不再用大卡片承载
  - 主课表区域优先吃剩余空间
- 目标是把首屏更多高度还给课程网格，而不是说明区。

### 2. 底部导航视觉修复

- `ScheduleAppShell.kt` 最近一轮已针对底部导航选中态做收口。
- 问题来源是默认 `NavigationBarItem` 选中背景在当前高度与间距下容易出现顶部被裁切的视觉问题。
- 当前方向是改成可控的图标圆底选中态，而不是继续依赖默认 indicator。

### 3. 提醒页选择器稳定性

- `TaskSettingsScreen.kt` 的“课程选择”和“提醒方式”此前存在不可选问题。
- 最近一轮方案已把它们收口到更标准的可展开选择器路径，而不是手写点击拦截式假下拉。
- 这块目前以“编译通过 + 代码链路核对”为主，仍需要设备级点击回归。

### 4. 个性化 / 预设 / 设置信息密度

- `PersonalizationScreens.kt`、`PersonalizationComponents.kt` 已开始往折叠分组和更稳定预览结构收口。
- 方向已经明确：
  - 首屏减少大段说明
  - 复杂配置折叠收起
  - 实时预览不要再硬塞满表格
- 但从实际体验反馈看，视觉拥挤问题还没有完全结束。

## 当前仍未完全收口的问题

### 第一优先级

- 底部导航虽然已改过，但需要真机或模拟器确认选中圆底是否完全不裁切。
- 课表页顶部虽然已经压缩，但还需要看实际设备上是否真的把主要空间还给了网格。
- 课表页仍要继续关注字体遮挡、课表格子可读性和宽屏手机下的横向展示效率。

### 第二优先级

- 个性化页实时预览此前出现过错位，需要继续看不同宽度下是否稳定。
- 预设 / 个性化 / 设置 目前虽然已开始折叠分组，但视觉上仍可能偏拥挤，后续仍可继续拆二级页或进一步减首屏密度。
- 提醒设置页需要实际点击验证：
  - 课程选择是否稳定展开
  - 提醒方式是否稳定展开
  - 编辑既有提醒时是否能正确回填

### 第三优先级

- 内置预设功能虽然已有结构，但产品完成度还不算最终版，后续仍要继续补真实内容和交互。
- 自定义图标目前未引入，导航先使用 Material Icons 作为临时稳定方案即可。

## 明日建议处理顺序

1. 先在模拟器或真机回归 `课表` 首屏和 `底部导航`，确认视觉问题是否已实际改善。
2. 如果课表首屏仍显拥挤，继续收紧 `ScheduleOverviewScreen.kt`：
   - 进一步压缩头部信息
   - 继续缩减非核心提示
   - 优先保证网格和课程文字可读性
3. 回归 `TaskSettingsScreen.kt` 的两个选择器，确认不是“代码上像修了，设备上还是别扭”。
4. 回归 `PersonalizationScreens.kt` 与 `PersonalizationComponents.kt`：
   - 看实时预览是否还错位
   - 看折叠结构是否已经足够减压
5. 如果前三项通过，再继续做内置预设完善或进一步的页面美化。

## 明日建议下发方式

如果明天继续按 agent 拆任务，建议如下：

- `agent1`：只改 `ScheduleOverviewScreen.kt`，继续压课表页头部并放大主网格。
- `agent2`：只改 `ScheduleAppShell.kt`，继续收口底部导航选中态和栏高细节。
- `agent3`：只改 `PersonalizationScreens.kt` 与 `PersonalizationComponents.kt`，继续减压个性化 / 预设 / 设置三页。
- `agent4`：只改 `TaskSettingsScreen.kt`，继续修提醒页选择器、回填和可点击反馈。
- `agent5`：负责集成、编译、文案复查、回归清单整理，不直接重做前四者主结构。

## 明天优先看的文件

- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/ScheduleOverviewScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/navigation/ScheduleAppShell.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/TaskSettingsScreen.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/screen/PersonalizationScreens.kt`
- `apps/android/app/src/main/java/com/miaom/schedule/ui/component/PersonalizationComponents.kt`

## 明天开工前建议

按当前项目的稳定工作顺序继续：

1. 先读上面几个文件的真实现状，不只看 agent 摘要。
2. 先跑 `apps/android` 下的 `./gradlew assembleDebug`。
3. 先做 UI 收口和回归，不急着开第四轮新功能。
4. 改完后再次编译，再做模拟器或真机点按验证。

## 备注

- 当前很多近期结论来自“编译通过 + 代码路径核对 + 用户局部实测”的组合，不等于所有主路径都已经做过完整设备级验收。
- 后续每次进入新一轮前，优先确认当前分支工作区改动边界，避免多轮 agent 改动继续叠加导致收口困难。
