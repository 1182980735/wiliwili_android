# Android 移植阶段四十九：内嵌播放器引擎抽象与 IjkPlayer 兜底

> 个人自用、仅技术验证，不对外分发。

## 目标

1. 内嵌播放器从直接使用 ExoPlayer 改为 `BlblPlayerEngine`。
2. 增加独立 TextureView 渲染容器，供 IjkPlayer 使用。
3. ExoPlayer 失败时自动切 IjkPlayer，保持用户画质。
4. 把 IjkPlayer 四套 ABI 的 `libijkplayer.so` 打进 APK。
5. 进行硬解、软解、音频焦点和 CPU 的模拟器验证；BYD 实机验证待完成。

## 实现

### 引擎抽象

`VideoInlinePlayerController` 现在持有：

```text
BlblPlayerEngine
  ├─ ExoPlayerEngine（默认）
  └─ IjkPlayerEngine（失败兜底 / 手动选择）
```

播放源统一通过 `PlaybackSource.Vod` 设置，不再由内嵌控制器自行生成 MPD。

### 渲染容器

- `item_video_detail_header.xml` 保留 ExoPlayer 的 `PlayerView`。
- 新增 `inline_ijk_surface`：`TextureView`。
- ExoPlayer 时显示 `PlayerView`，IjkPlayer 时显示 `TextureView`。
- Ijk 视频尺寸变化时按比例缩放，避免拉伸。

### 自动兜底

触发条件：

- ExoPlayer 报播放错误；或
- ExoPlayer 连续卡顿重试后仍未恢复。

切换时：

- 保持当前 qn 和播放位置；
- 释放 ExoPlayer；
- 创建 `IjkPlayerEngine(forceSoftwareDecode = true)`；
- 使用同一 `Playable.Dash` 继续播放；
- 不降低清晰度。

### Ijk 原生库

- `app/build.gradle.kts` 不再排除 `**/libijkplayer.so`。
- APK 已包含：
  - `arm64-v8a`
  - `armeabi-v7a`
  - `x86`
  - `x86_64`
- `IjkPlayerPlugin` 优先使用 APK 内置 `.so`，运行时下载仅作为旧包兼容路径。
- APK 体积从约 15 MB 增加到约 33 MB。

### 音频焦点

- ExoPlayer：设置 `USAGE_MEDIA / CONTENT_TYPE_MOVIE`，启用 Media3 音频焦点管理。
- IjkPlayer：增加 `AudioFocusRequest`，失焦暂停、duck 降音量、重新获得焦点恢复音量。

## 模拟器验证

设备：MuMu Android 12，x86_64，系统 AVC 解码器 `c2.android.avc.decoder` / `c2.qti.avc.decoder`。

| 场景 | 结果 |
|---|---|
| ExoPlayer 主路径 | 实际选择 `c2.qti.avc.decoder`，480P 连续播放约 38 秒 |
| ExoPlayer 连续卡死 | 自动切换 IjkPlayer，日志 `switch to IjkPlayer fallback` |
| Ijk 软解 | `mediacodec-avc = 0`，`MEDIA_INFO_VIDEO_RENDERING_START`，持续播放超过 16 秒 |
| 音频焦点 | ExoPlayer 与 IjkPlayer 均在 `dumpsys audio` 中持有 `USAGE_MEDIA / CONTENT_TYPE_MOVIE` 的 GAIN |
| CPU | Ijk 480P 软解约 36% 整机 CPU（4 核模拟器，约 1.4 核） |
| Surface 重绑定 | 修复为仅绑定 1 次，不再每 500ms 调用 `setVideoSurface` |
| 崩溃 | 无 `FATAL EXCEPTION` / ANR |

## 尚未完成

- 未在真实比亚迪车机上验证。
- 未验证车机导航播报、电话、倒车影像等音频焦点抢占场景。
- 未验证车机硬件 AVC 解码器在 1080P 下的持续稳定性。
- 未发布新 Release。

## 结论

代码路径已经完成，模拟器验证通过；BYD 实机验证需要设备可用后再做。
