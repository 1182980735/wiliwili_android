# Android 移植阶段五十一：动态专栏卡片网格与站内弹窗

## 目标

- 动态页“专栏”从单列列表改为卡片网格。
- 点击专栏动态不再跳转系统浏览器，改为应用内弹窗展示。

## 实现

### 网格卡片

- `DynamicFragment.switchContentTab()` 的专栏分支改用 `GridLayoutManager`，与视频动态共用 `spanCountForWidth()`。
- `item_dynamic_article.xml` 重构为纵向卡片：
  - 16:9 封面
  - 两行标题
  - 一行摘要
  - 作者/时间/点赞评论元信息
- 卡片保留焦点缩放、描边、圆角和遥控器焦点样式。

### 站内弹窗

- 移除 `Intent.ACTION_VIEW` 浏览器跳转。
- 新增 `openArticlePopup()`：
  - 使用 `AppPopup.custom` 弹窗
  - 内容为 WebView
  - 支持 JavaScript、DOM Storage、图片和 B 站页面跳转
  - 返回键优先在 WebView 历史内后退
  - 关闭时停止加载并释放 WebView
- 弹窗顶部显示专栏标题，底部提供“关闭”。

## 验证

- 模拟器动态页专栏已显示为 3 列卡片网格。
- 点击专栏卡片后弹出应用内 WebView 窗口，没有拉起系统浏览器。
- B 站页面资源加载正常。
- `assembleDebug` / `testDebugUnitTest` 通过。
- 无 FATAL/ANR。

## 备注

弹窗加载的是 B 站现有 H5 页面，因此复杂专栏、字体、登录态和部分外链行为仍取决于 WebView 与 B 站页面自身。
