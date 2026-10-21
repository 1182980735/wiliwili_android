# Android 移植阶段八：底部 X/Y/A/B 快捷操作

> 个人自用、仅技术验证，不对外分发。

## 功能

底部提示栏新增四个可点击、可获得焦点的按钮：

| 按键 | 功能 |
| --- | --- |
| X 刷新 | 刷新当前页面 |
| Y 搜索 | 打开搜索页 |
| B 返回 | 执行返回 |
| A 确定 | 点击当前焦点项 |

## 实现

- `activity_main.xml` 将原纯文本提示替换为 `footer_refresh`、`footer_search`、
  `footer_back`、`footer_confirm` 四个按钮。
- 新增 `bg_wiliwili_footer_key.xml`，提供普通、按下、焦点三种状态。
- `MainActivity` 增加点击处理：
  - X 调用 `dispatchRefreshKeyToCurrentPage()`
  - Y 调用 `openRootNavById(SidebarNavAdapter.ID_SEARCH)`
  - B 调用 `onBackPressedDispatcher.onBackPressed()`
  - A 调用 `confirmFocusedItem()`，对当前焦点 View 执行 `performClick()`
- `dispatchKeyEvent` 同时支持手柄/键盘：
  - `KEYCODE_X` / `KEYCODE_BUTTON_X`
  - `KEYCODE_Y` / `KEYCODE_BUTTON_Y`
  - `KEYCODE_A` / `KEYCODE_BUTTON_A`
  - `KEYCODE_B` / `KEYCODE_BUTTON_B`
- 正在输入搜索文本时，字母 X/Y/A/B 不拦截，避免影响输入。

## 验证

Logcat 实测点击：

```text
BLBL/Footer: tap refresh
BLBL/Footer: tap search
BLBL/Footer: tap back
BLBL/Footer: tap confirm
```

- X 可触发当前页面刷新
- Y 可进入搜索页
- B 可从搜索页返回首页
- A 对当前焦点项执行点击
- 无 `FATAL EXCEPTION`
- 底部按钮布局与浅色主题正常

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
