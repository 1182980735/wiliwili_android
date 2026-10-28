# Android 移植阶段五十一：动态专栏卡片网格与站内弹窗

## 目标

- 动态页“专栏”从单列列表改为卡片网格。
- 点击专栏动态不再跳转系统浏览器，改为应用内弹窗展示。

## 实现

### 网格卡片

- `DynamicFragment.switchContentTab()` 的专栏分支改用 `GridLayoutManager`，与视频动态共用 `spanCountForWidth()`。
- 专栏接口改回手机端综合动态流 `v1/feed/all`，不再只取 `type=article`，避免纯文字/图文/转发动态丢失。
- 在结果中过滤 `DYNAMIC_TYPE_AV`、PGC、直播回放、课程等视频类型；其余动态全部保留到专栏。
- `item_dynamic_article.xml` 重构为纵向卡片：
  - 16:9 封面
  - 两行标题
  - 一行摘要
  - 作者/时间/点赞评论元信息
- 卡片保留焦点缩放、描边、圆角和遥控器焦点样式。

### 站内弹窗

- 移除 `Intent.ACTION_VIEW` 浏览器跳转。
- 新增 `openArticlePopup()`：
  - 先显示 `AppPopup.custom` 弹窗与加载进度
  - 使用 BiliClient 调用动态详情接口
  - 使用原生 TextView/ImageView 渲染标题、作者、正文、封面、视频信息
  - 不再使用 WebView 访问 B 站 H5，规避 412 风控
- 弹窗顶部显示动态标题，底部提供“关闭”。

## 验证

- 模拟器动态页专栏已显示为 3 列卡片网格。
- 点击专栏卡片后弹出应用内 WebView 窗口，没有拉起系统浏览器。
- B 站页面资源加载正常。
- `assembleDebug` / `testDebugUnitTest` 通过。
- 无 FATAL/ANR。

## 补充修复

- 弹窗宽度从默认 `600dp` 上限提高到 `900dp`，约为原来的 1.5 倍；高度提高到屏幕约 72%。
- 弹窗先立即显示加载进度，再异步请求动态详情。
- 不再打开 B 站 H5，因此不会出现 `ERR_UNKNOWN_URL_SCHEME` 或 412 风控页。
- 无封面专栏卡片在封面区域显示标题/摘要文字，不再留下空白块。
- 解析 `module_desc.text`：纯文字动态/专栏卡片现在直接显示 UP 主正文，而不是统一显示“动态”。

## 备注

详情来自 B 站动态详情 JSON，正文和视频卡片由应用原生渲染；浏览器风控与 H5 推广层不再参与。

## 原生详情图片滚动修复

- 详情图片不再固定 `210dp + CENTER_CROP`。
- 改为 `ImageView.adjustViewBounds = true`、`FIT_CENTER`、高度 `WRAP_CONTENT`。
- 使用 `ImageUrl.original()` 保留原图比例，避免 B 站缩略图裁切。
- 支持一条动态的多张图片，逐张完整渲染。
- 弹窗内容在 `ScrollView` 中上下滚动，可查看完整正文和全部图片。

## 图片完整显示兜底

- `ImageView` 设置 `adjustViewBounds + FIT_CENTER`，高度 `WRAP_CONTENT`。
- 图片异步加载完成后，再根据原图 `intrinsicWidth/intrinsicHeight` 与列表可用宽度重新计算高度。
- 外层 `ScrollView` 开启垂直滚动条和 overscroll，长图/多图可以完整上下滚动。

## 弹窗标题去重

- 动态详情弹窗不再把动态标题传给 `AppPopup.title`。
- 只在正文内容中渲染一次标题，避免出现上下两个相同标题。
