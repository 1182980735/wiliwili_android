# Android 移植阶段十七：消息中心

> 个人自用、仅技术验证，不对外分发。

## 界面

参考 wiliwili 消息中心，实现右侧抽屉式消息面板：

- 左侧主页面变暗
- 右侧白色圆角面板覆盖
- 顶部四个 Tab：
  - 聊天列表
  - 回复我的
  - @我的
  - 收到的赞
- 右上角关闭按钮
- 点击左侧遮罩或返回键关闭
- 消息列表包含头像、标题、摘要、时间和未读红点

## 接口

新增 `MessageCenterApi`，接入 B 站消息接口：

- 聊天列表：`session_svr/v1/session_svr/get_sessions`
- 用户头像/昵称：`account/v1/user/cards`
- 回复我的：`x/msgfeed/reply`
- @我的：`x/msgfeed/at`
- 收到的赞：`x/msgfeed/like`

## 消息摘要

- 聊天消息：解析 `last_msg.content`，视频消息提取标题、BVID 和封面信息
- 回复：显示“回复了我的评论”、视频标题和评论内容
- @：显示“提到了我”和目标内容
- 点赞：显示点赞用户、人数和对应内容标题

## 点击行为

- 包含 BVID 的消息：进入视频详情页
- 其他外部 URI：交给系统打开
- 纯聊天会话：提示“聊天详情暂未接入”

## 验证

- 侧栏消息按钮不再弹“暂未接入”，而是打开消息中心
- 聊天列表加载 20 条会话
- 回复我的加载 20 条
- @我的加载 20 条
- 收到的赞加载 20 条
- 四个 Tab 全部可见
- 头像、昵称、时间、未读红点正常
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。

## 遮罩层修正

消息中心改为真正的浮动透明窗口：

- Activity 使用 `windowIsFloating + windowIsTranslucent`
- 窗口背景强制透明
- 关闭系统窗口 dim
- 根布局只绘制半透明遮罩和右侧消息面板
- 底层 MainActivity 内容保持可见并被遮罩压暗
- 点击左侧遮罩可关闭消息中心
