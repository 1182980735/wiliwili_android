# Android 移植阶段五十四：视频详情页下载按钮

## 功能

在视频详情页操作栏“分享”右侧新增“下载”按钮。

点击后：

1. 获取当前视频可用清晰度
2. 弹出清晰度选择
3. 复用播放器下载链路：
   - `DownloadRequestResolver.resolve()`
   - `DownloadService.enqueue()`
4. 加入下载队列并显示提示

## 兼容

- 视频详情页显示下载按钮
- 番剧详情页未提供下载回调时按钮自动隐藏
- 复用已有 DASH 音视频合并下载和下载记录页，不新增第二套下载实现

## 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- 无 FATAL / ANR
