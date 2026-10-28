# wiliwili Android 0.13

## 版本

- versionName: `0.13`
- versionCode: `13`
- package: `cn.xfangfang.wiliwili.poc`
- 构建类型：debug（固定 debug 签名，可覆盖安装 0.12）
- APK 大小：约 32 MB

## 核心更新

- 内嵌播放器抽成 `BlblPlayerEngine` 接口
- 默认 ExoPlayer 硬件解码优先
- ExoPlayer 报错或连续卡顿后自动切 IjkPlayer 同画质兜底
- 内嵌播放器增加 TextureView 渲染容器，支持 IjkPlayer Surface 渲染
- Ijk 视频按比例缩放，避免拉伸
- 四套 ABI 的 `libijkplayer.so` 打进 APK，不再依赖运行时下载
- Ijk 兜底强制 FFmpeg 软解，保持原画质
- ExoPlayer 与 IjkPlayer 增加音频焦点管理
- 取消自动降 360P/480P 的逻辑，恢复尊重用户默认画质
- 保持下一个视频优先级：分P/子分段 → 同合集 → 推荐
- 保持进度缩略图预览、buvid3 infoc 修复

## 回归结果

- ExoPlayer 硬件解码主路径：480P 连续播放约 38 秒
- ExoPlayer 卡死：自动切 IjkPlayer，日志 `switch to IjkPlayer fallback`
- Ijk 软解：`mediacodec-avc = 0`，`MEDIA_INFO_VIDEO_RENDERING_START`，持续播放超过 16 秒
- 音频焦点：ExoPlayer / IjkPlayer 均持有 `USAGE_MEDIA / CONTENT_TYPE_MOVIE` GAIN
- Ijk 480P 软解 CPU：约 36% 整机 CPU（4 核模拟器）
- `FATAL EXCEPTION`：0
- ANR：0
- `assembleDebug` / `testDebugUnitTest`：通过

## 尚未验证

- 真实比亚迪车机的 1080P 硬解稳定性
- 导航播报、电话、倒车影像抢占音频焦点
- Ijk 软解的长期 CPU、发热和掉帧

APK SHA-256：

```text
855a29bd4727759fcf099a260e6e1caa9b6ddd16d645d596b648b29f6f605bc9
```
