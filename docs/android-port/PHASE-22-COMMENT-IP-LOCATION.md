# Android 移植阶段二十二：评论 IP 属地

> 个人自用、仅技术验证，不对外分发。

## 目标

对齐 wiliwili 评论区的时间与 IP 属地展示：

```text
20小时前  IP属地：天津
```

## 实现

- `VideoCommentItem` 新增 `ipLocation` 字段
- 从 B 站评论接口的 `reply_control` 对象中读取 `location`
- 兼容旧格式 `reply_control` 字符串中的 `location:` 字段
- 评论布局在时间后新增 `tv_ip_location`
- 只有接口返回 IP 属地时才显示，避免空占位
- 根评论和回复线程共用同一套解析逻辑

## 验证

实测评论区显示：

```text
2026.10.6 IP属地：北京
2026.10.4 IP属地：天津
2026.10.2 IP属地：贵州
```

- 评论时间和点赞数布局正常
- 无 IP 数据时不显示空标签
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
