# wiliwili Android 0.12

## 版本

- versionName: `0.12`
- versionCode: `12`
- package: `cn.xfangfang.wiliwili.poc`
- 构建类型：debug（固定 debug 签名，可覆盖安装 0.11）

## 更新内容

- 修复 `buvid3` 以 `infoc` 结尾导致的重复指纹刷新
- 软件解码器 480P 卡死时自动回退 360P
- 全屏播放器增加软件解码卡顿回退
- stall watchdog 从 6 秒放宽到 15 秒，减少慢速 CDN 误重试
- 详情页进入后台时释放内嵌播放器，返回时恢复播放位置
- 回归验证进度缩略图预览
- 保持下一个视频优先级：分P/子分段 → 同合集 → 推荐
- 保持电视遥控器/手柄映射与上游 blbl 对齐

## 安装说明

APK 使用项目固定 debug 签名，可直接覆盖安装 0.11。

```text
wiliwili-android-0.12.apk
```

SHA-256：

```text
ecfba2e9caf10c90c6823ebde5af0acc0f6315cd0c68aeb0dae505f3cdc65ad0
```

## 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- 内嵌软件解码 480P 卡死后自动 360P 恢复
- 全屏从正常播放状态进入可持续播放
- 缩略图预览可见
- 无 `FATAL EXCEPTION` / ANR / `ExoPlaybackException`
