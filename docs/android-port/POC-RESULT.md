# Android 技术验证结果（个人自用）

> 目标：验证能否在 Android 上保留 wiliwili 的主壳结构、视觉语言与遥控操作习惯，
> 同时复用本机 blbl-main 的已实现 Android 能力。
>
> 本验证仅用于个人学习，不对外分发。来源与授权边界见 `android/SOURCE_RECORD.md`。

## 结论

可行。Android 原生 View 体系可以复刻 wiliwili 的核心产品结构，而不需要把
wiliwili 的 C++/SDL/borealis/libmpv 运行时移植到 Android。

本次已完成一个可构建、可安装、可播放的技术验证版：

- 工程路径：`android/`
- applicationId：`cn.xfangfang.wiliwili.poc`
- 构建：`./gradlew assembleDebug`
- APK：`android/app/build/outputs/apk/debug/app-debug.apk`（约 14 MB）
- 已在 Android 12、2560×1440 横屏设备上安装并运行

## 已落地内容

| 项目 | 实现 |
| --- | --- |
| 主壳 | 左侧图标导航 + 内容区，侧栏宽度和图标尺寸按 wiliwili 比例调整 |
| 首页 Tab | 直播 / 推荐 / 热门 / 追番 / 影视，默认推荐 |
| 视觉令牌 | 默认浅色：背景 `#F5F6F7`、卡片 `#FFFFFF`、主色 `#FF6699`、次级文字 `#9499A0`；播放器保持深色 |
| 侧栏 | 首页/动态/我的三个图标入口，底部消息/设置；文字标签隐藏并保留 contentDescription |
| 顶部/底部 | 首页右上角圆角搜索框，底部电池/时钟/`Y 搜索 B 返回 A 确定`提示栏 |
| 焦点 | 复用原有 Dpad/遥控焦点机制；`Y` 键可打开搜索，保持侧栏/内容/播放器返回链路 |
| 播放器 | 已通过视频卡片进入 Media3 PlayerActivity 并成功起播 |
| 播放器 OSD | 粉色进度条/滑块、82dp 圆形播放键、右侧控制组、顶部渐变标题区 |
| 横屏 | Manifest 全部 Activity 使用 sensorLandscape，BaseActivity 运行时再次锁定 |
| 单元测试 | `./gradlew testDebugUnitTest` 通过 |

## 与 wiliwili 仍有差距

1. 播放器 OSD 已完成第一轮 wiliwili 视觉重排，但清晰度/倍速仍通过原有设置面板进入，未改成 wiliwili 的独立文字按钮。
2. 视频卡片与详情页头部已完成第一轮 wiliwili 化；折叠区、评论区和部分列表仍沿用 blbl 适配器。
3. 已转换 129 个 wiliwili VectorDrawable，但仍有少量深层页面使用 blbl 原图标。
4. 不计划适配手机竖屏；当前版本强制横屏。
5. 搜索/动态/我的已完成首轮布局对齐，并完成真实扫码登录后的动态、我的、搜索和播放链路回归。

## 下一步建议

1. 用实体遥控器/手柄回归完整 D-pad 焦点路径。
2. 继续替换搜索/动态/我的深层列表中尚未切换的 blbl 图标。
3. 继续完善播放器 OSD 的清晰度/倍速文字按钮。
4. 继续打磨详情页折叠区、评论区和播放器设置面板。
5. 每次迭代继续使用 `assembleDebug` + 真机/D-pad 冒烟验证。
