# Android 移植阶段二十六：聊天视频消息卡片

> 个人自用、仅技术验证，不对外分发。

## 目标

聊天会话中双方发送的视频消息显示为视频卡片，点击可跳转到对应视频详情页。

## 接口

B 站私信视频消息为：

```text
msg_type = 11
```

`content` JSON 字段：

```json
{
  "title": "视频标题",
  "cover": "封面地址",
  "bvid": "BV号",
  "rid": 117483813996232,
  "times": 1211,
  "view": 2794,
  "danmaku": 10,
  "desc": "视频简介",
  "pub_date": 1792661109
}
```

## 实现

- `ChatMessageItem` 新增 `ChatVideoCard?`
- 解析视频消息的：
  - BVID / AID
  - 标题
  - 封面
  - 时长
  - 播放量
  - 弹幕数
  - 简介
- 聊天列表新增视频卡片：
  - 左侧封面
  - 右下角时长
  - 右侧标题
  - 简介 / 播放量 / 弹幕数
  - 自己的视频消息右对齐，对方视频消息左对齐
- 点击卡片跳转 `VideoDetailActivity`
  - 优先传入 BVID
  - BVID 缺失时使用 AID
- 封面缺失时显示默认播放占位，避免纯黑块

## 验证

- 视频消息显示为视频卡片
- 卡片显示封面、时长、标题和统计数据
- 点击卡片进入对应视频详情页
- 缺失封面的消息显示播放占位
- 普通文本消息仍显示气泡
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
