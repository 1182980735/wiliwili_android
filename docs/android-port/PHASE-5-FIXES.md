# Android 移植阶段五：侧栏对齐与我的投稿

> 个人自用、仅技术验证，不对外分发。

## 1. 侧栏消息/设置图标对齐

问题：消息齿轮图标看起来向左偏，和设置图标没有对齐。

原因：原 `wiliwili_ico_inbox` 的 SVG viewport 为 28，但实际路径只占约 x=1..19，
在 ImageView 的 fitCenter 下形成左侧偏移。

处理：

- 新增居中版本 `ic_wiliwili_inbox_centered.xml`
- viewport 改为 20×20，使路径几何在 View 中居中
- 侧栏消息按钮改用该资源

真机截图确认两个图标已基本垂直对齐。

## 2. 我的页新增“我的投稿”

- 在 My Tab 列表中新增 `KEY_UPLOAD`
- 顺序：历史记录、我的投稿、我的收藏、我的追番、我的追剧、稍后再看、最近点赞
- 新增 `MyUploadsFragment`
- 数据来源：`BiliApi.spaceArcSearchPage(mid, pn=1, ps=30, order=pubdate)`
- 复用统一视频卡片、长按动作、详情页和播放器入口
- 支持刷新、返回、Tab 焦点和 D-pad 焦点恢复

## 3. 验证

- `./gradlew assembleDebug`：通过
- 真实登录账号下点击“我的投稿”：成功加载当前账号投稿视频
- 消息/设置图标视觉对齐
- 底部提示栏、浅色主题和强制横屏保持正常
- 无 `FATAL EXCEPTION`

## 4. 投稿分页策略

- 投稿页已接入分页加载：首屏 30 条，触底后按页请求下一页，加载中防重入，到底停止。
- 滚动和 D-pad 到网格底部都会触发下一页加载。
- 已实测 page=2、page=3 连续加载，单页 30 条，接口返回 total=230、hasMore=true。
