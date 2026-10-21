# Android 移植阶段六：视频花屏/绿屏/卡死修复

> 个人自用、仅技术验证，不对外分发。

## 现象

打开部分 B 站视频后：

- 画面整屏绿色
- 中间一直显示“加载中”
- 播放器状态长期停在 `BUFFERING`
- 没有正常首帧输出

## 定位

复现日志关键信息：

```text
DMCodecAdapterFactory: Creating an asynchronous MediaCodec adapter for track type video
CCodec: allocate(c2.android.avc.decoder)
...
BLBL/Player: player:state state=BUFFERING pos=0ms
BLBL/Player: enhance:awaitFirstFrame
```

说明视频进入软件 AVC 解码器后，异步 MediaCodec 出队路径没有正常输出首帧，
播放器一直停在缓冲状态，画面呈现绿色。

## 修复

修改 `BlblRenderersFactory`：

```kotlin
init {
    // 部分软件/厂商解码器在异步 MediaCodec 队列模式下会输出绿帧
    // 或一直不交付首帧。
    forceDisableMediaCodecAsynchronousQueueing()
    setEnableDecoderFallback(true)
}
```

改动文件：

```text
android/app/src/main/java/blbl/cat3399/feature/player/engine/ExoPlayerEngine.kt
```

说明：

- 强制使用同步 MediaCodec 队列，优先保证兼容性和首帧可靠性
- 开启解码器回退，当前解码器不支持时允许 Media3 尝试其他候选
- 未改变清晰度、编码偏好、DASH 解析或播放器 UI 逻辑

## 验证

同一环境下分别测试两种渲染视图：

### SurfaceView

```text
exo:firstFrame pos=0ms
player:state state=READY pos=0ms
```

画面正常显示，不再是绿色，加载层消失。

### TextureView

同样正常输出首帧并进入 `READY`，未再出现绿屏或无限加载。

### 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过，无 `FATAL EXCEPTION`。

## 备注

同步 MediaCodec 队列可能在极低端设备上带来轻微性能差异，但当前验证优先解决
绿屏、首帧不输出和播放卡死问题。后续如发现性能问题，可按设备/解码器类型做条件开关。
