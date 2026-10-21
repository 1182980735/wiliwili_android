# blbl-main Android 架构分析与 wiliwili 移植集成方案

> 分析对象：`/Users/mac/Documents/trae_projects/blbl-main`（Git `main`，审查时 HEAD `c9514a66`）  
> 目标仓库：`/Users/mac/Documents/trae_projects/wiliwili`（GPLv3）  
> 文档性质：仅做静态代码/构建产物审查，未修改 `blbl-main` 或 `wiliwili` 源码，也未执行联网构建。  
> 结论适用范围：把 blbl-main 作为 Android 运行基础，同时保留 wiliwili 的界面语言、导航结构与操作习惯。

---

## 0. 结论先行

### 0.1 最重要的五个判断

1. **blbl-main 不是可发布的 Android 基础库，而是一个单模块、单 `:app` 的完整应用。**  
   `settings.gradle.kts` 只有 `include(":app")`，没有 `core`、`feature`、`player` 等 Gradle 子模块，也没有 Maven 坐标或公开 API。所谓“复用”本质上是**源码级复制/抽取**，不是加一行依赖。

2. **最适合复用的是“非 UI 能力层”：网络/WBI/Cookie、登录、视频 API、Media3 播放引擎、弹幕引擎、焦点/主题基础设施。**  
   最不适合直接复用的是：`MainActivity`、各页面布局、4,000+ 行的 `PlayerActivity`、设置页以及构建在具体业务 Activity 上的交互编排。

3. **推荐架构是“blbl 作为 Android 宿主与能力层 + wiliwili 视觉/交互为上层产品规范”的原生 Android 重构，而不是把 wiliwili 的 C++/SDL/borealis 运行时嵌进 blbl。**  
   理由：wiliwili 使用 borealis + libmpv，blbl 使用 Android View + Media3；两套渲染、输入、播放器、资源体系同时存在时，MVP 复杂度会失控。只有在“必须原样保留 wiliwili 二进制 UI”的硬约束下，才考虑 SDL Activity/JNI 桥接方案，且应另立可行性验证任务。

4. **MVP 应优先做 ExoPlayer + Web API，不引入 IjkPlayer、App gRPC、直播、下载和自动更新。**  
   这些模块拖慢首版，且 IjkPlayer 采用运行时下载 `.so` 的机制，带供应链与许可证双重风险。

5. **许可证是当前的首要阻断项，而不是工程细节。**  
   `blbl-main` 仓库根目录未发现 `LICENSE`/`COPYING`/`NOTICE`，源码文件也没有 SPDX 头；wiliwili 是 GPLv3。没有明确授权时，不能把 blbl 代码直接并入可分发应用。需先取得作者书面授权，或进行干净房间重写/只复用接口设计。

### 0.2 推荐落地路径（一句话版）

> **源码 fork blbl 的 core/net/api/player → 抽成内部模块 → 用 wiliwili 设计令牌重做 Android 导航/卡片/播放器 UI → 首版仅 ExoPlayer + Web API + 扫码登录 + 推荐/搜索/详情/播放 → 补齐许可证与第三方声明后再考虑 Ijk/直播/下载。**

---

## 1. 工程与构建快照

### 1.1 构建入口

| 文件 | 关键内容 | 对移植的意义 |
|---|---|---|
| `app/build.gradle.kts` | `namespace/applicationId = blbl.cat3399`；`compileSdk = 36`；`minSdk = 21`；`targetSdk = 36`；Java/Kotlin 17 | 可直接覆盖 Android 5.0+；但当前 AGP 与 compileSdk 需校准 |
| `settings.gradle.kts` | 仅 `include(":app")`；仓库只有 `google()`、`mavenCentral()`；`FAIL_ON_PROJECT_REPOS` | 单模块，需自行拆模块；依赖来源收敛较好 |
| 根 `build.gradle.kts` | AGP `8.5.2`、Kotlin `1.9.24`、Protobuf 插件 `0.9.4` | AGP 8.5.2 官方兼容的 compileSdk 低于 36，当前靠 `android.suppressUnsupportedCompileSdk=36` 压住警告，长期应升级 AGP 或降 compileSdk |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle `8.7` | 与 AGP 8.5 配套；升级 AGP 时一并升级 |
| `gradle.properties` | AndroidX、非传递 R 类、UTF-8、JVM `-Xmx2g` | 无特殊私有仓库依赖 |
| `app/proguard-rules.pro` | 保留 protobuf lite、`tv.danmaku.ijk.**`、`com.debugly.ijkplayer.**` | 若移除 Ijk/App gRPC，可同步删减规则 |

### 1.2 Android 配置

- `buildFeatures`: `buildConfig = true`、`viewBinding = true`；项目是 **ViewBinding + Android View 体系**，不是 Compose。
- 打包配置显式排除了 `**/libijkplayer.so`；所以默认 APK **不带 IjkPlayer native 库**，需要运行时从 `https://cat3399.top/blbl/ijkplayer/...` 下载插件。
- `debug` 使用仓库内固定 `debug.keystore`；`release` 依赖 `keystore/release.keystore` 与四个环境变量/属性。
- 构建时执行自定义任务 `checkThemeTokens`，禁止布局直接引用固定色板资源，必须使用主题属性。这个机制值得保留，并扩展为 wiliwili 设计令牌校验。

### 1.3 SDK/ABI 事实

- `minSdk = 21`（Android 5.0）。README 也声称支持 Android 5。
- 工作区另有一个 `android-api19-release.yml` 工作流，但它明确 checkout `blbl-api19` 分支；当前 `main` 的 `minSdk` 仍是 21，不能据此认为 main 已支持 API 19。
- `app/libs/ijkplayer-cmake-release.aar` 含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四套 `libijkplayer.so`，解压后约 40 MB；AAR 文件约 17 MB。
- 已有 debug APK 约 14 MB，未包含上述 `.so`；包含 18 个 dex，说明依赖/方法数量本身已不小。

### 1.4 直接依赖清单

来自 `app/build.gradle.kts`：

| 依赖 | 用途 | 备注 |
|---|---|---|
| `androidx.core:core-ktx:1.12.0` | AndroidX 基础 | Apache-2.0 |
| `androidx.appcompat:appcompat:1.6.1` | Activity/Compat | Apache-2.0 |
| `com.google.android.material:material:1.12.0` | Card/Tab/Dialog 等 | Apache-2.0 |
| `androidx.constraintlayout:constraintlayout:2.1.4` | 布局 | Apache-2.0 |
| `androidx.recyclerview:recyclerview:1.3.2` | 列表/网格 | Apache-2.0 |
| `androidx.viewpager2:viewpager2:1.0.0` | Tab/页面切换 | Apache-2.0 |
| `androidx.swiperefreshlayout:swiperefreshlayout:1.1.0` | 下拉刷新 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx:2.7.0` | 生命周期/协程 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1` | 协程 | Apache-2.0 |
| `com.squareup.okhttp3:okhttp:4.12.0` | HTTP | Apache-2.0 |
| `org.brotli:dec:0.1.2` | Brotli 解压 | 具体许可证以锁定 POM 为准，发布前核验 |
| `androidx.media3:*:1.8.0` | ExoPlayer、DASH/HLS、UI、OkHttp 数据源 | Apache-2.0 |
| `com.google.protobuf:protobuf-javalite:3.25.5` | App 接口 proto | BSD-3-Clause |
| `io.grpc:grpc-*:1.72.0` | App 接口 gRPC | Apache-2.0 |
| `javax.annotation:javax.annotation-api:1.3.2` | gRPC 生成代码编译期注解 | 常见为 CDDL/GPL 双许可，需按 POM 核验 |
| `com.google.zxing:core:3.5.3` | 二维码生成 | Apache-2.0 |
| `files("libs/ijkplayer-cmake-release.aar")` | IjkPlayer | LGPL/FFmpeg 合规重点，见第 9 节 |

测试依赖：JUnit 4.13.2、`kotlinx-coroutines-test`。`app/src/test` 有 28 个测试文件、约 1,892 行，主要集中在播放器状态机、DASH、设置、分页和解析逻辑；这是可保留的工程资产。

---

## 2. 模块地图

### 2.1 物理结构

```text
blbl-main/
├── app/                                  # 唯一 Gradle 模块
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── ijkplayer-cmake-release.aar   # 四 ABI 的 IjkPlayer 二进制
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/blbl/cat3399/
│       │   │   ├── BlblApp.kt
│       │   │   ├── core/                 # 网络、API、模型、偏好、主题、UI 基础设施
│       │   │   ├── feature/              # 按页面/业务拆包
│       │   │   └── ui/                   # MainActivity、侧边栏、启动页
│       │   ├── proto/                    # B 站 App 接口 proto
│       │   └── res/                      # 65 个 layout + 主题/颜色/尺寸/字符串
│       └── test/                         # 28 个单元测试文件
├── gradle/
├── debug.keystore
└── README.md / CHANGELOG.md
```

### 2.2 逻辑分层

```text
┌──────────────────────────────────────────────────────────────┐
│ UI / Feature 层                                               │
│ MainActivity + MainRootNavRegistry + SidebarNavAdapter       │
│ Home/Search/VideoDetail/Player/Live/My/Settings...            │
│ Activity/Fragment/ViewBinding/RecyclerView/DPAD Focus         │
├──────────────────────────────────────────────────────────────┤
│ 应用状态 / 偏好层                                             │
│ AppPrefs、AccountSessionStore、PlayerSessionSettings          │
│ ThemePresets、UiScale/UiDensity、Popup/Focus helpers          │
├──────────────────────────────────────────────────────────────┤
│ 领域 / API 层                                                 │
│ BiliApi 门面、VideoApiGateway、WebVideoApi/AppVideoApi        │
│ VideoSourceApi 能力选择、Mapper、BiliApiException             │
├──────────────────────────────────────────────────────────────┤
│ 网络 / 认证层                                                 │
│ BiliClient、CookieStore、WbiSigner、AppSigner                 │
│ PiliWebHeaders、WebCookieMaintainer、Http.await/ipv4Dns       │
├──────────────────────────────────────────────────────────────┤
│ 播放 / 媒体层                                                 │
│ BlblPlayerEngine、ExoPlayerEngine、IjkPlayerEngine            │
│ DASH MPD/代理、CDN failover、VolumeBalance、DanmakuEngine     │
├──────────────────────────────────────────────────────────────┤
│ 平台基础层                                                    │
│ BaseActivity、Immersive、AppToast、AppPopup、焦点/Dpad 工具    │
│ ImageLoader、PagedGridStateMachine、Download/Update/Log       │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 包级模块地图

| 包/目录 | 规模与代表文件 | 职责 | 对 wiliwili Android 的价值 |
|---|---|---|---|
| `core/net` | 7 个文件，约 1,293 行 | OkHttp、Cookie、WBI、App 签名、设备指纹、Cookie 维护 | **最高**，几乎是网络底座 |
| `core/api` | 9 个文件，约 10,000 行（含子目录） | B 站 Web/App API 封装 | **高**，但 `BiliApi` 是巨型门面，需拆分 |
| `core/api/video` | `VideoSourceApi.kt`、Web/App 实现、Mapper、DASH 模型 | 视频详情/推荐/播放地址，Web/App 双源切换 | **高** |
| `core/model` | 12 个文件 | `VideoCard`、`Danmaku`、`HistoryEntry` 等跨页面模型 | **中高**，可作为 UI 状态映射源 |
| `core/prefs` | 7 个文件，`AppPrefs.kt` 1,437 行 | SharedPreferences 配置、播放器参数、主题、启动页 | **中**，建议按领域拆分后使用 |
| `core/account` | `AccountSessionStore.kt` | 多账号 Cookie + 凭据快照 | **中**，MVP 可只保留单账号 |
| `core/theme` | `ThemePresets.kt`、`LauncherAliasManager.kt` | 主题预设解析与应用 | **中**，思想可复用，色板需替换 |
| `core/ui` | 27 个文件 | BaseActivity、主题色、焦点、DPAD、弹窗、缩放、沉浸式 | **中高**，TV/遥控适配价值大 |
| `core/tv` | `RemoteKeys.kt`、`TvDevice.kt` | 遥控键/TV 判定 | **中** |
| `core/image` | `ImageLoader.kt`、`ImageUrl.kt` | OkHttp + LruCache 的轻量图片加载 | **中**，可替换为 Coil，但 MVP 可先用 |
| `core/paging` | `PagedGridStateMachine.kt` | 页码/游标分页状态机 | **高**，Android-free、可单测 |
| `core/download` | `DownloadTaskStore.kt` 等 | 下载任务持久化 | 低，MVP 剥离 |
| `core/update` | `ApkUpdater.kt`、`ApkUpdateFlow.kt` | 自更新、下载 APK、安装 | 低且高权限，MVP 剥离 |
| `core/log` | 日志、崩溃、上传 | 诊断 | 低，需隐私审查 |
| `core/emote`、`core/note`、`core/io` | 表情、笔记、导出/转封装 | 边缘功能 | 低，剥离 |
| `feature/player` | 48 个文件，约 26,392 行 | 播放器 UI + 引擎 + 弹幕 + 下载 | **核心但有强耦合**，需拆分 |
| `feature/video` | 11 个文件 | 视频详情/分区/标签/卡片 | **中高**，详情页可参考 |
| `feature/home`、`category`、`search` | 多个 Fragment/Adapter | 浏览与检索 | 中，UI 需重做 |
| `feature/live` | 15 个文件 | 直播列表/播放/弹幕/SC | 低，MVP 剥离 |
| `feature/my`、`following` | 25 个文件 | 我的、历史、收藏、追番、UP 详情 | 中后置 |
| `feature/settings` | 10 个文件（含 2,983 行 `SettingsInteractionHandler.kt`） | 设置页 | 低，MVP 只保留最小设置 |
| `ui` | `MainActivity` 1,463 行、`MainRootNavRegistry`、`SidebarNavAdapter` | 根导航与侧边栏 | **高**，但应重写布局和交互 |
| `res` | 65 个 layout、主题/色板/尺寸 | 现有 UI 资源 | 主题属性机制可复用；布局全部重做 |
| `proto` | 12 个 proto | App gRPC 协议 | 低，Web-only MVP 不需要 |

---

## 3. 关键文件路径与职责

### 3.1 启动、Activity 与导航

| 文件 | 职责 | 审查要点 |
|---|---|---|
| `app/src/main/java/blbl/cat3399/BlblApp.kt` | `Application` 初始化：日志、崩溃、`BiliClient.init()`、下载恢复、Cookie 每日维护、表情预热 | 复用时可裁剪为 `BiliClient.init()` + 必要恢复；不要把下载/日志上传带入 MVP |
| `app/src/main/java/blbl/cat3399/core/ui/BaseActivity.kt` | 统一主题应用、UiDensity 包装、UiScale 变化重建、异常 savedInstanceState 清洗 | 可复用；建议保留“主题在 `super.onCreate` 前应用”的顺序 |
| `app/src/main/java/blbl/cat3399/ui/MainActivity.kt` | 主界面：侧边栏、根 Fragment 切换、用户信息浮层、返回键、DPAD 焦点守卫、自动更新/崩溃提示 | 导航骨架可借鉴，但直接复用会把 blbl 的更新、账号浮层、焦点逻辑全部带进来 |
| `app/src/main/java/blbl/cat3399/ui/MainRootNavRegistry.kt` | `RootNavSpec` + 导航项/Fragment 工厂/启动页 | **值得保留的注册表模式**；改造成 wiliwili 的导航目的地表 |
| `app/src/main/java/blbl/cat3399/ui/SidebarNavAdapter.kt` | 侧边栏 RecyclerView；`ID_SEARCH/HOME/CATEGORY/DYNAMIC/LIVE/MY/CUSTOM` | 仅作为列表适配参考；视觉需按 wiliwili 重写 |
| `app/src/main/res/layout/activity_main.xml` | ConstraintLayout：左侧 `sidebar` + `main_container` + 用户浮层 | 可替换为 wiliwili 的左侧图标栏 + 内容区；不要沿用全部尺寸 |
| `app/src/main/res/layout/item_sidebar_nav.xml` | 侧边栏单项 MaterialCardView + 图标 + 可选文案 | 焦点/选中样式参考 |
| `app/src/main/res/layout/item_video_card.xml` | 视频卡片：16:9 封面、统计、标题、UP、进度、操作按钮 | wiliwili 视频卡片的 Android 对应实现起点 |
| `app/src/main/AndroidManifest.xml` | 单 Activity 主界面 + 多个详情/播放 Activity；全屏传感器横屏；FileProvider/下载服务/自更新权限 | 权限和横屏策略需按 wiliwili 目标设备重新设计，不能原样继承 |

**导航机制事实：**

- `MainActivity` 通过 `supportFragmentManager` 管理根 Fragment，`switchRoot()` 会缓存根 Fragment，并隐藏非当前根；`MainRootNavRegistry` 提供 `navId/key/titleRes/iconRes/startupPageKey/matchesFragment/fragmentFactory`。
- 详情页、设置、登录、播放器等仍使用独立 Activity；因此它不是严格的单 Activity 架构。
- 保存的是 `STATE_KEY_ROOT_NAV_ID`；没有 Navigation Component 的 route/back stack 抽象。
- 大量 DPAD/焦点逻辑直接写在 `MainActivity`（1,463 行）里；对 TV/遥控是有价值的实现参考，但不适合作为 wiliwili 移植的公共接口。

### 3.2 主题与设计令牌

| 文件 | 职责 | 审查要点 |
|---|---|---|
| `app/src/main/java/blbl/cat3399/core/theme/ThemePresets.kt` | 按偏好选择基础主题和 accent overlay；播放器固定蓝色主题，不随预设 | 主题入口清晰，可替换为 wiliwili 主题映射 |
| `app/src/main/java/blbl/cat3399/core/ui/ThemeColor.kt` | 从 theme attr 解析颜色/reference，并提供 fallback | 适合封装成 wiliwili token 读取工具 |
| `app/src/main/res/values/attrs.xml` | 自定义主题属性：页面背景、内容背景、对话框、On* 色、Accent 四态、焦点背景/描边 | **令牌化的核心**，建议保留并改名/扩充 |
| `app/src/main/res/values/themes.xml` | `Theme.Blbl.Base`、Dark、PinkLight、TvPinkIllustration、Player.Fixed、Launcher 主题 | 当前有 4 套视觉预设；MVP 建议只保留 wiliwili 主主题 + 播放器主题 |
| `app/src/main/res/values/styles.xml` | Material 组件样式、圆角、AlertDialog、TabLayout、播放器 OSD 尺寸 overlay | 可复用的组件样式骨架 |
| `app/src/main/res/values/colors.xml` | 暗色/粉色/经典插画色板、焦点色、accent 变体 | blbl 品牌色板，不应直接当作 wiliwili 色板 |
| `app/src/main/res/values-v31/themes.xml` | Android 12 SplashScreen 配置 | 可复用启动体验 |
| `app/src/main/java/blbl/cat3399/core/theme/LauncherAliasManager.kt` | 已废弃的启动图标别名同步 | 新项目可删除 |

**主题机制的关键优点：**

1. 业务布局不直接写死 `@color/blbl_bg` 等，而使用 `?attr/blblPageBackdrop`、`?attr/blblAccent`、`?attr/blblFocusStrokeColor` 等语义属性。
2. `app/build.gradle.kts` 的 `checkThemeTokens` 在 `preBuild` 阶段扫描 `res/layout*`，发现固定色板 token 就失败。
3. `ThemePresets.applyTo(activity)` 在 `BaseActivity.onCreate()` 中先于 `super.onCreate()` 执行，保证 `setContentView` 前主题生效。
4. 播放器有独立的 `Theme.Blbl.Player.Fixed`，避免主界面主题影响播放器 OSD 对比度。

**给 wiliwili 的改造建议：**

- 保留“语义属性 + theme overlay + 静态检查”三层机制，但属性命名改成 wiliwili 设计令牌，例如：
  - `wiliColorBackground` / `wiliColorSurface` / `wiliColorOnSurface`
  - `wiliColorAccent` / `wiliColorAccentContainer`
  - `wiliFocusStroke` / `wiliFocusBackground`
  - `wiliRadiusCard` / `wiliSpacingPage`（若继续用 XML attr）
- 把 wiliwili 的 `@theme/color/bilibili`、`@theme/color/grey_1`、`@theme/color/pink_1`、`@theme/font/grey`、`@theme/brls/background`、`@theme/color/line` 映射到 Android attr；不能直接 inflate borealis XML。
- 保留 `checkThemeTokens`，并把 wiliwili 旧固定色也列入 forbidden token，防止页面走回硬编码。

### 3.3 网络、WBI 与登录

| 文件 | 职责 | 审查要点 |
|---|---|---|
| `core/net/BiliClient.kt` | 单例网络入口；4 个 OkHttpClient：API、无 Cookie API、CDN、无 Cookie App CDN；WBI key 缓存 | 复用性最高，但应改为可注入实例，便于测试和多环境 |
| `core/net/CookieStore.kt` | 实现 OkHttp `CookieJar`；SharedPreferences 持久化；SESSDATA 判断、导出/替换快照 | 账号体系基础；明文持久化需评估安全 |
| `core/net/WbiSigner.kt` | WBI mixin key、参数排序/URL 编码/MD5 签名 | 纯 Kotlin，可单元测试，推荐复用 |
| `core/net/AppSigner.kt` | Android HD appkey/appsec 的 MD5 签名 | 硬编码第三方客户端密钥，合规/风控风险 |
| `core/net/PiliWebHeaders.kt` | `env/app-key/x-bili-mid/x-bili-aurora-eid` 等 Pili 风格请求头 | 与播放/风控逻辑强相关，需跟随 API 变化 |
| `core/net/WebCookieMaintainer.kt` | buvid3/4、b_nut、bili_ticket、Cookie 刷新、播放前健康检查 | 复用价值高；涉及设备指纹、隐私和平台风控 |
| `core/net/Http.kt` | `Call.await()` 协程封装；仅 IPv4 DNS | 小而稳，推荐复用 |
| `core/account/AccountSessionStore.kt` | 多账号 cookie + 凭据快照、切换、导出 | MVP 可退化为单账号 |
| `core/prefs/AppPrefs.kt` | 全部偏好；`BiliAppAuthSession`、用户 UA、API 源、主题、播放器参数 | 1,437 行，建议按 network/theme/player/ui 拆分 |
| `feature/login/QrLoginActivity.kt` | TV 端扫码登录：申请 `auth_code`、二维码、轮询、保存 access/refresh token 与 cookie_info | 可复用流程；UI 需按 wiliwili 重做 |
| `core/api/video/VideoSourceApi.kt` | `VideoSourceApi` 能力接口、Web/App Provider、`VideoApiGateway`、请求模型 | **API 抽象层最值得保留** |
| `core/api/video/web/WebVideoApi.kt` | Web 视频详情、推荐、排行、播放地址、播放器信息、字幕、快照 | MVP 的主力数据源 |
| `core/api/video/web/WebVideoMapper.kt` | JSON → `VideoDetail`/`VideoPlayStream`/`VideoCard` | 可复用，但需跟随接口变更 |
| `core/api/video/app/AppVideoApi.kt` | App 推荐与 UGC/PGC 播放地址（gRPC） | 仅 `VIDEO_RECOMMEND`、`VIDEO_PLAY_URL_*` 能力，MVP 可暂缓 |
| `core/api/BiliApi.kt` | 2,558 行巨门面：搜索、视频、直播、番剧、收藏、历史、关注、风控 | 不要整体当公共 API；先按用例切出薄 façade |
| `core/api/ApiSource.kt` | `WEB/APP` 来源选择与能力矩阵 | 设计合理，可复用到未来多数据源 |

**登录/网络链路（实际代码路径）：**

```text
QrLoginActivity
  → WebCookieMaintainer.ensureWebFingerprintCookies()
  → AppSigner.signQuery(local_id/platform/mobi_app)
  → POST passport.bilibili.com/x/passport-tv-login/qrcode/auth_code
  → 生成二维码
  → POST .../qrcode/poll
  → 保存 BiliAppAuthSession + cookie_info 到 CookieStore
  → WebCookieMaintainer.ensureBuvidActiveOncePerDay()
  → AccountSessionStore.saveCurrentSessionAsActive()
```

- `BiliClient` 拦截器默认补 `User-Agent`、`Referer`，API 还补 `Origin`；CDN 请求刻意不加 `Origin` 以避免部分 CDN 403。
- `ensureWbiKeys()` 从 `/x/web-interface/nav` 的 `wbi_img.img_url/sub_url` 提取 key，缓存 12 小时。
- `WbiSigner` 使用 64 位混排表 + MD5，算法独立、可测。
- `WebCookieMaintainer` 包含 buvid 激活、bili_ticket、Cookie refresh、RSA-OAEP 等逻辑；这部分与平台风控高度耦合，必须视为“会失效的集成点”，不能当成稳定协议。
- `AppSigner` 的 appkey/appsec 来自 PiliPlus 风格实现（代码注释自述），属于第三方客户端凭证复刻；上线前需法务/合规评估。

### 3.4 播放器与弹幕

| 文件 | 职责 | 审查要点 |
|---|---|---|
| `feature/player/engine/BlblPlayerEngine.kt` | 统一引擎接口：source、play/pause/seek/speed/release、listener | **最重要的可复用抽象**；目前是 `internal`，需提升为模块公共 API |
| `feature/player/engine/ExoPlayerEngine.kt` | Media3 ExoPlayer 实现：DASH/HLS、OkHttp、DASH 本地 MPD/代理、CDN failover、音频均衡、HLS 调试 | MVP 首选；与 `BiliClient`、播放器模型有耦合 |
| `feature/player/engine/IjkPlayerEngine.kt` | IjkPlayer 实现：Surface/Texture、native 加载、header 构建 | 可作为备选，但不应进入 MVP 必需路径 |
| `feature/player/engine/IjkPlayerPlugin.kt` | 运行时从固定 URL 下载 `libijkplayer.zip`、解压 `.so`、写入 stamp | 供应链风险；仅校验大小/ELF/版本号，未做签名校验 |
| `feature/player/engine/DashMpdGenerator.kt` | 将 B 站 DASH 信息生成可播放 MPD | ExoPlayer 无缝清晰度关键 |
| `feature/player/engine/DashLocalHttpProxy.kt` | 本地 HTTP 代理，服务于 DASH/清晰度切换 | 复杂但可复用 |
| `feature/player/engine/DashPlaybackCatalogResolver.kt` | DASH 轨道/清晰度解析 | 可复用 |
| `feature/player/engine/VolumeBalanceAudioProcessor.kt` | 音量平衡 | 可按需保留 |
| `feature/player/CdnFailoverDataSource.kt` | CDN 多候选切换 | 可复用，建议独立成 media 模块 |
| `feature/player/PlayerActivity.kt` | 4,107 行播放器总编排：播放、弹幕、设置、社交、自动续播/跳过、推荐、下载、评论等 | **不能整体当库复用**；建议拆 controller + screen |
| `feature/player/PlayerActivityStartPlayback.kt` | 解析详情、获取 playurl、选择 DASH/Progressive/VideoOnly、重载 | 与 Activity 扩展函数耦合深 |
| `feature/player/PlayerModels.kt` | `Playable`、`PlaybackConstraints`、`ResumeCandidate`、播放器会话设置 | 可复用为 engine 的输入模型 |
| `feature/player/danmaku/DanmakuEngine.kt` | 1,346 行弹幕引擎/轨道/排版 | 自研弹幕核心，可复用但测试/性能需验证 |
| `feature/player/danmaku/DanmakuView.kt` | Android 自定义 View 弹幕层 | 可复用到 wiliwili 风格播放器 |
| `feature/player/engine/PlayerEngineKind.kt` | Exo/Ijk 枚举与偏好值映射 | 简单，可保留 |

**播放器关键事实：**

- 统一引擎接口为 `BlblPlayerEngine`，支持 `setSource(PlaybackSource.Vod/Live)`、播放控制、监听器和 `selectVideoQuality()`。
- ExoPlayer 使用 Media3 1.8.0，并组合 `media3-exoplayer-dash/hls/ui/datasource-okhttp`。
- `PlayerActivity` 通过 Intent extras 启动，关键参数：`EXTRA_BVID`、`EXTRA_CID`、`EXTRA_EP_ID`、`EXTRA_AID`、`EXTRA_SEASON_ID`、`EXTRA_START_POSITION_MS`、`EXTRA_PLAYLIST_TOKEN`、`EXTRA_PLAYLIST_INDEX`。
- `PlayerActivity` 的职责远超播放器：弹幕、评论、点赞/投币/收藏、自动跳过赞助、视频快照、下载、推荐、调试等。直接复用它，会把 blbl 整个业务域带入项目。
- 播放器布局 `activity_player.xml` 使用 `androidx.media3.ui.PlayerView` + 自绘 `DanmakuView` + 多层 scrim/OSD，UI 可参考，但必须按 wiliwili 播放器视觉重构。
- IjkPlayer 实际 APK 不带 `.so`：`packaging.jniLibs.excludes` 排除 native 库，`IjkPlayerPlugin` 在需要时从 `cat3399.top` 下载，故**离线、可重现和企业/商店分发都不友好**；且仅做大小/ELF/`install_stamp` 校验，未做哈希/签名校验。

### 3.5 第三方二进制 AAR

`app/libs/ijkplayer-cmake-release.aar` 内容：

- `classes.jar`：`tv.danmaku.ijk.media.player.*` 与 `com.debugly.ijkplayer.BuildConfig`。
- `jni/arm64-v8a/libijkplayer.so`、`armeabi-v7a`、`x86`、`x86_64`。
- `AndroidManifest.xml` 声明 `package="com.debugly.ijkplayer"`、`minSdkVersion=21`。
- `proguard.txt` 为空，无 `consumer-rules`、无 LICENSE/NOTICE、无 POM。
- `libijkplayer.so` 内嵌字符串显示 FFmpeg 配置包含 `--disable-gpl --disable-nonfree`，libav* license 字符串为 `LGPL version 2.1 or later`；构建配置还显示 `--enable-openssl --enable-libxml2 --enable-demuxer=dash`。

AAR 不是 Maven 依赖，构建不会自动校验 checksum；源码仓库直接提交二进制，审查/升级/复现都困难。

---

## 4. 与 wiliwili UI 层对接的改造点

### 4.1 先划清“保留 wiliwili UI/操作”的边界

“保留 wiliwili 的界面/操作”有三种解释，工程策略完全不同：

| 方案 | 做法 | 优点 | 缺点 | 推荐度 |
|---|---|---|---|---|
| A. 原生 Android 重实现 wiliwili 视觉/交互 | 用 Android View/Compose 按 wiliwili 设计令牌重建导航、卡片、播放器控件；复用 blbl 能力层 | 可维护、可测试、Android 原生输入/生命周期稳定 | 需 UI 重做，不是逐像素克隆 | **推荐 MVP** |
| B. 嵌入 wiliwili C++/SDL/borealis 运行时 | 保留原有 C++ 页面，Android Activity 只做宿主；通过 JNI/Service 调 blbl 网络/播放 | 视觉/逻辑最接近原版 | 双运行时、双播放器、资源/输入/ABI/构建复杂；GPL 影响更强 | 仅作可行性验证 |
| C. 直接 fork blbl UI 再换皮 | 保留 blbl 页面结构，只改颜色/图标 | 最快 | 不是 wiliwili 的操作结构，长期会分裂 | 不满足目标，仅可做临时 demo |

**本文建议 A。** 若组织硬性要求 B，必须先验证：

1. borealis Android/SDL Activity 与目标 minSdk/ABI 的可行性；
2. wiliwili `libmpv` 与 Media3 只能二选一，否则 Surface/音频焦点/字幕/弹幕会冲突；
3. JNI 生命周期、Activity 重建、后台播放、权限、GPL 源码提供方式；
4. 该方案下 blbl 只能作为“原生服务/数据层”，其 UI 资产不再适用。

### 4.2 导航结构对接

wiliwili 的主导航是 `AutoTabFrame`：左侧图标 Tab + 内容区，底部还有 inbox/settings 入口；播放器则是独立的 `tabFrame` 多 Tab 结构。对应到 Android：

| wiliwili 概念 | blbl 现有实现 | Android 改造建议 |
|---|---|---|
| 左侧/顶部 `AutoTabFrame` | `MainActivity` + `SidebarNavAdapter` + `main_container` | 保留 `RootNavRegistry` 的“目的地注册表”模式，替换为 wiliwili 的首页/动态/我的/搜索/设置等 destination |
| Tab 图标/选中态/焦点态 | `item_sidebar_nav.xml` + `ThemeColor` + `stateListAnimator` | 用 wiliwili SVG 转 VectorDrawable，采用统一 selected/focused/pressed 三态 |
| Tab 切换时保留内容状态 | `switchRoot()` 缓存根 Fragment，`setMaxLifecycle(STARTED)` | 可保留缓存策略；若改用 Navigation Component，需显式保存滚动位置与列表状态 |
| 播放器内 Tab（评论/分P/推荐/UP 投稿） | `PlayerActivity` 多面板 + 多个 partial 文件 | 抽成 `PlayerTabHost` + `PlaybackController`，不要让 UI 直接操作 ExoPlayer/Ijk 内部 |
| 底部 inbox/settings | `MainActivity` 用户浮层 + Settings Activity | 按 wiliwili 保持底部固定入口；设置页先做最小集 |
| 返回栈 | 自定义 OnBackPressedCallback + Fragment back stack | 统一成 `NavHost` 或显式 `WiliBackDispatcher`；TV Back 行为要覆盖 |

**建议的导航契约：**

```kotlin
data class WiliDestination(
    val id: String,                 // 稳定路由 id，用于 saved state
    val titleRes: Int,
    val iconRes: Int,
    val startupKey: String? = null,
    val enabled: () -> Boolean = { true },
    val createScreen: () -> WiliScreen,
)

interface WiliScreen { /* 页面生命周期/焦点/刷新/返回处理 */ }
```

`MainRootNavRegistry` 可作为迁移起点，但不要保留 `navId: Int` 与 blbl Fragment 类的硬绑定；改为 route id + `WiliScreen` 工厂，避免新 UI 继续依赖 blbl `R` 资源和业务 Fragment。

### 4.3 主题/资源对接

**不要直接复用：**

- `resources/xml/**`：这些是 borealis XML，依赖 `brls:` 命名空间、`@theme/...`、`AutoTabFrame` 等自定义标签，Android 的 LayoutInflater 无法加载。
- `wiliwili/source/**` 的 C++ 视图类：不能作为 Android View 使用。
- wiliwili 的既有色板命名（`grey_1`、`pink_1`、`bilibili`）与 Android Material 语义不完全一一对应。

**可以转换/复用：**

- `resources/svg/**`：优先转成 Android VectorDrawable；转换后检查是否含不支持的 SVG 特性。
- `resources/pictures/**`：按资源许可证逐项审核后放入 `drawable-nodpi` 或合适密度目录。
- `resources/font/**`：先核对 `FREEWARE.txt`/`LICENSE.txt`，再以 Android font resource 引入；不要默认打包第三方字体。
- `resources/i18n/**/*.json`：写脚本生成 `res/values*/strings.xml`，不要手工硬编码；保留 key 的可追溯映射。
- wiliwili 的 `@theme/color/bilibili` 等映射到 Android 语义 token，而不是复制字面色值。

**推荐主题映射表：**

| wiliwili/设计语义 | Android attr 建议 | 说明 |
|---|---|---|
| 主背景 `@theme/brls/background` | `?attr/colorBackground` / `wiliColorBackground` | 页面根背景 |
| 卡片/灰底 `@theme/color/grey_1` | `wiliColorSurface` | 卡片、侧栏选中容器 |
| 主文字 | `?attr/colorOnSurface` / `wiliColorOnSurface` | 避免硬编码白/黑 |
| 次级文字 `@theme/font/grey` | `?android:attr/textColorSecondary` / `wiliColorOnSurfaceSecondary` | 统计、时间、辅助说明 |
| B 站强调色 `@theme/color/bilibili` | `wiliColorAccent` | 选中、进度、强调按钮 |
| 粉色容器 `@theme/color/pink_1` | `wiliColorAccentContainer` | 选中背景、标签 |
| 分隔线 `@theme/color/line` | `wiliColorDivider` | 列表分隔 |
| 焦点描边/底色 | `wiliFocusStroke` / `wiliFocusBackground` | TV/遥控必须保留，不能只做 Material ripple |

保留 blbl 的 `ThemeColor.resolve()` 思路和 `checkThemeTokens` 静态检查；建议把 `colorPrimary` 等 Material 属性和 wiliwili token 做显式映射，避免 Material 组件自行取色造成不一致。

### 4.4 数据/API 对接

建议在 wiliwili UI 与 blbl API 之间加一层 **Repository/State 适配层**，不要让 UI 直接调用 `BiliApi` 巨型对象：

```text
wiliwili Android UI (View/ViewModel)
        ↓  WiliHomeState / WiliVideoDetailState / WiliPlayerState
WiliRepository（项目自己的薄层）
        ↓  只暴露需要的用例
VideoApiGateway / SearchApi / QrLoginService / PlaybackSourceResolver
        ↓
BiliClient + WebVideoApi + Mapper
```

首批建议暴露的用例：

- `observeLoginState()` / `loginByQr()` / `logout()`
- `loadRecommendFeed(page)` / `loadPopular(page)` / `searchVideos(keyword, page)`
- `loadVideoDetail(bvid/aid)` / `loadVideoTags()` / `loadRelatedVideos()`
- `resolvePlayback(bvid, cid, epId, qn, codec)` → 自己的 `PlayableMedia` 模型
- `loadDanmaku(cid)` / `loadSubtitles(cid)`
- `reportProgress(...)`（首版可延后）

数据映射原则：

1. blbl `VideoCard`/`VideoDetail` 可以复用为数据传输对象，但不要让它们直接出现在 wiliwili UI 状态里；建议转为 `WiliVideoCardUiModel`，以便未来替换数据源。
2. `VideoSourceApi` 的能力矩阵值得保留：Web 优先，App 只补 Web 不足的能力。
3. 统一 API 错误模型：把 `BiliApiException`、HTTP IOException、解析异常归一为 UI 可展示的 `WiliError`。
4. 统一协程调度与取消：页面离开即取消加载；播放地址刷新要有 generation/token，避免旧响应覆盖新视频。

### 4.5 播放器接口对接（重点）

当前 `PlayerActivity` 不能被 wiliwili 播放器界面直接“换皮”复用，原因是 UI 与播放引擎、业务社交、弹幕、下载等强耦合。建议先抽一个 **与 Android Activity 无关的播放会话接口**：

```kotlin
interface WiliPlaybackController {
    val state: StateFlow<WiliPlaybackState>

    suspend fun prepare(media: WiliPlayableMedia, startPositionMs: Long?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun selectQuality(qn: Int): Boolean
    fun setDanmakuEnabled(enabled: Boolean)
    fun release()
}

data class WiliPlaybackState(
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val qualityQn: Int?,
    val error: WiliPlaybackError?,
    val firstFrameRendered: Boolean,
)
```

实现时可优先适配：

- `BlblPlayerEngine` 接口本身已接近这个目标，建议从 `internal` 提升并移除对 blbl `AppPrefs`/`AppLog` 的直接静态依赖。
- `ExoPlayerEngine` 首版只保留：Media3 Player、DASH/HLS、OkHttp、基本 seek/speed、字幕；把 CDN failover 和无缝 DASH 作为增强项。
- `IjkPlayerEngine` 推迟；若必须支持，改为“内置 AAR 或自建 Maven + 明确许可证 + 校验哈希”的方式，不做运行时下载。
- 播放器 UI 用 `PlayerView` 或纯 Surface + 自绘控件；具体 wiliwili 控件视觉由 UI 规格定义，controller 不感知控件层级。
- 弹幕先接入 `DanmakuView` 的只读/基础配置；复杂滤镜、AI 屏蔽、赞助跳过、社交操作逐步后置。

### 4.6 输入/焦点对接

wiliwili 以手柄/遥控焦点为主，触屏为辅；blbl 已有大量此类基础设施：

- `core/ui/FocusTreeUtils.kt`
- `core/ui/FocusReturn.kt`
- `core/ui/DpadGridController.kt`
- `core/ui/RecyclerViewFocus.kt`
- `core/ui/TabContentFocus.kt`
- `core/ui/BackButtonSizingHelper.kt`
- `core/tv/RemoteKeys.kt`
- `MainRootNavRegistry` + `SidebarFocusHost`
- `BaseActivity` 的 UiScale/UiDensity 处理

建议：

1. 保留“焦点是显式状态”的原则：列表项、卡片、播放器控制条都必须可 `focusable` 且有明显 focus 视觉。
2. 把 wiliwili 的手柄/键盘映射集中到 `WiliInputMapper`，不要让每个页面自行判断 `KeyEvent`。
3. 触摸和遥控共用同一套 View；触摸点击不应依赖 `requestFocus()` 的副作用。
4. DPAD 左右在播放器 OSD 中用于 seek，上下用于切换控制区；TV Back 双击退出或返回上一级要有统一策略。
5. 焦点回收：从播放器返回列表时恢复原卡片焦点；从详情页返回时保持滚动位置。blbl 的 `FocusReturn`/缓存 Fragment 可作为参考。

### 4.7 资源与 i18n 对接成本

- 65 个 blbl layout 不是可复用 UI 资产；真正可复用的是主题属性、焦点样式、卡片信息层级和 OSD 结构。
- wiliwili 的 XML 也需要“翻译”为 Android XML/Compose，而不是复制。
- 现有 blbl `strings.xml` 只有 137 行，wiliwili 多语言 JSON 更完整；建议以 wiliwili 的 key 为准生成 Android `strings.xml`，再补 blbl API 错误文案。
- 主题/图标/字体/图片要逐项记录来源和许可证，尤其是 B 站图标、第三方字体、i18n 文案。

---

## 5. 可复用清单（按优先级）

> 以下“可直接复用”均以**许可证已获授权**为前提；否则只能作为接口/行为参考。

### 5.1 第一优先级：MVP 必需

| 模块/文件 | 复用方式 | 改造量 | 风险 |
|---|---|---|---|
| `core/net/WbiSigner.kt` | 直接复制/抽成无 Android 依赖的工具 | 低 | 算法随平台变化 |
| `core/net/Http.kt` | 直接复制 | 极低 | 低 |
| `core/net/CookieStore.kt` | 复制并改为可注入、加密存储可选 | 中 | 明文持久化、备份泄露 |
| `core/net/BiliClient.kt` | 复制后拆成 `BiliHttpClient` + 配置注入 | 中 | 单例静态状态、拦截器耦合 AppPrefs |
| `core/net/WebCookieMaintainer.kt` | 保留核心维护流程 | 中高 | 平台风控变化频繁 |
| `core/net/PiliWebHeaders.kt` | 复制 | 中 | 请求头协议可能变化 |
| `core/account/AccountSessionStore.kt` | MVP 可只保留单账号；多账号后续 | 中 | 凭据/备份安全 |
| `core/api/video/VideoSourceApi.kt` | 保留接口、能力矩阵和 Gateway | 中 | `internal` 需开放；API 模型需稳定 |
| `core/api/video/web/WebVideoApi.kt` + `WebVideoMapper.kt` | Web-only MVP 主力 | 中 | B 站接口变更、WBI 风控 |
| `core/model/VideoCard.kt` 等 | 作为 DTO 或映射源 | 低 | 与 UI 模型耦合 |
| `feature/login/QrLoginActivity.kt` | 抽成 `QrLoginService` + 自定义 UI | 中 | 二维码轮询状态、cookie 写入 |
| `feature/player/engine/BlblPlayerEngine.kt` | 提升为公共接口 | 低中 | 需去 `internal` 和静态依赖 |
| `feature/player/engine/ExoPlayerEngine.kt` | 作为首版播放核心 | 中高 | 依赖 BiliClient、DASH/字幕/日志 |
| `feature/player/engine/DashMpdGenerator.kt` | 保留 | 中 | 清晰度切换兼容性 |
| `feature/player/engine/DashPlaybackCatalogResolver.kt` | 保留 | 中 | 同上 |
| `feature/player/engine/DashLocalHttpProxy.kt` | 视需要保留 | 中高 | 本地代理生命周期/端口/网络 |
| `feature/player/CdnFailoverDataSource.kt` | 保留或放二期 | 中 | CDN 候选变化 |
| `feature/player/PlayerModels.kt` | 复用 `Playable`/`PlaybackConstraints`/`ResumeCandidate` | 低中 | 与 UI 会话设置耦合 |
| `feature/player/danmaku/DanmakuEngine.kt` + `DanmakuView.kt` | 保留基础弹幕 | 中 | 性能、线程、自定义配置多 |
| `core/paging/PagedGridStateMachine.kt` | 直接复用 | 低 | 无 Android 依赖，易单测 |
| `core/ui/ThemeColor.kt` | 复制并改名 | 低 | 依赖 `R.attr` 常量 |
| `core/ui` 的焦点/DPAD 工具 | 选择性复制 | 中 | 与 blbl 页面强相关，需解耦 |
| `core/image/ImageLoader.kt` | MVP 可先用；后续换 Coil | 低 | 手写图片管线的缓存/取消能力有限 |
| `core/prefs/AppPrefs.kt` | 拆出主题/播放/网络三组 | 中高 | 1,437 行，横向耦合 |

### 5.2 第二优先级：体验补齐

| 模块 | 用途 | 建议 |
|---|---|---|
| `feature/video/VideoDetailActivity.kt` 及 Header/Playlist/Tag 适配器 | 视频详情页信息结构 | 保留数据请求与状态逻辑，重做布局和交互 |
| `feature/search` | 搜索建议/热词/结果 | 保留 `SearchApi` 和分页，重做 UI |
| `feature/my/MyHistoryFragment`、`MyFav*` | 历史/收藏 | 二期 |
| `feature/following` | UP 主页/关注列表 | 二期 |
| `feature/player/PlayerSettings.kt` | 清晰度/倍速/字幕/弹幕设置模型 | 只保留必要选项；UI 重做 |
| `feature/player/danmaku/CacheManager.kt` | 弹幕分段缓存 | 可复用 |
| `core/tv/RemoteKeys.kt` / `TvDevice.kt` | 遥控/TV 判定 | 可复用 |
| `core/ui/popup/*` | 统一弹窗 | 可复用，但样式需替换 |
| `core/theme/ThemePresets.kt` | 主题应用流程 | 保留流程，替换预设与色板 |
| `feature/settings` | 设置页 | 仅保留最小项：主题、播放器、网络、关于/许可证 |

### 5.3 MVP 明确剥离/延后

| 模块 | 原因 |
|---|---|
| `core/update/ApkUpdater`、`ApkUpdateFlow` | 自更新 + `REQUEST_INSTALL_PACKAGES` + FileProvider，权限和供应链风险 |
| `core/download/*`、`DownloadService`、`DownloadsActivity` | 前台服务、通知、存储权限、MediaStore 兼容，首版不必要 |
| `feature/live/*`、`LivePlayerActivity` | 直播流、WebSocket、弹幕、SC 另立子项目 |
| `feature/player` 中的社交/评论/赞助跳过/视频快照/下载 | 4,000+ 行 Activity 的主要复杂度来源 |
| `core/api/video/app/*`、`app/src/main/proto/*`、gRPC 依赖 | 只有 App 推荐和 U/V 播放地址使用；Web-only 可先删 |
| `feature/risk/GaiaVgateActivity` | 风控验证流程复杂，后续按实际触发再补 |
| `feature/custom/*`、`CustomPageConfig` | 自定义首页/我的页，非 MVP |
| `core/emote`、`core/note`、`core/io` | 表情、笔记、导出，边缘能力 |
| `IjkPlayer` AAR 与运行时插件 | 首版统一 ExoPlayer；减少 ABI/许可证/下载风险 |
| `feature/settings` 的绝大多数设置项 | 设置页 2,983 行交互处理器不适合首版 |
| 多主题/Launcher Alias | 只保留 wiliwili 主主题和可选播放器主题 |

---

## 6. 改造成本估算

> 估算口径：1 名熟悉 Android/Media3 的工程师；已有部分网络/播放器代码可用；不含法务审批时间；不含完全 clean-room 重写。若许可证要求完全独立实现，成本至少 ×2–3。

| 工作项 | 内容 | 估算（人日） | 备注 |
|---|---|---:|---|
| 工程基线与模块化 | 包名/签名/依赖收敛、拆 `core`/`media`/`ui`、去掉无关权限 | 3–5 | 单模块拆分层是主要工作 |
| 网络/WBI/Cookie/登录 | 抽取 BiliClient、Cookie、WBI、二维码登录，补单元测试 | 5–8 | 风控变化不可控 |
| 主题令牌 | 建立 wiliwili token、attr、sty、主题覆盖、检查规则 | 5–8 | 视觉细节多 |
| 主壳与导航 | 左侧/顶部 Tab、焦点、返回栈、启动页 | 5–8 | 可参考 blbl 但需重写 |
| 首页/搜索 | 推荐流、分页、搜索建议/热词/结果 | 8–12 | 含错误/空态/加载态 |
| 视频详情 | 详情、分P、标签、UP 信息、相关推荐 | 6–10 | PGC 可后置 |
| Exo 播放器 MVP | 播放、暂停、seek、清晰度、倍速、字幕、CDN 基本容错 | 8–12 | 不含 Ijk/无缝/社交 |
| 弹幕基础 | 拉取、渲染、开关、透明度/字号基础配置 | 5–8 | 复杂过滤/AI 后置 |
| 最小设置/关于/许可证 | 主题、播放器、网络、第三方声明 | 2–4 | 必须包含开源声明 |
| 设备与兼容测试 | API 21/23/31/34/36、TV 遥控、触屏、平板 | 5–8 | 至少 1 台真机 + 模拟器矩阵 |
| **MVP 合计** | 上述项目 | **约 52–83 人日（10–17 周单人）** | 合理团队并行可压到 4–6 周 |
| 直播/番剧/收藏/历史 | 页面与播放链路 | 15–25 | 独立里程碑 |
| IjkPlayer 合规接入 | 构建/分发/哈希/许可证/测试 | 5–10 | 若保留运行时下载则另加合规风险 |
| 下载/后台服务 | FGS、通知、存储、断点续传 | 8–12 | 有系统兼容成本 |
| App gRPC/风控/社交/评论 | 高耦合业务 | 15–25 | 建议长期后置 |
| **完整功能接近 blbl** | 非单纯 UI 移植 | **额外 43–72 人日** | 不含法务 |

**成本结论：**

- 做到“能登录、能刷推荐、能搜索、能看详情、能播放、能开关弹幕”的 MVP，合理范围是 **4–6 周团队时间**（或单人 10–17 周）。
- 若目标是 wiliwili 功能完整对齐（直播、番剧、收藏、历史、评论、设置、下载等），应规划 **3–6 个月**，而不是两周换皮。
- 需要特别预留 **20%–30%** 的维护成本给 B 站接口/WBI/Cookie 变化；这部分不是一次开发即可结束。

---

## 7. MVP 建议

### 7.1 MVP 目标

首版只验证一条完整主链路：

```text
启动 → 可选扫码登录 → 首页推荐 → 搜索 → 视频详情 → ExoPlayer 播放
     → 清晰度/倍速/字幕/弹幕基础控制 → 返回保留进度
```

### 7.2 MVP 范围（必须有）

- [ ] 单 Activity 或少量 Activity 的 wiliwili 风格主壳：左侧/顶部 Tab + 内容区。
- [ ] wiliwili 主题令牌 + 焦点三态 + 播放器 OSD 基础视觉。
- [ ] Web API 数据源：推荐、热门/分区、搜索、视频详情、播放地址、字幕/弹幕接口。
- [ ] QR 扫码登录；Cookie 持久化；登录状态恢复。
- [ ] 首页/搜索/详情/播放器四个核心页面。
- [ ] ExoPlayer + Media3 DASH/HLS，至少支持 AVC/AAC、清晰度切换、倍速、字幕开关、基础弹幕。
- [ ] 触摸和遥控/键盘双输入；TV 焦点不丢失。
- [ ] 基础错误页、加载/空态、网络失败重试。
- [ ] 关于页 + 第三方许可证清单。
- [ ] 默认关闭自更新、Ijk 插件下载、下载服务和非必要权限。

### 7.3 MVP 明确不做

- 直播、番剧/PGC、动态、自定义首页、关注/收藏/历史、评论、点赞投币收藏。
- IjkPlayer、无缝切换清晰度、CDN 高级切换、视频下载、自动更新、崩溃上传。
- App gRPC/API 源切换、多账号、赞助跳过、AI 弹幕过滤、视频快照。
- 多主题/启动图标别名；只保留 wiliwili 主主题。

### 7.4 推荐里程碑

| 里程碑 | 交付物 | 验收 |
|---|---|---|
| M0 法务/基线 | 源码授权结论；项目可构建；依赖清单/许可证清单 | 构建可复现；无未授权代码进入仓库 |
| M1 能力层 | 网络/WBI/Cookie/登录/视频 API + 单测 | 能登录、拉到推荐/搜索/详情/播放地址 |
| M2 主壳 | wiliwili 风格导航、主题令牌、首页/搜索/详情 | 鼠标/触摸/遥控均可操作；主题检查通过 |
| M3 播放器 | Exo 播放器 + 基础 OSD + 弹幕/字幕 | 连续播放 30 分钟；seek/切清晰度/返回恢复进度稳定 |
| M4 打磨 | 错误处理、性能、兼容性、许可证页/关于页 | API21/TV/触屏冒烟通过；发布包无多余权限 |

### 7.5 MVP 验收标准建议

1. 冷启动到首页可交互 ≤ 3 秒（目标设备，非严格 SLA）。
2. 扫码登录后重启 App，登录态仍有效；退出后 Cookie 清理干净。
3. 推荐/搜索/详情/播放四条链路中任一步网络失败，都有可恢复错误态，不出现崩溃。
4. 播放器支持播放/暂停/seek/倍速/清晰度/字幕/弹幕开关；返回后重新进入能续播。
5. TV 遥控从侧栏进入内容、从内容进入播放器、从播放器返回原卡片，焦点链路完整。
6. 默认 APK 不包含/download 触发未声明的敏感权限；无运行时下载可执行代码。
7. 发布包附带完整第三方许可证与 wiliwili GPLv3 源码获取说明（若本案最终分发）。

---

## 8. 建议的目标模块结构

为了避免继续维护单模块 78,000 行 Kotlin，建议在 fork 后尽快切成如下结构：

```text
:app                     # 入口 Activity、wiliwili shell、导航、DI
:core:model              # 纯 Kotlin DTO/UI 状态模型
:core:network            # OkHttp、Cookie、WBI、HTTP、错误模型
:core:auth               # QR 登录、账号会话、Cookie 维护
:core:data               # BiliApi 薄面、Repository、Mapper、分页
:media:api               # WiliPlaybackController / PlayableMedia
:media:exo               # Media3 实现、DASH/HLS、字幕
:media:danmaku           # 弹幕引擎/View（可选独立）
:ui:theme                # wiliwili 设计令牌、主题、样式
:ui:components           # 卡片、Tab、焦点、弹窗、加载/空态
:feature:home
:feature:search
:feature:video-detail
:feature:player
:feature:settings
:feature:about-licenses
```

初期不必全部拆完；至少先把 **network/auth/data/media** 从 `:app` 移出。这样 wiliwili UI 与 blbl 能力层之间的依赖方向是单向的：

```text
feature/*  →  ui/*  →  media/api + core/data
                         → core/network/auth/model
```

禁止出现：

- `core/network` 依赖任何 `R` 或 UI；
- `media/exo` 依赖具体 `PlayerActivity`；
- wiliwili UI 直接读 `BiliClient.prefs` 或直接构造 `VideoPlayRequest`；
- 一个页面直接调用 2,558 行 `BiliApi` 的全部能力。

---

## 9. 许可证与第三方依赖注意事项

> 本节是工程合规提示，不构成法律意见；分发前必须由法务/合规负责人确认。

### 9.1 首要风险：blbl-main 未声明许可证

已核查：

- 仓库根目录没有 `LICENSE`、`COPYING`、`NOTICE`。
- `app/src/main/java` 中未发现 SPDX 头或统一版权声明。
- README 仅写了“感谢”“页面和操作逻辑参考 BBLL”，这不能替代开源许可证或书面授权。

法律含义（一般性）：

- 无许可证的代码默认由作者保留全部权利。未经许可的复制、修改、分发、并入其他项目通常不可行。
- **不能因为代码在 GitHub 上公开就默认可以商用/开源复用。**
- 如果 wiliwili GPLv3 代码与 blbl 未授权代码组成一个可分发的组合作品，许可证链会断裂。
- README 所述“大部分页面和操作逻辑抄袭 BBLL”意味着还可能存在上游 BBLL 的权利主张；需要做清晰的代码来源追溯。

**行动项（阻断发布）：**

1. 联系 blbl-main 作者/上游仓库，取得明确的书面授权（允许修改、再分发、许可证兼容），并保留记录。
2. 若拿不到授权：
   - 只把 blbl-main 当行为/接口参考，进行**干净房间重写**；或
   - 仅复用可独立证明来源的公开协议/算法/公开 API 文档，不复制源码/资源。
3. 对 `BBLL`、`PiliPlus` 等被感谢项目的许可证做上游追溯；不要把“参考”当作授权。
4. 在最终仓库保留 `LICENSE`、`NOTICE`、第三方依赖清单和源码获取方式。

### 9.2 wiliwili 的 GPLv3 影响

- wiliwili 根 `LICENSE` 为 **GNU GPLv3**。
- 若分发包含 wiliwili 代码/资源的衍生作品，通常需要按 GPLv3 提供对应源码、许可证文本和修改说明，并保证用户可重新构建/替换。
- GPLv3 与 Apache-2.0 兼容；LGPL-2.1-or-later 在满足条件时可与 GPLv3 组合，但必须逐项确认。
- 如果只是“参考 wiliwili 的界面设计思想”并独立实现 Android UI，通常不构成复制代码；但以下内容风险高，需单独确认：
  - 直接复制 `resources/svg/**`、`resources/pictures/**`、字体、i18n 文本；
  - 直接翻译/复制 C++ 业务逻辑；
  - 将 wiliwili 作为库链接进同一 APK。
- 若项目整体以 GPLv3 发布，则必须同步满足 blbl 代码的授权条件；反过来，若 blbl 代码无许可证，GPLv3 也不能“覆盖”它。

### 9.3 IjkPlayer / FFmpeg / AAR

已从 AAR 观察到的证据：

- `jni/*/libijkplayer.so` 内嵌 FFmpeg 版本字符串：`FFmpeg version n7.1.1-4-g834d732`。
- FFmpeg 配置含 `--disable-gpl --disable-nonfree`，libav* license 字符串为 `LGPL version 2.1 or later`。
- AAR 内没有 LICENSE/NOTICE，`proguard.txt` 为空。
- IjkPlayer 的 Java 层/上游项目也需要按其实际 LICENSE 核验；不要仅凭 native 字符串判断整个 AAR 的许可。

**风险：**

1. LGPL 合规不是“附一个 LICENSE 文件”这么简单。若 `.so` 为静态合并的 FFmpeg，需要评估如何满足 LGPL 对“可替换库/重新链接”的要求。
2. 运行时从第三方域名下载 `.so` 会引入：
   - 供应链篡改风险；
   - 可重现构建问题；
   - 应用商店/F-Droid/企业 MDM 可能禁止动态下载可执行代码；
   - 分发者仍需对下载的二进制承担许可证义务。
3. 现有插件安装仅校验大小、ELF 头和 stamp，**没有 SHA-256/签名验证**；生产使用必须补签名或哈希白名单。
4. 建议 MVP 不引入 Ijk；若后续必须支持，优先：
   - 通过自建 Maven/内置 AAR 固定版本；
   - 附带完整 LICENSE/NOTICE；
   - 提供 FFmpeg/Ijk 源码获取方式与构建脚本；
   - 对下载产物做签名/哈希校验，并提供可关闭开关。

### 9.4 其他第三方依赖

| 依赖 | 预期许可证 | 发布前动作 |
|---|---|---|
| AndroidX / Material / ConstraintLayout / RecyclerView / ViewPager2 / Lifecycle | Apache-2.0 | 生成依赖清单，附 NOTICE |
| OkHttp / kotlinx-coroutines | Apache-2.0 | 附 LICENSE/NOTICE |
| Media3 | Apache-2.0 | 附 LICENSE/NOTICE；确认版本锁与 CVE 修复 |
| protobuf-javalite | BSD-3-Clause | 附版权声明；确认生成代码来源 |
| gRPC | Apache-2.0 | 附 LICENSE；MVP 可删 |
| ZXing core | Apache-2.0 | 附 LICENSE |
| Brotli `org.brotli:dec` | 需以 POM/LICENSE 核验 | 不要凭印象写许可证 |
| `javax.annotation-api` | 常见为 CDDL/GPL 双许可 | 核验 POM；仅为 compileOnly 也要记录 |
| IjkPlayer AAR | LGPL/FFmpeg 合规重点 | 见上节 |
| B 站 API/proto/内部接口 | 无公开授权保证 | 评估服务条款、接口版权/合同风险；不要打包无法证明来源的专有资源 |
| wiliwili 图标/字体/图片/i18n | 分别核对文件级许可 | 保留 `resources/font/LICENSE.txt`、`resources/material/LICENSE.txt` 等；无授权的不入包 |

### 9.5 商标、隐私与安全

- `blbl`、`bilibili`、B 站 Logo、wiliwili 名称/图标都涉及商标与品牌；发布前重命名并替换图标/启动图。
- `BiliClient`、`CookieStore`、`AppPrefs` 使用 SharedPreferences 明文保存 Cookie/凭据；`allowBackup="true"` 还可能让备份带走登录态。建议：
  - 使用 Android Keystore/EncryptedSharedPreferences（或等价的加密方案）；
  - `allowBackup=false` 或提供备份排除规则；
  - 明确隐私政策与数据最小化。
- `AndroidManifest.xml` 当前包含 `usesCleartextTraffic="true"`、`REQUEST_INSTALL_PACKAGES`、`POST_NOTIFICATIONS`、前台服务、`WRITE_EXTERNAL_STORAGE` 等；MVP 应删除未使用权限并将 cleartext 关闭。
- `AppSigner` 硬编码第三方客户端 appkey/appsec；`PiliWebHeaders` 含设备/用户标识请求头；需评估对平台协议和当地法规的影响。
- `core/update/ApkUpdater` 会下载并安装 APK；即使保留，也必须做签名校验、HTTPS 锁定、用户明确同意和分发合规审查。
- 日志/崩溃上传 (`core/log`) 可能包含设备标识、UID、URL；MVP 建议默认关闭或仅本地。

### 9.6 发布前许可证检查清单

- [ ] 确认 blbl-main 每个复用文件的来源与授权；无授权则替换/重写。
- [ ] 确认与 wiliwili GPLv3 的组合分发方式；准备对应源码与构建说明。
- [ ] 生成完整第三方依赖清单（含传递依赖、AAR、字体、图片、i18n）。
- [ ] 附 LICENSE / NOTICE / 源码获取入口 / 修改说明。
- [ ] Ijk/FFmpeg 单独完成 LGPL 评估，或从 MVP 删除。
- [ ] 替换未授权品牌/图标/API 专有内容。
- [ ] 去除明文存储、明文流量、自更新和非必要危险权限。

---

## 10. 主要风险与缓解措施

| 风险 | 影响 | 缓解 |
|---|---|---|
| blbl 无许可证 | 不可合法分发，可能阻断整个项目 | 先取得授权或 clean-room；法务前置 |
| wiliwili GPLv3 与 blbl 授权不兼容 | 组合作品无法合规发布 | 许可证兼容审查；必要时只复用接口/设计 |
| B 站接口/WBI/Cookie 频繁变化 | 登录、推荐、播放随时失效 | 抽象数据源、版本化解析器、契约测试、快速回滚 |
| 4,000+ 行 PlayerActivity 难拆 | UI 改造风险高、回归面大 | 先抽 `PlaybackController`，MVP 不做社交/下载/自动跳过 |
| 单模块 78,000 行 Kotlin | 编译慢、边界混乱、难测试 | 先拆 core/network/auth/data/media；逐步拆 feature |
| AGP 8.5.2 + compileSdk 36 | 构建警告/未来失败 | 升级 AGP 或降到官方支持 compileSdk；不要长期依赖 suppress |
| `minSdk 21` + Media3/新 API | Android 5 设备兼容问题 | 建立 API 21/23 真机/模拟器冒烟；不要使用未做兼容的 API |
| Ijk 运行时下载 `.so` | 供应链、合规、可重现性 | MVP 删 Ijk；后续内置固定版本 + 哈希/签名校验 |
| 明文 Cookie/备份 | 账号泄露、隐私风险 | 加密存储、关闭备份、最小化日志 |
| 请求头/AppSigner 复刻 | 平台风控、协议/合规风险 | 法务评估；优先 Web API；错误可恢复 |
| 主题/布局硬编码 | wiliwili 视觉无法统一 | 复用 attr+overlay+`checkThemeTokens`，禁止固定色 |
| 焦点/触摸双栈 | TV 遥控体验退化 | 统一 `WiliInputMapper` + 焦点回收测试 |
| wiliwili XML/资源直接复制 | Android 无法加载/许可证问题 | 建立资源转换/许可证登记流程 |

---

## 11. 直接回答“哪些能复用、怎么复用、先做什么”

### 11.1 最小可运行 Android MVP 的代码复用清单

**必须复用（先复制后解耦）：**

1. `core/net/WbiSigner.kt`
2. `core/net/Http.kt`
3. `core/net/CookieStore.kt`
4. `core/net/BiliClient.kt`（拆成可注入 HTTP 层）
5. `core/net/WebCookieMaintainer.kt`（保留必要维护逻辑）
6. `core/api/video/VideoSourceApi.kt` + `core/api/video/web/WebVideoApi.kt` + `WebVideoMapper.kt`
7. `core/model/VideoCard.kt` / `VideoDetail.kt` / `VideoPlayStream.kt`
8. `feature/login/QrLoginActivity.kt` 的登录流程（重做 UI）
9. `feature/player/engine/BlblPlayerEngine.kt` + `ExoPlayerEngine.kt`
10. `feature/player/engine/DashMpdGenerator.kt` + `DashPlaybackCatalogResolver.kt`
11. `feature/player/CdnFailoverDataSource.kt`（可选）
12. `feature/player/danmaku/DanmakuEngine.kt` + `DanmakuView.kt`（基础能力）
13. `core/paging/PagedGridStateMachine.kt`
14. `core/ui/ThemeColor.kt` + 焦点/DPAD 工具中可独立的部分
15. `app/build.gradle.kts` 的主题 token 检查机制

**建议参考但重写：**

- `MainActivity`、`MainRootNavRegistry` 的注册表思想。
- `activity_main.xml`、`item_sidebar_nav.xml`、`item_video_card.xml` 的信息层级/焦点态。
- `PlayerActivity` 的 OSD 状态机、播放失败重试、续播语义。
- `ThemePresets` 的主题 overlay 流程。
- `QrLoginActivity` 的轮询状态码处理。

**MVP 直接不要：**

- `IjkPlayerPlugin` 运行时下载、Ijk AAR。
- `ApkUpdater`/自动更新/FileProvider/REQUEST_INSTALL_PACKAGES。
- 下载服务、直播、SponsorBlock、社交动作、评论、番剧、多账号、自定义页。
- App gRPC/Proto（Web-only 时）。

### 11.2 最小可运行文件的“理想拆分”

```text
app/
  WiliApp.kt
  MainActivity.kt                  # 只负责宿主 + 导航
  ui/navigation/WiliNavRegistry.kt
  ui/theme/WiliTheme.kt
  feature/home/HomeScreen.kt
  feature/search/SearchScreen.kt
  feature/detail/VideoDetailScreen.kt
  feature/player/PlayerScreen.kt
  feature/login/LoginScreen.kt
  feature/settings/SettingsScreen.kt
core/network/
  BiliHttpClient.kt
  CookieStore.kt
  WbiSigner.kt
  WebCookieMaintainer.kt
core/auth/QrLoginService.kt
core/data/
  VideoRepository.kt
  SearchRepository.kt
  Mappers.kt
media/
  PlaybackController.kt
  ExoPlaybackController.kt
  DanmakuController.kt
```

---

## 12. 附录：关键证据索引

### 12.1 构建/清单

- `app/build.gradle.kts`：`minSdk 21`、`compileSdk/targetSdk 36`、AGP 兼容抑制、依赖、`checkThemeTokens`、Ijk AAR、`jniLibs.excludes "**/libijkplayer.so"`。
- `settings.gradle.kts`：仅 `:app`，`google()`/`mavenCentral()`，`FAIL_ON_PROJECT_REPOS`。
- `gradle/wrapper/gradle-wrapper.properties`：Gradle 8.7。
- `app/src/main/AndroidManifest.xml`：`INTERNET`、`REQUEST_INSTALL_PACKAGES`、通知/前台服务/存储权限；`usesCleartextTraffic=true`；所有 Activity `sensorLandscape`；FileProvider；自更新/下载相关组件。
- `app/proguard-rules.pro`：protobuf、Ijk JNI 类的 keep 规则。

### 12.2 导航/UI

- `ui/MainActivity.kt`：`MainRootNavRegistry` 使用、根 Fragment 缓存、侧栏展开/收起、DPAD 焦点守卫、返回键、用户浮层、自动更新/崩溃提示。
- `ui/MainRootNavRegistry.kt`：`RootNavSpec`、7 个根导航项、启动页 key、`fragmentFactory`。
- `ui/SidebarNavAdapter.kt`：`ID_SEARCH/HOME/CATEGORY/DYNAMIC/LIVE/MY/CUSTOM`、选中态主题色。
- `res/layout/activity_main.xml`：`sidebar` + `main_container` + `user_info_overlay`。
- `res/layout/item_sidebar_nav.xml`、`item_video_card.xml`：焦点/选中/卡片结构。
- `core/ui/*`：`BaseActivity`、`FocusReturn`、`DpadGridController`、`RecyclerViewFocus`、`TabContentFocus`、`ThemeColor` 等。

### 12.3 主题

- `core/theme/ThemePresets.kt`：默认暗色、TV Pink、经典插画、播放器固定主题。
- `res/values/attrs.xml`：`blblPageBackdrop`、`blblContentBackground`、`blblDialogSurface`、`blblOnPageBackdrop`、`blblAccent*`、`blblFocus*`。
- `res/values/themes.xml`：`Theme.Blbl.Base`、`Theme.Blbl.Base.Dark`、`Theme.Blbl.Base.PinkLight`、`Theme.Blbl.Base.TvPinkIllustration`、`Theme.Blbl.Player.Fixed`。
- `res/values/styles.xml`：Material 组件、对话、Tab、OSD 尺寸 overlay。
- `res/values-v31/themes.xml`：Android 12 SplashScreen。
- `core/theme/LauncherAliasManager.kt`：已废弃的启动图标别名同步。

### 12.4 网络/登录

- `core/net/BiliClient.kt`：多 OkHttpClient、CookieJar、拦截器、WBI key 缓存、请求封装。
- `core/net/CookieStore.kt`：SharedPreferences 持久化、SESSDATA 判断、导出/替换。
- `core/net/WbiSigner.kt`：混排表、参数排序、MD5、URL 编码。
- `core/net/AppSigner.kt`：Android HD appkey/appsec 与 MD5 签名。
- `core/net/WebCookieMaintainer.kt`：buvid、bili_ticket、Cookie refresh、播放前健康检查。
- `feature/login/QrLoginActivity.kt`：扫码 auth_code、轮询、cookie_info、`BiliAppAuthSession`。
- `core/account/AccountSessionStore.kt`：账号快照/切换/清理。
- `core/api/ApiSource.kt`、`core/api/video/VideoSourceApi.kt`：Web/App 能力矩阵。

### 12.5 播放器

- `feature/player/engine/BlblPlayerEngine.kt`：统一引擎接口和 `PlaybackSource`。
- `feature/player/engine/ExoPlayerEngine.kt`：Media3 主实现。
- `feature/player/engine/IjkPlayerEngine.kt`：IjkPlayer 备选实现。
- `feature/player/engine/IjkPlayerPlugin.kt`：从 `https://cat3399.top/blbl/ijkplayer` 下载 `.so`，仅大小/ELF/stamp 校验。
- `feature/player/engine/DashMpdGenerator.kt`、`DashLocalHttpProxy.kt`、`DashPlaybackCatalogResolver.kt`：DASH 与 CDN/无缝切换。
- `feature/player/CdnFailoverDataSource.kt`：多 CDN failover。
- `feature/player/danmaku/*`：自研弹幕引擎/View/缓存/计时。
- `feature/player/PlayerActivity.kt`（4,107 行）与 `PlayerActivityStartPlayback.kt`（1,173 行）：完整的播放器业务编排，强耦合，不能整体当库。
- `res/layout/activity_player.xml`：`PlayerView` + `DanmakuView` + OSD 层。

### 12.6 二进制/授权

- `app/libs/ijkplayer-cmake-release.aar`：4 ABI `.so`、`classes.jar`、空 `proguard.txt`、无 LICENSE/NOTICE。
- AAR 内 `libijkplayer.so` 字符串：FFmpeg `n7.1.1-4-g834d732`、`--disable-gpl --disable-nonfree`、`LGPL version 2.1 or later`。
- `README.md`：自述第三方项目参考/致谢、更新方案、免责声明；**不是许可证**。
- `wiliwili/LICENSE`：GPLv3。
- `wiliwili/resources/font/LICENSE.txt`、`resources/material/LICENSE.txt`：字体/图标资源的单独许可提示。

### 12.7 未验证事项

- 未实际运行 Gradle 构建/测试；未在当前机器做 API 21 真机验证。
- 未验证 blbl 所有 API 在目标网络环境下的可用性；B 站接口/WBI/风控属于外部可变依赖。
- 未进行完整依赖传递许可证扫描；上表许可证为初步判断，发布前必须用锁定 POM/SBOM 复核。
- 未获得 blbl-main 作者授权；这是发布前的硬阻断。

---

## 13. 最终建议

1. **先解决授权，再谈代码复用。** 在拿到 blbl-main 书面授权之前，不要把它作为可分发产品的基础；同时明确 wiliwili GPLv3 的组合分发方式。
2. **以 blbl-main 作为“能力层参考实现”，以 wiliwili 作为“产品层规范”。** 重点复用 `core/net`、`core/api/video`、`feature/player/engine`、`core/paging`、部分 `core/ui`，不要复用整页 UI。
3. **首版只做 ExoPlayer + Web API。** 这是最快、最可控、许可证最轻的路径；Ijk/App gRPC/直播/下载全部延后。
4. **先抽 `PlaybackController` 和 `VideoRepository` 两个边界，再改 UI。** 否则 wiliwili 播放器/首页会被 blbl 的 Activity 和巨型 `BiliApi` 反向绑架。
5. **保留主题 token 静态检查与焦点基础设施。** 这两项是 blbl 工程中最值得学习、也最能降低 wiliwili Android 移植返工成本的部分。
