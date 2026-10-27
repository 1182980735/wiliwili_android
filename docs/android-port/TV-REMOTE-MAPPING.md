# 电视遥控器按键映射核对

对比上游 `1182980735/blbl` 最新 `main`：

- `core/tv/RemoteKeys.kt`
- `feature/player/PlayerActivity.kt`
- `ui/MainActivity.kt`

结论：当前 Android 版的**电视遥控器标准按键**与上游 blbl 基本一致。

## 主界面

| 遥控器按键 | 行为 |
|---|---|
| 方向键 | 焦点移动；左边界进入侧栏，右侧回内容区 |
| OK / DPAD_CENTER | 确认当前焦点 |
| 返回 / BACK / ESCAPE | 返回或退出当前层 |
| MENU / REFRESH / F5 | 刷新当前页面（`RemoteKeys.isRefreshKey`） |

## 全屏播放器

| 遥控器按键 | 行为 |
|---|---|
| 方向键左右 | 快退/快进预览 |
| 方向键上下 | OSD 焦点移动；隐藏时打开 OSD/播放列表 |
| OK / DPAD_CENTER / ENTER / NUMPAD_ENTER / BUTTON_A | 播放暂停或确认焦点 |
| BACK / ESCAPE / BUTTON_B | 关闭面板/隐藏 OSD/退出 |
| MENU / SETTINGS | 打开设置面板 |
| INFO / GUIDE | 显示 OSD 并聚焦控制 |
| MEDIA_PLAY_PAUSE / PLAY / PAUSE | 播放/暂停 |
| MEDIA_NEXT / MEDIA_PREVIOUS | 按播放模式播放下一个/上一个 |
| MEDIA_REWIND / FAST_FORWARD | 快退/快进 |

## 手柄扩展映射

在电视遥控器之外，本项目保留自定义手柄映射：

- `LB/RB`：详情页切换右侧 Tab；全屏播放器快退/快进
- `LT/RT`：详情页和全屏播放器音量减/加
- `X`：刷新/评论排序/弹幕开关
- `Y`：搜索/OSD
- `A/B`：确认/返回
- `START`：画质
- `SELECT`：视频数据

这些是项目自定义扩展，不影响标准电视遥控器按键。

## 注意

- 不同车机/电视厂家的遥控器可能自定义键值，需要用 `adb shell getevent -l` 或应用内按键日志确认。
- 如果车机把方向盘媒体键映射成厂商私有键值，应用无法识别为 Android 标准 `KEYCODE_MEDIA_*`，需要车机侧映射。
