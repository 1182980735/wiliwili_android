# wiliwili_A Android

当前发布版本：**0.24** 技术验证版

- 应用显示名：`wiliwili_A`
- 安装包名：`com.anhuihl.wiliwili.android`
- versionName：`0.24`
- versionCode：`24`

> 公开的 Android 原生技术验证工程，非 wiliwili 官方 Android 版本。

## 0.24 更新内容

- 全屏播放器支持 Android 画中画
- 按 Home 键进入 PiP，播放器继续播放
- PiP 退出后恢复播放器界面
- Home/画中画期间不立即暂停播放器
- 保留后台音频继续播放能力（当前为 Activity 生命周期方案）
- 保留 0.23 公开用户空间关注/粉丝入口

## 0.23 更新内容

- 公开用户空间增加关注/粉丝列表入口
- 粉丝数和关注数可点击查看对应用户列表
- 公开用户空间继续保留投稿、合集/系列
- 新增用户空间关注/粉丝标题覆盖
- 保留评论楼中楼、评论操作、发弹幕和历史回退

## 0.22 更新内容

- 评论楼中楼增加独立“← 全部回复”返回入口
- 楼中楼回复分页继续加载
- 楼中楼回复支持点赞、点踩、回复
- 回复评论携带 root/parent
- 发送弹幕/评论前检查登录态和 `bili_jct`
- CSRF 缺失时提示退出后重新扫码登录
- 保留历史记录回退和双平台 Release 发布

## 0.21 更新内容

- 修复历史记录偶发空白
- 历史接口增加 Web 指纹 Cookie 和 Referer 头部
- 新版游标接口为空时回退旧版历史接口
- 历史条目解析兼容更多 business 类型
- 历史空结果会继续尝试旧接口，避免整页空白
- 保留评论长按菜单、发弹幕和双平台 Release 发布

## 0.20 更新内容

- 长按评论弹出发送操作菜单
- 支持评论点赞
- 支持评论点踩
- 支持回复评论，回复请求携带 root/parent
- 评论操作成功后刷新评论列表
- 保留发送弹幕和发表评论功能
- 每个 Release 同时上传 GitHub 和 Gitee

## 0.19 更新内容

- 修复评论面板“写评论”按钮黑底黑字、文字不可见
- 按钮文字改为播放器固定白色，深色评论面板下可见
- 继续保留发送弹幕、发表评论、Gitee 优先更新

## 0.18 更新内容

- 新增发送弹幕：全屏播放器和视频详情内嵌操作栏均可打开输入框
- 新增发表评论：视频评论面板新增“写评论”入口
- 评论发布走 B 站 `/x/v2/reply/add`
- 弹幕发送走 B 站 `/x/v2/dm/post`
- 发送成功后自动刷新当前评论列表
- 继续使用 Gitee 优先更新，GitHub 作为备用更新源
- 发布脚本同时上传 GitHub 和 Gitee

## 0.17 更新内容

- 设置页同时显示 Gitee 和 GitHub 项目地址
- 应用内更新优先从 Gitee Release 拉取 APK
- Gitee 不可用时自动回退 GitHub Release
- 新增双平台发布脚本 `tools/publish_release.py`
- 每次发布同时创建/更新 GitHub Release 和 Gitee Release，并上传同一个 APK
- 包名保持 `com.anhuihl.wiliwili.android`，应用名保持 `wiliwili_A`

## 0.16 更新内容

### 播放与详情页

- 打开视频详情页不再自动播放，首屏焦点默认落在播放按钮
- 按确认键后才开始播放
- 内嵌窗口进入全屏时强制继承当前进度，不再受自动续播 5 秒阈值影响
- 全屏播放器退出时回传当前进度和播放/暂停状态
- 内嵌窗口按全屏进度恢复，窗口与全屏进度双向同步
- 视频详情页新增下载按钮，复用 `DownloadRequestResolver + DownloadService`
- 详情页操作按钮改为低饱和雾青色
- 详情页视频信息条和简介取消背景色

### 动态与分类

- 动态页新增全部 / 视频 / 专栏 / 图文 / 转发五个页签
- 专栏详情支持复制正文、字号调节、夜间模式
- 专栏图片支持点击缩放、保存到相册
- 动态详情使用原生 API 渲染，避免 WebView 412 风控
- 侧边栏新增分类入口，固定排在首页和动态之间
- 分类页支持全站 / 动画 / 音乐 / 舞蹈 / 游戏 / 知识 / 科技等页签

### 账号与空间

- 新增粉丝列表
- 用户浮层和“我的”页关注/粉丝统计可进入对应列表
- 关注列表支持按关注分组筛选
- 保留历史、投稿、收藏夹、追番、追剧、稍后再看、最近点赞
- 历史、收藏夹详情、多账号切换继续可用

### 设置与更新

- 设置 → 关于应用新增“检查更新”
- 从 GitHub Releases API 获取最新正式版
- 自动解析 Release 更新日志和 APK 资产地址
- 下载 APK 后调用系统安装器
- 新增“自动检查更新”开关
- 云端播放进度恢复/上报继续可用

### 工程

- 安装包名改为 `com.anhuihl.wiliwili.android`
- 应用显示名改为 `wiliwili_A`
- 启动图标改为全幅自适应图标：粉色背景 + 透明前景白色 wiliWili 标志，不再套深色遮罩
- Kotlin 内部命名空间仍为 `blbl.cat3399`，仅变更 Android applicationId，避免大面积重写源码包路径
- 新包名是独立应用，旧 `cn.xfangfang.wiliwili.poc` 安装可共存

## 版本任务

- `0.22` 评论链路：楼中楼完整页、回复分页、点赞/点踩/回复、CSRF 登录态检查
- `0.23` 用户空间：公开用户动态、收藏、关注/粉丝列表与更多操作
- `0.24` 播放器：画中画、后台音频、MediaSession/通知与车机适配

## 代码托管与更新源

- GitHub：https://github.com/1182980735/wiliwili_android
- Gitee：https://gitee.com/anhuihl/wiliwili_android
- 应用内更新优先使用 Gitee Releases API 和 Gitee Release APK
- Gitee 不可用时自动回退 GitHub Releases
- 双平台推送：`git push origin main` / `git push origin --tags` 会同时推 GitHub + Gitee
- 双平台 Release 发布脚本：`python3 tools/publish_release.py 0.17`
- 发布脚本会同时上传 GitHub 和 Gitee 的 APK 资产

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
| IjkPlayer / FFmpeg | ExoPlayer 失败时同画质软解兜底，原生库已打包 | `app/libs/ijkplayer-cmake-release.aar` |
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
- [x] 内嵌播放器进入全屏强制继承当前进度，退出全屏回传进度
- [x] 视频详情首屏不自动播放，焦点默认播放按钮
- [x] 设置页支持从 GitHub Release 检查更新和下载安装
- [x] 动态五页签、专栏详情工具栏、粉丝列表与关注分组
- [x] 视频详情下载按钮、侧边栏分类入口、详情页配色调整
- [x] 应用包名改为 com.anhuihl.wiliwili.android，显示名改为 wiliwili_A
- [x] 内嵌播放器卡顿停滞自动切换 CDN 重试
- [x] 清理播放结束提示、详情页 A 键改为确认
- [x] 详情页切换评论/分集/推荐/投稿后右侧内容面板等宽（guidePercent=0.70）
- [x] 内嵌播放器 DASH MPD + CDN failover，修复持续缓冲回归
- [x] 全面功能回测并修复卡片菜单错误勾选
- [x] 首页直播子 Tab 关注置首
- [x] 修复直播页摇杆焦点无法进入内容区
- [x] 发布版本 0.10
- [x] 详情页 LT/RT 改为音量调节（LB/RB 切换 Tab）
- [x] 左侧栏消息/设置图标尺寸对齐
- [x] 全屏播放器 LT/RT 改为音量调节
- [x] buvid3 infoc 修复、下一个视频优先级、电视遥控器映射核对
- [x] 比亚迪车机兼容性静态评估
- [x] 修复软件解码播放卡死并发布 0.12
- [x] 播放器改为硬件解码优先与合并音视频源，取消自动降画质
- [x] 内嵌播放器抽成 BlblPlayerEngine，IjkPlayer 同画质兜底并内置原生库
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

## 上线手册

- [docs/android-port/RELEASE-MANUAL.md](docs/android-port/RELEASE-MANUAL.md)
