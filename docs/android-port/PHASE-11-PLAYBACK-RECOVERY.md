# Android 移植阶段十一：弱网播放自动恢复

> 个人自用、仅技术验证，不对外分发。

## 目标

解决网络不稳定时：

- playurl 或 CDN 请求失败
- 播放页一直停在 BUFFERING
- 点击播放后卡死
- 需要手动退出重进才能恢复

## 已有保护

- MediaCodec 异步队列已关闭，避免部分软件/厂商解码器绿屏或首帧不输出
- 解码器回退已开启
- CDN 多地址 failover 已存在
- BiliClient GET 请求已支持最多 3 次重试

## 新增恢复逻辑

### 1. 网络类播放错误自动重试

当 ExoPlayer 报网络连接失败、超时、HTTP 408/425/429/5xx 等错误时：

- 自动保留当前播放进度
- 重新请求 playurl
- 重新设置播放源
- 最多重试 2 次
- 提示“网络不稳定，正在重试播放…”

### 2. 长时间缓冲看门狗

播放状态进入 `BUFFERING` 后启动看门狗：

- 12 秒后仍然缓冲
- 且播放进度没有前进超过 1 秒
- 自动重新获取播放地址并恢复当前进度
- 25 秒内最多触发一次，避免重复刷新
- 连续尝试最多 2 次
- 提示“网络不稳定，正在重新获取播放地址…”

### 3. 播放成功后重置计数

首次正常渲染视频帧后重置网络恢复和缓冲恢复计数，避免一次成功播放后后续偶发问题无法再次自动恢复。

## 相关日志

```text
BLBL/Player: player:networkRecovery
BLBL/Player: player:stallRecovery
```

## 验证

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`

均通过。

正常网络下重新安装验证：

```text
exo:firstFrame pos=0ms
player:state state=READY pos=0ms
```

播放正常，无额外刷新或误触发。
