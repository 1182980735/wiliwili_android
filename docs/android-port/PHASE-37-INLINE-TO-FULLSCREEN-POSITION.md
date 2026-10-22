# Android 移植阶段三十七：内嵌播放切全屏继承当前进度

> 个人自用、仅技术验证，不对外分发。

## 问题

视频详情页窗口化内嵌播放到一半，点击“全屏”进入 `PlayerActivity` 时从头开始播放。

## 原因

`VideoDetailActivity.playCurrentFromHeader()` 创建全屏播放 Intent 时只传了：

- bvid
- cid
- aid
- playlist token/index

没有传 `PlayerActivity.EXTRA_START_POSITION_MS`，所以全屏播放器默认从 0 开始。

## 修复

- 打开全屏前读取 `VideoInlinePlayerController.currentPositionMs()`。
- 将当前进度写入 `PlayerActivity.EXTRA_START_POSITION_MS`。
- 仅当进度大于 0 时传递，避免 0 值覆盖播放器默认行为。
- 分P、合集和播放列表 token 仍按原逻辑传递。

## 验证

- 内嵌播放器进度约 0:05 时点击全屏。
- 全屏播放器启动后时间轴从接近 0:05 的位置开始，并继续播放。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
