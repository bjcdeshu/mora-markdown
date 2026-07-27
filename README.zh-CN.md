# Mora

**一款安静、本地优先的 Android Markdown 阅读器，需要时也能做恰到好处的轻编辑。**

[English](README.md)

**[下载最新版本](https://github.com/bjcdeshu/mora-markdown/releases/latest)** · Android 8.0+ · [使用反馈](https://github.com/bjcdeshu/mora-markdown/issues/15) · [报告 Bug](https://github.com/bjcdeshu/mora-markdown/issues/new?template=bug_report.yml) · [LINUX DO](https://linux.do/)

![Mora 阅读页与目录界面](docs/assets/social-preview.png)

## 下载

每个稳定版本都附有签名 APK 和对应的 SHA-256 校验文件。Pre-release 是测试候选版；标记为 **Latest** 的版本已经通过 Mora 对准确附件的发布验收。

## 围绕一篇文档而设计

- 从 Android 文件选择器或其他应用打开标准 Markdown 文件，并从最近文档继续阅读。
- 使用调校后的排版、安静的阅读进度、阅读位置恢复，以及字号、行高和页面边距调节。
- 通过 H1–H3 目录、当前章节高亮和文内搜索浏览长文。
- 使用移动端格式快捷栏做轻量源码编辑，并一键原地保存；只有新建目标或需要可写副本时才进入“另存为”。
- 跟随设备语言与外观，提供英文和简体中文界面、浅色/深色模式、动态配色和三套启动器配色。

## 界面截图

| 阅读 | 文档目录 | 首页 |
|:--:|:--:|:--:|
| ![在 Mora 阅读器中打开一篇中英双语 Markdown 文档](docs/screenshots/v0.3.2/reader.png) | ![Mora 为长文生成的文档目录](docs/screenshots/v0.3.2/toc-or-search.png) | ![Mora 首页的最近文档与打开和新建入口](docs/screenshots/v0.3.2/home.png) |

## 本地优先

Mora 通过 Android Storage Access Framework 读写普通 Markdown 文件。没有账号、私有文档格式、遥测或内置云服务；文件存放在哪里由你的存储提供器决定，控制权始终属于你。

Markdown 原始 HTML 会被转义，不安全 URL 会被清理；阅读 WebView 不暴露 JavaScript Bridge，也不开放本地文件访问。文档引用远程图片时，渲染仍可能连接图片所在服务器。完整边界见 [隐私说明](PRIVACY.md) 与 [安全策略](SECURITY.md)。

## Mora 不做什么

Mora 不是 Vault 或知识库工作区。双链、关系图谱、账号、内置云同步和插件生态不在当前产品范围内。

## 从源码构建

需要：

- JDK 17 或更新版本（支持 Android Studio 自带运行时）
- Android SDK 36
- 兼容 Android Gradle Plugin 9.3 的 Android Studio

使用仓库自带的 Gradle Wrapper：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Debug APK 会生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 参与贡献

欢迎提交边界清晰的 Bug 和改进。较大的修改请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。当前方向见 [ROADMAP.md](ROADMAP.md)，版本历史见 [CHANGELOG.md](CHANGELOG.md)，签名发布流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 开源许可

Mora 使用 [MIT License](LICENSE)。
