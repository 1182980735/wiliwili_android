# Android 移植阶段三十三：详情页手柄映射对齐 wiliwili 原生方案

> 个人自用、仅技术验证，不对外分发。

## 映射依据

wiliwili 的详情页由 `VideoView` 与 `BasePlayerActivity` 注册控制器动作：

- `LB`：快退
- `RB`：快进
- `LT` / `LB`：切换上一个右侧 Tab
- `RT` / `RB`：切换下一个右侧 Tab
- `Y`：显示/隐藏 OSD
- `X`：切换弹幕；焦点在评论列表时切换评论排序
- `RT + 方向键上/下`：音量加/减
- `START`：画质菜单
- `A`：非全屏进入全屏；全屏时切换播放/显示 OSD
- `B`：返回/退出全屏/关闭 OSD
- `BACK`：视频数据面板（wiliwili `BUTTON_BACK` profile）

## Android 新映射

详情页：

- `LB/RB`：焦点在左侧播放区域时快退/快进；焦点在右侧列表时切换 Tab
- `LT/RT`：始终切换右侧 Tab，支持循环
- `X`：焦点在评论列表时切换热门/最新排序，否则切换弹幕
- `Y`：显示/隐藏内嵌播放器 OSD
- `START`：打开画质菜单
- `A`：进入全屏播放
- `B`：返回/关闭弹层
- `SELECT`：打开内嵌播放器视频数据面板
- `RT + 方向键上/下`：调节内嵌播放器音量

快退快进步长按 wiliwili `getSeekRange` 规则：

- 0-30 秒：5 秒
- 31-60 秒：15 秒
- 61-300 秒：30 秒
- 301-1200 秒：60 秒
- 超过 1200 秒：当前进度/20

## 画质稳定性补充

联调发现模拟器部分 1080P 视频会绿屏，但手动切 480P 正常。

新增实际解码器检测：

- ExoPlayer `onVideoDecoderInitialized` 获取真实解码器名称
- 若当前画质高于 480P 且实际使用软件解码器：
  - 自动回退到 480P AVC
  - 保持当前播放位置和播放状态
  - 提示“当前使用软件解码器，已自动回退 480P”

实机验证：自动回退后画面正常，画质标签显示 480P。

## 验证

- `LT` 在评论页循环切换到“投稿”
- `RT` 切回“评论”
- `X` 成功切换弹幕并显示提示
- `START` 成功打开画质菜单
- 1080P 绿屏视频自动回退 480P 后正常播放
- `assembleDebug` 和 `testDebugUnitTest` 通过
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
