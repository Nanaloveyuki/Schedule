# Android Compose 与导航

## 目标

整理与课程表项目最相关的 Compose 和导航知识点，优先针对当前仓库的开发节奏，而不是覆盖全部 Jetpack Compose 能力。

## 官方资料摘要

### Compose setup

Android 官方 Compose setup 页面强调：

- 新项目应优先采用 Compose 工程结构
- 通过 Gradle 启用 Compose 支持
- 以 Material 3 作为标准设计系统
- 通过版本平台或统一版本策略降低依赖冲突

这些点与本项目方向一致：

- 界面使用 Compose
- 风格采用 Google 推荐的 Material 3
- 当前 UI 不走 XML 视图主线

### Compose Navigation

Android 官方导航页面强调：

- 在 Compose 中使用 `NavHost`、`NavController` 和目的地定义管理导航
- 路由应简洁稳定
- 自适应布局和深层链接要从导航层考虑，而不是零散塞进页面逻辑
- 测试时应能分别验证导航图和页面状态

## 本项目当前导航结构

当前仓库已落地的主链路：

- 首页
- 课表查看
- 课程编辑
- 时间段编辑
- 定时任务设置

已验证可编译的导航实现位置：

- `apps/android/app/src/main/java/com/miaom/schedule/ui/navigation/ScheduleNavGraph.kt`

## 适合本项目的 Compose 约束

### 1. 保持 Material 3 标准风格

用户明确要求使用 Google 推荐风格，因此当前界面策略应是：

- 标准 `TopAppBar`
- `Card` 承载功能入口和列表项
- `OutlinedTextField` 作为录入主表单
- 页面结构简洁，优先可用性

不适合当前阶段的事情：

- 为了“设计感”重写一套非标准风格组件
- 过早引入复杂动画和实验性布局

### 2. 先把业务入口跑通，再细化交互

当前雏形已经存在，但部分录入还偏工程化，例如：

- 课程录入需要手填 `slotId`
- 提醒任务需要手填 `courseId`

更适合下一步的优化方向：

- 用下拉或选择器替代裸 ID 输入
- 在课表查看页按星期和时间段排序展示
- 提供空状态提示和快捷入口

### 3. 页面只关心状态，不关心存储细节

Compose 页面和 ViewModel 目前通过 `ScheduleRepository` 获取数据，这个边界要继续保留。

原因：

- 当前存储实现已经从 Room 切到 `SharedPreferences + JSON + StateFlow`
- 如果页面直接耦合数据库细节，后续再切回 Room 代价会很高

## 对后续功能的具体建议

### 课表查看

- 增加按时间段开始时间排序
- 增加当时间段缺失时的友好提示
- 后续可加“今天课程”和“本周概览”摘要

### 编辑页

- 课程编辑页增加时间段选择器
- 时间段页可加入预设模板
- 定时任务页可从现有课程列表中选择课程

### 导航层

- 当前页面数少，保留单层 `NavHost` 即可
- 暂不需要引入复杂嵌套路由

## 当前阶段的验证重点

- `./gradlew assembleDebug` 必须先通过
- 页面导航必须从首页完整可达
- 新增记录后返回查看页能看到变化
