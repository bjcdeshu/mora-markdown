# 安卓现代 Markdown 阅读与编辑应用：产品与技术方案整理

## 1. 项目背景

目前桌面端已经有 Typora 这样成熟、排版精致、阅读与编辑体验统一的 Markdown 工具，但安卓端仍缺少一款真正满足以下要求的产品：

- 界面现代
- 阅读体验接近 Typora
- 能直接打开本地 `.md` 文件
- 支持稳定、顺手的 Markdown 编辑
- 不以知识库、双链或复杂笔记系统为核心
- 保持标准 Markdown 文件，不绑定私有数据库
- 采用接近 Google 原生应用的 Material You 风格

现有安卓 Markdown 应用大多存在明显偏差：

- Markor 更偏工具型，界面和排版不够现代
- Obsidian 更偏知识库和工作空间，不是纯粹的文档阅读器
- 其他应用要么成熟度不足，要么偏写作套件，要么文件管理不够自然

因此，这个项目的核心机会不是“再做一个笔记软件”，而是做一款：

> 面向本地 Markdown 文件的现代化阅读与编辑工具。

---

## 2. 产品定位

产品只围绕两个核心功能展开：

1. 阅读
2. 编辑

不以知识库、数据库、双链或云端协作为核心。

### 2.1 核心定位

> 打开任意 Markdown 文件，像阅读排版精美的文章一样阅读，并能随时切换到现代化编辑模式。

### 2.2 产品原则

- Document-first，而不是 Vault-first
- 本地文件优先
- 标准 Markdown 优先
- 阅读体验优先
- 编辑稳定性优先
- 视觉现代，但不让正文变成 Material 卡片堆叠
- 不把简单工具做成复杂工作空间

---

## 3. 对现有安卓方案的判断

## 3.1 Markor

Markor 的优势是：

- 轻量
- 开源
- 离线
- 本地文件支持较好
- 文件操作自由

但问题也很明显：

- 界面偏旧
- 设计感不足
- 阅读排版不够精致
- 更像实用工具，而不是现代文档阅读器

因此，Markor 解决的是“能不能打开和编辑 Markdown”，没有解决“是否愿意长时间阅读”。

## 3.2 Obsidian

Obsidian 的核心对象不是单篇文档，而是整个 Vault。

它主要围绕以下能力设计：

- 文件夹与笔记库
- 内部链接
- 反向链接
- 标签
- 属性
- 图谱
- 插件
- 多窗格工作区

它适合知识管理，但不完全适合只想安静打开一篇 Markdown 文档的人。

核心区别可以概括为：

| 维度 | Typora | Obsidian |
|---|---|---|
| 产品中心 | 单篇文档 | 整个 Vault |
| 核心目标 | 阅读与写作 | 知识管理 |
| 编辑方式 | 文档式所见即所得 | 源码编辑加实时装饰 |
| 阅读方式 | 编辑与阅读高度统一 | 独立 Reading View |
| UI 重心 | 正文 | 工作区、目录、链接、插件 |
| 适合场景 | 文档写作与阅读 | 知识库与笔记网络 |

因此，Obsidian 并不是“不够好”，而是产品目标不同。

---

## 4. Typora 的核心策略

Typora 的页面排版本质上可以理解为：

```text
Markdown 源文件
    ↓
解析为结构化文档
    ↓
生成可编辑的 HTML DOM
    ↓
应用 CSS 主题
    ↓
由浏览器排版引擎渲染
```

Typora 之所以有明显的“页面感”，关键不在 Markdown 解析本身，而在：

- HTML 文档结构
- CSS 主题系统
- 浏览器级字体与排版能力
- 有限正文宽度
- 精确的行高、段距和标题间距
- 对 Markdown 标记的动态隐藏
- 极简的应用外壳

### 4.1 页面结构

Typora 的正文可以抽象为：

```html
<body>
  <main id="write">
    <h1>标题</h1>
    <p>正文内容</p>
    <blockquote>引用内容</blockquote>
    <pre><code>代码</code></pre>
  </main>
</body>
```

核心视觉来自 CSS：

```css
body {
  margin: 0;
  background: #ffffff;
  color: #24292f;
  font-family:
    system-ui,
    -apple-system,
    "Noto Sans CJK SC",
    sans-serif;
}

#write {
  max-width: 760px;
  margin: 0 auto;
  padding: 64px 32px 120px;
  font-size: 17px;
  line-height: 1.8;
}

#write p {
  margin: 0 0 1.2em;
}

#write h1 {
  margin: 1.8em 0 0.8em;
  font-size: 2em;
  line-height: 1.3;
}

#write h2 {
  margin: 1.7em 0 0.7em;
  font-size: 1.55em;
  line-height: 1.4;
}

#write blockquote {
  margin: 1.5em 0;
  padding-left: 1em;
  border-left: 3px solid #d0d7de;
  color: #57606a;
}

#write img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1.8em auto;
}

#write pre {
  overflow-x: auto;
  padding: 16px;
  border-radius: 8px;
  background: #f6f8fa;
}
```

### 4.2 Typora 真正困难的部分

真正复杂的不是阅读渲染，而是行内所见即所得编辑。

例如：

```markdown
**加粗文字**
```

正常情况下只显示加粗后的文字，光标进入时再显示必要的 Markdown 符号。

这意味着编辑器需要同时处理：

- Markdown 源码
- 文档结构
- DOM 节点
- 光标位置
- 选择区
- 输入法
- 撤销栈
- 标记显隐
- 源码与页面状态同步

因此，Typora 式编辑不应作为首版目标。

---

## 5. Obsidian 的技术策略

Obsidian 的编辑策略和 Typora 不同。

其核心结构可理解为：

```text
本地 Vault 文件夹
    ↓
文件扫描与索引
    ↓
链接、标签、属性、图谱
    ↓
编辑视图或阅读视图
```

### 5.1 编辑视图

Obsidian 使用 CodeMirror 6 作为编辑器基础。

编辑模式包括：

- Source Mode
- Live Preview

Live Preview 并不是把整篇 Markdown 永久转换成 HTML，而是在源码编辑器上使用装饰、替换和组件覆盖。

例如：

```markdown
![photo](image.jpg)
```

在未聚焦时显示图片，光标进入时再显示原始 Markdown 语法。

这种方式适合知识库编辑，但最终页面感通常不如真正的 HTML 阅读视图。

### 5.2 阅读视图

阅读模式是另一套独立渲染：

```text
同一份 .md 文件
    ├── CodeMirror 6 → 编辑视图
    └── Markdown 渲染器 → 阅读视图
```

这也是为什么 Obsidian 的编辑模式和阅读模式有时并不完全一致。

---

## 6. 推荐的产品策略

项目应组合 Typora 和 Obsidian 的优点，但不复制它们的全部复杂性。

### 6.1 阅读模式采用 Typora 路线

```text
Markdown
    ↓
Markdown Parser
    ↓
HTML
    ↓
CSS 主题
    ↓
WebView
```

目标是获得：

- 高质量排版
- 统一主题
- 表格
- 代码高亮
- 图片
- 数学公式
- Mermaid
- 响应式布局
- 精细字体控制

### 6.2 编辑模式采用 Obsidian 路线

```text
Markdown 源码
    ↓
CodeMirror 6
```

但首版不做 Live Preview，只做高质量源码编辑。

### 6.3 最终产品结构

```text
阅读：Typora 风格
编辑：CodeMirror 6
外壳：Material 3 / Material You
文件：Android 原生文件系统
```

---

## 7. 推荐技术架构

## 7.1 Android 外壳

推荐：

```text
Kotlin
Jetpack Compose
Material 3
Storage Access Framework
WebView
```

Compose 负责：

- 首页
- 最近文件
- 顶栏
- 底部栏
- 设置
- 弹窗
- 主题
- 动态颜色
- 系统交互

WebView 负责：

- Markdown 阅读
- CodeMirror 编辑
- CSS 主题
- 代码高亮
- Mermaid
- 数学公式

## 7.2 推荐结构

```text
Android：Kotlin + Compose
│
├── 文件层
│   ├── 打开 Markdown
│   ├── 保存
│   ├── 另存为
│   ├── 最近文件
│   ├── 文件权限
│   └── 图片及附件访问
│
├── DocumentSession
│   ├── 当前文件 URI
│   ├── Markdown 原文
│   ├── 当前编辑内容
│   ├── 是否修改
│   ├── 当前光标
│   ├── 编辑滚动位置
│   └── 阅读滚动位置
│
└── WebView
    ├── 阅读模式
    │   ├── markdown-it
    │   ├── Typora 风格 CSS
    │   ├── 代码高亮
    │   ├── Mermaid
    │   └── 数学公式
    │
    └── 编辑模式
        ├── CodeMirror 6
        ├── Markdown 语法高亮
        ├── 撤销栈
        ├── 查找替换
        └── Markdown 快捷栏
```

## 7.3 一个 WebView 还是两个 WebView

推荐优先使用同一个 WebView 承载阅读和编辑。

```text
点击阅读
    ↓
读取 CodeMirror 当前内容
    ↓
markdown-it 转换
    ↓
立即展示阅读页面

点击编辑
    ↓
恢复 CodeMirror
    ↓
恢复光标和滚动位置
```

优点：

- 编辑和阅读内容始终一致
- 不需要频繁重新初始化
- 切换速度快
- 共享主题和资源
- 减少内存占用
- 更容易处理未保存内容

---

## 8. 阅读模式设计

阅读模式是产品最重要的差异化部分。

### 8.1 页面排版重点

需要重点控制：

- 正文宽度
- 手机左右留白
- 字体组合
- 字号
- 行高
- 段落间距
- 标题上下间距
- 列表缩进
- 引用块
- 代码块
- 图片尺寸
- 表格滚动
- 链接样式
- 明暗主题

### 8.2 文档主题

建议将文档主题独立于应用主题：

```text
themes/
├── typora-light.css
├── typora-dark.css
├── paper.css
├── minimal.css
└── reading.css
```

后期可支持：

- 自定义 CSS
- 导入兼容后的 Typora 主题
- 字体切换
- 页面宽度调节
- 行距调节
- 段距调节
- 代码主题切换

### 8.3 手机排版建议

桌面端常用 700 至 800 像素正文宽度，但手机端应采用响应式设计：

```css
#write {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding:
    24px
    max(20px, env(safe-area-inset-right))
    96px
    max(20px, env(safe-area-inset-left));
}
```

手机端要避免：

- 页面左右过窄
- 卡片式正文
- 过多阴影
- 标题字号失控
- 表格挤压正文
- 代码块撑破页面
- 图片无法缩放

---

## 9. 编辑模式设计

编辑模式不必复制 Typora，而应追求稳定、现代和手机友好。

### 9.1 基础能力

- Markdown 语法高亮
- 自动换行
- 不显示行号
- 撤销和重做
- 查找与替换
- 自动补全括号
- 自动延续列表
- Tab 与缩进
- 链接插入
- 图片插入
- 代码块插入
- 标题快捷操作
- 光标位置恢复
- 编辑滚动位置恢复

### 9.2 底部快捷栏

建议使用移动端固定快捷栏：

```text
H  B  I  链接  图片  引用  列表  任务  代码
```

快捷栏应支持：

- 选中文字后包裹语法
- 无选中文字时插入模板
- 自动调整光标位置
- 横向滑动
- 跟随系统键盘显示和隐藏

### 9.3 编辑视觉

CodeMirror 不应呈现为传统代码编辑器。

应做到：

- 无行号
- 无边框
- 背景与应用统一
- Markdown 标记弱化
- 正文字体舒适
- 标题语法有层级
- 当前行仅轻微强调
- 代码块与引用有适当区分
- 深色模式不过度高饱和

---

## 10. 文件访问方案

安卓端应使用 Storage Access Framework。

流程：

```text
系统文件选择器
    ↓
获取 content:// URI
    ↓
申请持久读写权限
    ↓
记录最近文件
    ↓
以后可直接重新打开
```

### 10.1 必备能力

- 打开单个 Markdown 文件
- 创建新文件
- 保存
- 另存为
- 最近文件
- 分享
- 用其他应用打开
- 接收其他应用分享的 `.md`
- 从文件管理器直接打开

### 10.2 不建议做的事

首版不要：

- 申请整个存储权限
- 自建复杂文件管理器
- 强制导入应用私有目录
- 把文件复制进私有数据库后再编辑
- 修改原始文件结构

---

## 11. 本地图片处理

本地图片是最容易出现兼容问题的部分。

例如：

```markdown
![图片](images/photo.jpg)
```

Markdown 文件可能来自：

- 本地文件夹
- 网盘
- SAF 文档提供者
- 压缩包解压目录
- 其他应用共享目录

由于 Android 返回的是 `content://` URI，而不是普通文件路径，因此不能简单依赖网页中的相对路径。

建议建立资源映射层：

```text
Markdown 图片地址
    ↓
路径解析器
    ↓
ContentResolver
    ↓
应用内部映射 URL
    ↓
WebView 加载
```

例如：

```text
https://appassets.androidplatform.net/document/image/xxx
```

需要支持：

- 相对路径
- 绝对路径
- URL 编码
- 中文文件名
- 空格
- 大小写差异
- 同级目录
- 上级目录
- 网络图片
- SVG
- GIF

---

## 12. 保存策略

不应每输入一个字符就写入磁盘。

推荐流程：

```text
用户输入
    ↓
CodeMirror 内存状态
    ↓
300 至 800 毫秒防抖
    ↓
同步到 DocumentSession
    ↓
手动保存 / 切到后台 / 退出
    ↓
写入原文件
```

### 12.1 文件冲突检测

应记录：

- 打开时内容哈希
- 当前内容哈希
- 文件最后修改时间
- 是否存在外部变更

当其他应用修改文件时，不能直接无提示覆盖。

应提供：

- 保留当前版本
- 重新加载外部版本
- 另存为
- 对比变化

### 12.2 异常恢复

首版就应考虑：

- 应用崩溃
- 系统杀后台
- 文件写入失败
- 权限失效
- 存储设备卸载
- 网盘离线

可以将未保存内容暂存到应用私有缓存，但原始文件仍是唯一正式来源。

---

## 13. 安全设计

Markdown 可能包含原始 HTML。

例如：

```html
<script>
  // 任意脚本
</script>
```

阅读器默认不应允许文档执行任意 JavaScript。

建议：

- 默认关闭原始 HTML
- 或使用 HTML Sanitizer
- 禁止文档脚本执行
- 禁止任意文件访问
- 禁止跨域读取本地资源
- 使用受控资源映射
- WebView 只暴露最小化 JavaScript Bridge
- 不允许文档调用 Android 原生敏感接口

应用自己的 markdown-it、CodeMirror、Mermaid 等脚本可以运行，但用户文档中的脚本不应运行。

---

## 14. 视觉风格

用户喜欢 RikkaHub 一类的 Google 原生风格。

更准确的称呼是：

- Material Design 3
- Material You
- Jetpack Compose Material 3
- Pixel 风或 Google 原生感

### 14.1 推荐视觉组合

```text
Material You 原生应用外壳
        +
Typora 风格文档阅读区
        +
现代 Markdown 源码编辑器
```

### 14.2 各区域的视觉分工

| 区域 | 视觉策略 |
|---|---|
| 首页 | Material 3 |
| 最近文件 | Material 3 |
| 设置 | Material 3 |
| 顶栏 | Material 3 |
| 菜单和弹窗 | Material 3 |
| 底部快捷栏 | Material 3 |
| 阅读正文 | Typora 式 HTML/CSS |
| 编辑区 | CodeMirror 自定义主题 |
| 文档主题 | 独立于应用动态色 |

### 14.3 不能完全 Material 化的区域

Markdown 正文不应使用：

- 大量卡片
- 明显按钮
- 过多圆角容器
- 强动态色背景
- 过度分割线
- 浮夸阴影

正文应该像文档，而不是设置页。

### 14.4 推荐视觉语言

- 圆角适中
- 留白充足
- 动画克制
- 支持壁纸动态配色
- 支持深色模式
- 使用 Material Symbols
- 工具栏轻量
- 内容优先
- 减少固定边框
- 不滥用卡片

---

## 15. MVP 范围

首版只做真正影响使用体验的功能。

## 15.1 必做功能

### 文件

- 打开 `.md`
- 新建 `.md`
- 保存
- 另存为
- 最近文件
- 从其他应用打开
- 分享

### 阅读

- Markdown 渲染
- 标题
- 列表
- 任务列表
- 引用
- 表格
- 代码块
- 代码高亮
- 图片
- 链接
- 目录
- 文内搜索
- 明暗主题
- 阅读位置记忆

### 编辑

- CodeMirror 6
- Markdown 高亮
- 自动换行
- 撤销与重做
- 查找与替换
- Markdown 快捷栏
- 光标位置记忆
- 编辑滚动位置记忆

### 设置

- 应用主题
- 文档主题
- 字号
- 行距
- 页面边距
- 代码字号
- 是否显示目录

## 15.2 可以后做

- Mermaid
- 数学公式
- 自定义 CSS
- Typora 主题导入
- 文件夹浏览
- 多标签页
- 分屏预览
- 导出 HTML
- 导出 PDF
- Git 同步
- Syncthing 集成
- WebDAV
- 云盘增强
- 文件历史版本

## 15.3 首版不做

- 双链
- 图谱
- 标签数据库
- 属性系统
- 笔记知识库
- 插件市场
- 在线协作
- 私有文档格式
- 完整 WYSIWYG
- Typora 式行内编辑
- AI 功能
- 复杂文件管理器

---

## 16. 产品界面建议

## 16.1 首页

```text
顶部：
应用名称 / 搜索 / 设置

正文：
最近文件
置顶文件
新建文档
打开文件

底部：
最近 / 文件 / 设置
```

首页应尽量简洁，不做 Obsidian 式复杂工作区。

## 16.2 阅读页

```text
顶部：
返回 / 文件名 / 目录 / 编辑 / 更多

正文：
沉浸式单栏文档

底部：
阅读进度或隐藏式工具栏
```

更多菜单：

- 搜索
- 文档主题
- 字体设置
- 分享
- 用其他应用打开
- 文件信息

## 16.3 编辑页

```text
顶部：
返回 / 文件名 / 阅读 / 保存 / 更多

正文：
CodeMirror 编辑器

底部：
Markdown 快捷工具栏
```

---

## 17. 项目最难的部分

项目真正困难的部分并不是 Markdown 解析，而是以下几项。

| 难点 | 原因 |
|---|---|
| 本地图片 | SAF URI 与相对路径不天然兼容 |
| 编辑手感 | 手机输入法、选择区、光标和快捷栏容易出问题 |
| 文件冲突 | 其他应用可能同时修改同一文件 |
| WebView 安全 | 用户 HTML 和应用脚本需要隔离 |
| 长文性能 | 大型文档渲染、搜索和切换要流畅 |
| 主题一致性 | Compose、阅读 CSS、CodeMirror 三套主题需协调 |
| 状态恢复 | 切换模式、后台恢复、崩溃恢复都需稳定 |

---

## 18. 最终判断

这个项目完全可以做好，而且产品边界清晰。

最合理的方向是：

```text
阅读：
Markdown → HTML → CSS → WebView

编辑：
CodeMirror 6 → Markdown 源码

应用外壳：
Kotlin + Jetpack Compose + Material 3

文件：
Storage Access Framework + 标准 Markdown
```

核心不是复制整个 Typora，也不是重新做一个 Obsidian。

真正应该完成的是：

> 用 Material You 做一个现代、原生的安卓外壳，用 Typora 的页面思路做好阅读，用 CodeMirror 做好稳定编辑。

只要优先把以下五项做好，产品就已经有明确价值：

1. 打开文件顺手
2. 阅读排版精致
3. 本地图片可靠
4. 编辑手感稳定
5. 阅读与编辑切换自然

首版不碰知识库、不碰双链、不碰完整所见即所得，反而更容易把产品做得完整、克制且真正好用。
