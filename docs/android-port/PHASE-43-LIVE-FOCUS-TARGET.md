# Android 移植阶段四十三：修复直播页焦点无法进入内容

> 个人自用、仅技术验证，不对外分发。

## 问题

首页切换到“直播”Tab 后，摇杆/方向键无法把焦点从顶部 Tab 移到直播内容区。其他首页 Tab 正常。

## 原因

`HomeFragment` 的顶部 Tab 在处理 `DPAD_DOWN` 时，会查找当前页面是否实现：

```text
TabContentFocusTarget
```

但 `LiveFragment` 只实现了：

```text
LiveGridTabSwitchFocusHost
TabContentSwitchFocusHost
```

没有实现 `TabContentFocusTarget`，因此查找返回 null，焦点无法交给直播页内容。

## 修复

让 `LiveFragment` 实现 `TabContentFocusTarget`：

- `requestFocusPrimaryItemFromTab()`
- `requestFocusPrimaryItemFromContentSwitch()`
- `requestFocusPrimaryItemFromBackToTab0()`

分别委托给直播页已有的：

- `focusCurrentPageFirstCardFromTab()`
- `requestFocusCurrentPageFirstCardFromContentSwitch()`

保留子 Tab、分区和返回焦点逻辑。

## 验证

- 登录状态下直播页第一个子 Tab 为“关注”。
- 顶部直播 Tab 获得焦点后，按方向键下可进入直播卡片区域。
- 左右方向键可在直播卡片之间移动。
- `assembleDebug` 和 `testDebugUnitTest` 通过。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
