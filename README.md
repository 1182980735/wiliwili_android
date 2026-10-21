# wiliwili Android 技术验证版

> 个人自用、不对外分发的 Android 可运行验证工程。

## 目标

在 Android 上尽可能保留 wiliwili 的视觉语言与遥控/手柄操作习惯，同时复用本机
`blbl-main` 已有的 Android 网络、登录、视频解析与 Media3 播放能力。

## 构建

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_SDK_ROOT=/usr/local/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 当前阶段

- [x] Android 工程基线
- [x] wiliwili 浅色主题（页面 #F5F6F7 / 卡片 #FFFFFF / 主色 #FF6699）
- [x] 侧边栏 + 首页 Tab + 视频网格主壳
- [x] 强制横屏（所有 Activity 使用 sensorLandscape，运行时再次锁定）
- [x] wiliwili 风格播放器 OSD：粉色进度、圆形播放键、右侧控制组、顶部渐变信息条
- [x] 129 个 wiliwili SVG 批量转换为 VectorDrawable
- [x] 视频卡片与视频详情页头部 wiliwili 化
- [x] 搜索/动态/我的页面对齐
- [x] 主壳三入口侧栏、首页搜索框、底部手柄提示栏
- [x] 参考截图级浅色首页/动态/我的重构
- [x] 播放器与接口链路实测

来源与使用边界见 [SOURCE_RECORD.md](SOURCE_RECORD.md)。

## 文档

- 来源与使用边界：[SOURCE_RECORD.md](SOURCE_RECORD.md)
- UI / 交互规格：[docs/android-port/wiliwili-ui-spec.md](docs/android-port/wiliwili-ui-spec.md)
- blbl Android 架构分析：[docs/android-port/blbl-architecture.md](docs/android-port/blbl-architecture.md)
- 各阶段验证记录：[docs/android-port](docs/android-port)

> 本仓库为公开技术验证仓库。blbl-main 参考代码未声明 LICENSE，wiliwili 资源采用 GPLv3；如需再分发或商用，请先取得相关授权并完成许可证合规审查。
