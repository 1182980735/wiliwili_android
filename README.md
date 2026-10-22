# wiliwili Android 技术验证版

> 公开的 Android 原生技术验证工程，非 wiliwili 官方 Android 版本。

## 目标

在 Android 上尽可能保留 wiliwili 的视觉语言与遥控/手柄操作习惯，同时参考并复用
blbl Android 工程中的网络、登录、视频解析与 Media3 播放能力。

## 技术项目与上游来源

本仓库是 Android 原生技术验证工程，不是 wiliwili 官方 Android 版本，也不直接嵌入
wiliwili 的 C++/SDL/borealis/libmpv 运行时。界面、交互和资源参考 wiliwili，Android
运行能力参考并复用了 blbl Android 工程的实现。

| 项目 | 用途 | 上游来源 | 本仓库参考/使用版本 |
| --- | --- | --- | --- |
| wiliwili | UI/交互规范、主题色、页面结构、129 个 SVG 图标 | https://github.com/xfangfang/wiliwili | Fork `1182980735/wiliwili`，分支 `yoga`，提交 `88e5876`（v1.6.0） |
| blbl / blbl-android | Android 宿主、网络/WBI/Cookie、扫码登录、视频解析、Media3 播放、弹幕、焦点基础设施 | 上游 https://github.com/cat3399/blbl ; Fork https://github.com/1182980735/blbl | 分支 `main`，提交 `c9514a66` |
| BBLL | 页面结构和遥控/手柄操作逻辑参考（由 blbl README 标注） | https://github.com/xiaye13579/BBLL | 设计/交互参考 |
| PiliPlus | 登录等关键功能逻辑参考（由 blbl README 标注） | https://github.com/bggRGjQaUbCoE/PiliPlus | 设计/行为参考 |
| bilibili-API-collect | B 站 API 文档与接口行为参考 | https://github.com/SocialSisterYi/bilibili-API-collect | API 文档参考 |

### 主要 Android 技术栈

| 技术 | 用途 | 版本/说明 |
| --- | --- | --- |
| Kotlin + AndroidX + Material Components | Android 原生 UI 与生命周期 | 见 `app/build.gradle.kts` |
| ConstraintLayout / RecyclerView / ViewPager2 / SwipeRefreshLayout | 页面布局、网格、Tab 与刷新 | 见 `app/build.gradle.kts` |
| AndroidX Media3 / ExoPlayer | DASH/HLS 视频播放与音视频轨道选择 | 1.8.0 |
| OkHttp | HTTP、Cookie 与 WBI 接口请求 | 4.12.0 |
| kotlinx.coroutines | 异步任务与分页请求 | 1.8.1 |
| protobuf-javalite / gRPC | B 站 App 接口协议 | 3.25.5 / 1.72.0 |
| ZXing | 二维码登录 | 3.5.3 |
| IjkPlayer / FFmpeg | 可选备用播放内核 | `app/libs/ijkplayer-cmake-release.aar` |
| Gradle + Android Gradle Plugin | 构建 | Gradle 8.7 / AGP 8.5.2 |
| 本地 SVG 转换工具 | wiliwili SVG 转 Android VectorDrawable | `tools/svg_to_vector.py` |

## 许可证与来源说明

- wiliwili 上游采用 GPLv3。
- blbl 上游仓库当前未声明 LICENSE，代码版权归原作者所有。
- IjkPlayer、FFmpeg 及其他 AAR/依赖按各自上游许可证执行。
- 公开可见不代表获得再分发或商业使用授权；完整来源记录和合规边界见
  [SOURCE_RECORD.md](SOURCE_RECORD.md)。

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
- [x] 动态页 UP 列表按最近更新顺序排序
- [x] 底部 X 刷新 / Y 搜索 / B 返回 / A 确定快捷操作
- [x] 视频点击进入详情页，左右分栏展示评论/合集/推荐/投稿
- [x] API GET 请求自动重试，降低偶发网络刷新失败
- [x] 弱网播放错误与长时间缓冲自动恢复
- [x] 详情页评论/推荐 Tab 动态分栏与信息区紧凑化
- [x] 修复详情页分栏空白断层并统一右侧 Tab 字号
- [x] 详情页视频区域放大并让信息区贴近底部
- [x] 修复详情页按钮比例并为未播放视频增加黑色背景
- [x] 详情页视频容器铺满左栏，移除旧 480dp 宽度限制
- [x] 视频详情页底部 X/Y/A/B 控制栏与焦点窗口刷新/返回
- [x] 消息中心：聊天列表 / 回复我的 / @我的 / 收到的赞
- [x] Xbox 手柄 LB/RB 左右切换 Tab，A 键兼容 DPAD 确认
- [x] 详情页右侧 Tab 显示评论/分集/推荐/投稿数量
- [x] 详情页推荐/分集/投稿改为紧凑横向视频列表
- [x] 动态页左侧 UP 列表默认显示最近更新红点
- [x] 评论区显示 IP 属地
- [x] 消息中心聊天列表可进入并查看历史消息
- [x] 详情页右侧卡片层级与动态底部控制栏
- [x] 搜索页三列布局、热门排名和标记
- [x] 聊天视频消息显示视频卡片并可点击跳转
- [x] 详情页操作计数与播放器快捷控制栏
- [x] 详情页内嵌播放器（进度/弹幕/画质/全屏）
- [x] 内嵌播放器 10 视频回归与绿屏/首帧兼容修复
- [x] 消息中心聊天文本发送
- [x] 内嵌播放器音量/倍速/弹幕/设置/播放列表/视频数据
- [x] 内嵌播放器硬件 AVC 自适应画质
- [x] 动态页专栏 Tab 与动态文章列表
- [x] 聊天历史分页、图片/表情渲染与已读同步
- [x] 我的页动态数量统计修复
- [x] 热门搜索上限调整到 50 条
- [x] 视频卡片长按更多菜单
- [x] 全屏播放器视频数据、DLNA 设备发现与播放结束行为
- [x] 详情页手柄映射对齐 wiliwili 原生方案
- [x] 软件解码器自动回退 480P，修复模拟器偶发绿屏
- [x] 移除全屏播放器“视频详情页”和“上传广告片段”按钮
- [x] 关闭旧 blbl 项目的启动自动更新提示
- [x] 修正设置项目地址、SponsorBlock Referer、更新源和上游 QQ 群残留
- [x] 内嵌播放切全屏继承当前播放进度
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
