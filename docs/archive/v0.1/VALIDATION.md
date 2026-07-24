# Demo 校验记录

## 已完成

- AndroidManifest 与全部资源 XML 语法校验
- Gradle Version Catalog TOML 语法校验
- Kotlin 源文件基础结构与括号扫描
- HTML/CSS/JavaScript 交互预览自动化测试
- 首页、阅读页、编辑页移动端截图检查
- ZIP 完整性检查

## 交互预览自动化覆盖

- 打开示例文档
- 阅读视图渲染
- 切换编辑模式
- 修改 Markdown 内容
- 未保存状态变化
- 返回阅读模式并重新渲染
- 打开排版设置
- 修改正文字号
- 关闭设置
- 返回时显示放弃修改确认框

## 未执行

当前生成环境没有 Android SDK，也无法完成 Android 依赖下载，因此没有在此环境内产出 APK 或执行真机 / 模拟器构建。

首次在 Android Studio 同步后，应执行：

```bash
./gradlew assembleDebug
```

如果尚未生成 Gradle Wrapper 二进制文件，先执行：

```bash
gradle wrapper --gradle-version 9.5.0
```
