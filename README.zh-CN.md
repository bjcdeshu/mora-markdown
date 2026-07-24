# Mora

**一款安静、本地优先的 Android Markdown 阅读与编辑器。**

[English](README.md)

Mora 只围绕一条简单的使用链路：打开一份标准 Markdown 文件，舒服地阅读，需要时随手修改，并始终保留对文件的控制权。

它以单篇文档为中心，而不是以 Vault 为中心；没有账号、私有文档格式、遥测或云端服务。

> Mora v0.3.0 是第一个稳定发布目标。原计划的稳定版 v0.2.0
> 在发布前已被后续版本取代；`v0.2.0-rc.1` 仅保留为历史 Pre-release。
> 欢迎通过 GitHub Issues 反馈特定设备或文件提供器上的问题。

## 核心能力

- 随滚动方向显隐工具栏的沉浸阅读，以及安静、可拖动的右侧阅读进度标记
- 调校后的默认排版，以及字号、行高和页面边距调节
- H1–H3 目录、当前章节高亮和文内搜索
- 最近文档与逐文档阅读位置恢复
- 通过 Android Storage Access Framework 访问本地文件
- 通过 Android `VIEW`、`EDIT` 和 `SEND` Intent 从微信、文件管理器等外部应用打开内容
- 基础 Markdown 源码编辑、移动端格式快捷栏、保存与另存为
- CommonMark，并支持表格、删除线和任务列表
- 跟随设备语言的英文与简体中文界面，以及 Android 13+ 的单应用语言入口
- 跟随系统、浅色、深色三种外观模式，以及可选的纯黑深色表面
- 三套可由用户选择的 Fine Frame 启动图标配色
- 在支持的 Android 版本上提供从文档返回首页的预测性返回动画
- Material 3、动态配色，以及 Android 8.0+

## 不做什么

Mora 不准备成为知识库工作区。Vault、双链、关系图谱、内置云同步、账号系统和插件生态目前不在产品范围内。

## 下载

已发布的签名 APK 与 SHA-256 校验文件会附在本仓库的
[GitHub Releases](../../releases) 中。请留意版本标签与说明：Pre-release
用于测试，稳定版则已经通过文档规定的“准确附件真机验收门”。

## 从源码构建

需要：

- JDK 17 或更新版本（可直接使用 Android Studio 自带运行时）
- Android SDK 36
- 兼容 Android Gradle Plugin 9.3 的较新版本 Android Studio

克隆仓库后运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK 会生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

Mora 支持 Android 8.0（API 26）及以上系统。

## 渲染与联网边界

Mora 使用 `commonmark-java` 解析 Markdown，生成 HTML 后套用受控 CSS，并在 Android WebView 中显示。

- Markdown 中的原始 HTML 会被转义。
- 渲染器会清理潜在的不安全 URL。
- JavaScript 只用于 Mora 控制的阅读交互，例如标题跳转和当前章节识别。Markdown 内容无法借由原始 HTML 注入脚本，Mora 也没有向页面暴露 JavaScript Bridge。
- WebView 内禁用本地文件、`content://`、文件 URL 跨域和明文网络访问；
  Mora 自身的阅读导航只允许固定 HTTPS origin。
- Markdown 引用远程图片时，阅读页面可能连接图片所在服务器，因此 Mora 声明了 Android 联网权限。
- 点击网页链接会交给系统浏览器处理。

完整边界见 [PRIVACY.md](PRIVACY.md) 和 [SECURITY.md](SECURITY.md)。

## 工程结构

```text
app/src/main/java/de/unbow/mora/
├── data/       # 文件访问、阅读设置、最近文档
├── markdown/   # CommonMark 解析与阅读页 HTML/CSS
├── model/      # 文档状态与持久化协调
└── ui/         # Compose 页面、编辑器、阅读器、搜索与目录
```

Android 外壳使用 Jetpack Compose 与 Material 3；阅读链路为 CommonMark → HTML/CSS → WebView，编辑器为原生 Compose 源码编辑器。

当前发布基线、验证门槛与发布边界记录在 [PROJECT_STATUS.md](PROJECT_STATUS.md)。
签名候选版、真机验证和正式发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 参与贡献

欢迎提交 Bug 和边界清晰的改进。较大的 Pull Request 请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 并创建 Issue 讨论。

产品方向见 [ROADMAP.md](ROADMAP.md)，版本变化见 [CHANGELOG.md](CHANGELOG.md)。

## 开源许可

Mora 使用 [MIT License](LICENSE)。
