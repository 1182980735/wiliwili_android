# Android 移植阶段三十八：内嵌播放器停滞自动重试

> 个人自用、仅技术验证，不对外分发。

## 实际现象

联调时内嵌播放器在约 0:05 处停住，画面为绿帧，OSD 播放进度不再增长。

日志确认：

- 视频解码器为 `c2.android.avc.decoder`
- 播放状态进入 `BUFFERING`
- `isPlaying` 变为 `false`
- 进度停在约 5251ms
- 没有 Activity 生命周期切换或手动暂停

## 原因

现有首帧 watchdog 只在 `onRenderedFirstFrame()` 之前生效：

- 首帧未显示时 8 秒切换 CDN
- 首帧显示后 watchdog 立即取消

因此播放中途遇到 CDN/解码缓冲卡住时，没有第二次重试机制，画面会停留在最后一个绿色缓冲帧。

## 修复

新增播放停滞 watchdog：

- 在 `updateProgress()` 中追踪 `currentPosition` 是否持续增长。
- 当用户仍处于播放意图、播放状态为 `STATE_BUFFERING`，且进度超过 6 秒没有增长时：
  - 自动切换下一个候选 CDN URL
  - 从当前进度重新 prepare
  - 恢复播放
- 最多连续重试 4 次。
- 一旦进度重新增长，重试计数清零。
- 保留原有软件解码器自动回退 480P 逻辑。

## 验证

- 重新打开视频并强制播放。
- 内嵌播放器连续播放超过 23 秒，时间轴从 0:00 增长到 0:23。
- 480P 画面正常，无绿屏。
- 全屏切换继续从内嵌当前进度开始。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
