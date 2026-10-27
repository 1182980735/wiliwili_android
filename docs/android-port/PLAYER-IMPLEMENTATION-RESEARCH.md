# Android 播放器实现调研与重构记录

## 调研结论

### PiliPlus

PiliPlus 的 Android 播放器基于 libmpv，硬件解码策略使用：

```text
mediacodec, auto-safe
```

播放器会启动硬件解码，失败后由 MPV 自行回退，不会因为一次卡顿直接修改用户选择的默认画质。

### ExoPlayer / Media3 主流做法

ExoPlayer 项目通常使用以下能力：

- `DefaultRenderersFactory.setEnableDecoderFallback(true)`
- 硬件解码器优先的 `MediaCodecSelector`
- 对部分设备禁用异步 MediaCodec 队列，避免绿屏和首帧不输出
- 通过 CDN/媒体源重试解决网络问题
- 解码器确实不支持时再降级，而不是播放卡顿就降画质

## 本项目原来的问题

1. 内嵌播放器直接 `ExoPlayer.Builder(activity).build()`，没有使用全屏播放器已有的 renderer 配置。
2. 内嵌播放器自行生成 MPD，而全屏使用合并音视频源，两条路径行为不一致。
3. 软件解码 480P/1080P 卡顿时自动降 360P，导致用户默认画质越来越低。

## 本轮重构

- 内嵌播放器改用硬件优先的 `MediaCodecSelector`。
- 启用 `setEnableDecoderFallback(true)`。
- 禁用 MediaCodec 异步队列，降低绿屏/首帧异常概率。
- 内嵌播放器改用与全屏一致的合并音视频源，不再自行生成 MPD。
- 移除“卡顿就自动降 360P/480P”的逻辑。
- 恢复尊重用户“默认画质”设置。
- 详情页进入后台时释放内嵌播放器，减少全屏与内嵌同时占用解码器。

## 当前验证与限制

- `assembleDebug`、`testDebugUnitTest` 通过。
- 在 MuMu Android 12 模拟器上，硬件优先后实际选择 `c2.qti.avc.decoder`。
- 部分模拟器视频仍会在 4-5 秒进入 BUFFERING，说明模拟器/厂商解码器或特定媒体流仍有兼容问题；这不能再通过降低默认画质掩盖。
- 真实比亚迪车机需要验证其硬件 AVC 解码器、音频输出和 CDN 网络。

## 后续建议

1. 在真实 BYD 车机或电视盒子上验证 1080P/720P。
2. 如果仍失败，增加 IjkPlayer/FFmpeg 作为**同一画质**的兜底内核，而不是降分辨率。
3. 保留手动画质选择，允许用户针对车机选择 720P。
