# Android 移植阶段三十：聊天发送、内嵌播放器 OSD、自适应画质与动态专栏

> 个人自用、仅技术验证，不对外分发。

## 1. 聊天文本发送

- 新增 `MessageCenterApi.sendChatMessage(talkerId, text)`
- 调用 B 站 `web_im/v1/web_im/send_msg`
- 复用当前 Cookie 的 `DedeUserID` 和 `bili_jct`
- 聊天详情底部新增输入框和发送按钮
- 支持 IME 发送、Enter 发送和遥控器焦点操作
- 发送成功后本地追加消息并滚动到底
- 发送失败显示错误提示

## 2. 内嵌播放器 OSD

新增按钮：

- 音量：滑杆直接控制 ExoPlayer volume
- 倍速：0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x
- 弹幕开关：即时显示或隐藏弹幕层
- 设置：打开现有设置页
- 播放列表：当前分P/合集列表，选择后内嵌切换
- 视频数据：分辨率、编码、帧率、播放状态、时间、缓冲、画质、解码器、倍速、音量、CDN host

原有的播放/暂停、进度、时间、画质、全屏、首帧封面兜底和 CDN 重试保持不变。

## 3. 自适应画质

新增 `InlinePlaybackQualityPolicy`：

- 检测设备是否有硬件 AVC 解码器
- 有硬件 AVC：使用用户默认/选择的画质
- 无硬件 AVC：回退 480P AVC
- 请求始终优先 AVC/H.264
- 画质切换保留当前进度和播放状态

## 4. 动态页专栏

- 第二 Tab 从“综合”改为“专栏”
- 接入动态桌面 feed 接口：
  `x/polymer/web-dynamic/desktop/v1/feed/all`
- 兼容 `modules` 数组结构
- 解析动态作者、时间、正文、封面、点赞和评论数
- 新增专栏卡片布局和 `DynamicArticleAdapter`
- 点击卡片打开对应动态文章链接
- 视频 Tab 与专栏 Tab 可切换，分别保留各自列表和分页状态

## 验证

- 聊天输入栏和发送按钮正常显示
- 内嵌播放器 OSD 显示音量、倍速、弹幕、设置、播放列表、视频数据
- 视频数据面板显示 1280x720 / AVC / 1080P qn80 / 缓冲进度 / CDN host
- 动态页第二 Tab 显示“专栏”并加载真实动态卡片
- 视频播放正常，无绿屏
- 无 `FATAL EXCEPTION`
- `assembleDebug` 和 `testDebugUnitTest` 通过

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
