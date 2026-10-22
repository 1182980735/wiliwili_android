# Android 移植阶段十八：手柄 Tab 切换与 Xbox 模拟测试

> 个人自用、仅技术验证，不对外分发。

## 新增按键

支持 Xbox 手柄左右肩键切换 Tab：

| Xbox 按键 | Android KeyCode | 行为 |
| --- | --- | --- |
| LB | `KEYCODE_BUTTON_L1` (102) | 向左切换 Tab |
| RB | `KEYCODE_BUTTON_R1` (103) | 向右切换 Tab |
| LT | `KEYCODE_BUTTON_L2` (104) | 兼容映射为向左切换 Tab |
| RT | `KEYCODE_BUTTON_R2` (105) | 兼容映射为向右切换 Tab |

## 实现方式

- `BaseActivity` 统一拦截 L1/L2 和 R1/R2
- 在 Activity 的 View 树中查找当前可见的 `TabLayout`
- 选择左侧或右侧相邻 Tab
- 到达第一个/最后一个 Tab 时保持当前选中项
- 首页、搜索、动态、我的、视频详情、消息中心等使用 TabLayout 的页面都可生效

## 底部控制栏

首页和视频详情页底部新增：

```text
LA 左Tab    RA 右Tab
```

点击按钮与手柄 LB/RB 行为一致。

## A 键兼容修正

部分页面焦点可能落在 RecyclerView 等容器上，单纯 `performClick()` 不会触发子项。
现在 A 键的确认逻辑改为：

1. 先对当前焦点 View 调用 `performClick()`
2. 如果未处理，再发送合成的 `DPAD_CENTER` 按下/抬起事件
3. 与遥控器确认键行为保持一致

## Xbox 模拟测试

通过 adb 注入标准 Xbox Android 键值：

```bash
# A / B / X / Y
adb shell input keyevent 96
adb shell input keyevent 97
adb shell input keyevent 99
adb shell input keyevent 100

# LB / RB
adb shell input keyevent 102
adb shell input keyevent 103
```

测试结果：

- 首页初始选中“推荐”，RB 切到“热门”
- LB 切回“推荐”
- 视频详情页 RB 从“评论”切到“合集”
- 视频详情页 B 在默认状态关闭页面
- 视频详情页 X 正常触发刷新
- 底部 LA/RA/X/Y/B/A 六个按钮均显示正常

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
