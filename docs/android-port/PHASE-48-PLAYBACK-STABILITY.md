# Android 移植阶段四十八：播放稳定性回归修复

> 个人自用、仅技术验证，不对外分发。

## 问题

视频播放出现绿屏/黑屏、进度停在 4-6 秒、持续转圈。日志有时没有 HTTP 错误，只有：

```text
playbackState=BUFFERING
isPlaying=false
playWhenReady=true
```

## 排查结论

1. `buvid3` 为 `...infoc` 时，旧逻辑会反复触发指纹刷新，播放请求被拖住。
2. 模拟器/软件解码器条件下，480P AVC 部分视频会在 4-6 秒卡死；360P 可稳定播放。
3. 6 秒 stall watchdog 过于激进，会把仍在下载的慢流误判为卡死并重置，导致越重试越卡。
4. 详情页进入全屏后，后台内嵌播放器仍占着解码器/音频资源，可能干扰全屏播放。

## 修复

### buvid3

- `infoc` 结尾时不再依赖 SPI 返回，直接生成 32 位随机 hex `buvid3`。
- 避免每 30 秒重复刷新指纹。
- 单元测试覆盖 `infoc` 判断和随机值格式。

### 软件解码器回退

- 内嵌播放器检测到实际解码器为软件 AVC 且 480P 播放卡死时，自动重载 360P。
- 全屏播放器同样在软件解码器卡顿时回退 360P。
- 只回退一次，硬件解码器不触发。

### Watchdog

- 普通 stall watchdog 从 6 秒放宽到 15 秒。
- 软件解码器 360P 回退仍保留 6 秒快速响应。
- 避免慢速 CDN 正常下载被反复中断。

### 全屏资源释放

- 详情页 `onPause` 时释放内嵌 ExoPlayer。
- 返回详情页时按上次位置重新准备，避免全屏和内嵌两个播放器同时占用解码资源。

### 下一视频优先级

保持不变并已验证：

```text
分P/子分段 -> 同合集 -> 推荐
```

## 回归结果

- 内嵌 480P：软件解码卡在约 5.8 秒 → 自动切 360P → 恢复并持续播放超过 13 秒
- 全屏从正常播放状态进入：持续播放超过 25 秒
- 进度拖动缩略图预览：`videoShot:done ok=true`，实机截图可见
- `buvid3`：修复后日志为 `needHomepage=false needSpi=false`，不再循环刷新
- `FATAL EXCEPTION`：0
- ANR：0
- `ExoPlaybackException`：0
- `assembleDebug` / `testDebugUnitTest` 通过

## 发布说明

本次发布 `0.12`。
