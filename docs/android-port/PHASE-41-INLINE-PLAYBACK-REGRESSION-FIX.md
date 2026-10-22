# Android 移植阶段四十一：内嵌播放器持续缓冲回归修复

> 个人自用、仅技术验证，不对外分发。

## 问题

部分视频在内嵌播放器播放约 4-5 秒后停住，画面绿屏或黑屏，进度不再增长，表现为一直缓冲。

## 排查结果

日志显示：

- 播放器进入 `BUFFERING`
- `isPlaying=false`
- 解码器 `c2.android.avc.decoder`
- 进度停在约 4-5 秒
- 没有 Activity 生命周期暂停或崩溃

进一步确认，播放结束提示 / A 键确认那次修改只涉及 UI 和提示清理，不会影响网络播放本身。

真正的回归点在内嵌播放链路：

1. 内嵌请求使用 `fnval=1`，拿到的多为 progressive/durl 单文件。
2. 内嵌只使用一个 CDN URL + OkHttpDataSource，没有全屏播放器的 CDN failover。
3. 之前为规避软件解码器绿屏，强制回退 480P；部分视频 480P 更不稳定。
4. 新视频加载期间 `firstFrameRendered` 没有立即重置，6 秒 buffering watchdog 可能误触旧状态。

## 修复

- 内嵌播放请求改为 `fnval=4048`，优先获取 DASH。
- 优先使用 DASH 视频轨 + 音频轨，生成带 SegmentBase 的标准 MPD 播放。
- `MergingMediaSource` 保留为 MPD 生成失败时的兜底。
- 视频轨和音频轨都接入与全屏播放器一致的 CDN failover。
- stall retry 会同步更新 CDN failover 的 preferred index，确保真正切换线路。
- progressive 单文件仅作为 DASH 不可用时的兜底。
- 取消“实际解码器是软件解码就强制回退 480P”的逻辑。
- 无硬件 AVC 时仍由 `InlinePlaybackQualityPolicy` 回退 480P。
- 在新视频 `prepare()` 开始时立即重置首帧状态和 stall watchdog。
- stall watchdog 仅在已准备完成、非后台加载、非用户拖动时触发。

## 验证

- 连续打开多个视频，最长连续播放超过 80 秒。
- 冷启动首个视频也通过 MPD 播放，未再出现固定 4-5 秒卡死。
- 无再次出现 4-5 秒固定卡死。
- 1080P 内嵌播放正常。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
