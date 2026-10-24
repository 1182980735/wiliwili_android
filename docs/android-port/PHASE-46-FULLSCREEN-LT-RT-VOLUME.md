# Android 移植阶段四十六：全屏播放器 LT/RT 改为音量调节

> 个人自用、仅技术验证，不对外分发。

## 修改

全屏 `PlayerActivity` 手柄映射：

- `LT`：音量 -5%
- `RT`：音量 +5%

同时：

- 从 `L1/L2` 快退分组中移除 `L2`
- 从 `R1/R2` 快进分组中移除 `R2`
- 保留 `LB/RB` 原有快退/快进行为
- 音量调整时显示“音量 XX%”提示

## 实现

在 `BlblPlayerEngine` 增加统一音量属性：

```kotlin
var volume: Float
```

实现：

- `ExoPlayerEngine`：映射到 `exoPlayer.volume`
- `IjkPlayerEngine`：映射到 `IjkMediaPlayer.setVolume(left, right)`

`PlayerActivity.dispatchKeyEvent()` 在弹窗处理之后优先捕获：

```kotlin
KeyEvent.KEYCODE_BUTTON_L2 -> adjustPlayerVolume(-0.05f)
KeyEvent.KEYCODE_BUTTON_R2 -> adjustPlayerVolume(0.05f)
```

## 验证

- 全屏播放器按 LT：音量 100% → 95%
- 按 RT：音量 95% → 100%
- 显示音量提示
- 无 `FATAL EXCEPTION`
- `assembleDebug` 和 `testDebugUnitTest` 通过

## 发布说明

本次只提交代码，不创建 GitHub Release，也不上传 APK。
