# Android 移植阶段三十九：清理播放结束提示、详情页 A 键改为确认

> 个人自用、仅技术验证，不对外分发。

## 1. 播放结束提示残留

### 问题

全屏播放结束且播放模式为“什么都不做”时，会显示：

`播放结束 · 按确认重播`

之后直接切到下一个视频，旧提示仍然留在左下角。

### 原因

结束提示使用 `showSeekHint(..., hold = true)` 持续显示，只在新提示覆盖它或用户重播时清除；开始下一个视频时没有显式清理。

### 修复

- 提取结束提示常量 `PLAYBACK_ENDED_HINT`。
- 新增 `clearPlaybackEndedHint()`：仅当当前提示文本仍是结束提示时隐藏。
- 在 `startPlayback()` 开始处调用。
- 覆盖手动下一集、分P、合集、推荐以及自动连播等所有新播放链路。
- 不会误清除自动连播、空降助手等其他提示。

## 2. 详情页 A 键改为确认

### 调整

视频详情页底栏：

```text
旧：A 全屏
新：A 确认
```

按键行为：

- `KEYCODE_A` / `KEYCODE_BUTTON_A` 不再强制打开全屏播放器。
- 改为确认当前焦点项目：
  - 播放按钮
  - UP 主
  - 点赞/投币/收藏/分享
  - 右侧 Tab 和列表项
  - 其他可点击控件
- 进入全屏仍可通过内嵌播放器右侧的“全屏”按钮完成。
- 底栏 `footerConfirm` 点击也走统一确认逻辑。

## 验证

- 详情页底栏显示“A 确认”。
- 按 A 后仍停留在视频详情页，不再跳转 `PlayerActivity`。
- 播放结束提示清理代码已接入所有 `startPlayback()` 新视频入口。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
