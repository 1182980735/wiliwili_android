# wiliwili Android 移植 UI / 交互规格

> 文档状态：可执行规格 v1  
> 分析对象：`/Users/mac/Documents/trae_projects/wiliwili`  
> Android 对接对象（本机同目录）：`/Users/mac/Documents/trae_projects/blbl-main`  
> 约束：本规格只描述界面、交互、令牌和资源复用，不要求修改 wiliwili 源码；Android 落地以 blbl 现有 Kotlin/ViewBinding 工程为基础。  
> 主要证据：`AGENTS.md`、`resources/xml/**`、`resources/svg/**`、`resources/i18n/**`、`wiliwili/source/utils/register_helper.cpp`、`wiliwili/source/view/**`、`wiliwili/source/activity/**`，以及 blbl 的 `app/src/main/res/**`、`app/src/main/java/blbl/cat3399/**`。

---

## 0. 结论先行

1. **不要直接复用 wiliwili 的 XML 布局标签**。`resources/xml/` 是 borealis 的自定义 XML（`<brls:AppletFrame>`、`<AutoTabFrame>`、`<RecyclingGrid>`），不是 Android Layout XML；应复用其**层级、尺寸、颜色、交互意图**，映射到 blbl 的 `ConstraintLayout` / `MaterialCardView` / `RecyclerView` / `TabLayout + ViewPager2`。
2. **主壳应保留 blbl 的侧边栏，而不是复制 wiliwili 的三图标侧栏**。wiliwili 主壳是“100 宽一级图标侧栏 + 页面内顶部二级 Tab”；blbl 的 `activity_main.xml` 已经是“可展开侧栏 + `main_container`”，更适合 Android 手机/平板/TV 混合场景。wiliwili 的首页五个二级 Tab 应嵌入 blbl 的 `HomeFragment` 的 `TabLayout + ViewPager2` 中。
3. **设计令牌有明确源值**。主色 `color/bilibili = #FF6699`，可在 wiliwili 配置中通过 `custom_theme_color` 覆盖；它与 blbl 现有 `#FB7299`（`blbl_accent_tv_pink`）和默认紫色 accent 不同。Android 必须把这些颜色收敛为 theme attr，不能在 layout 里写死。
4. **wiliwili 的逻辑坐标可以近似 1:1 映射到 Android 的 dp/sp**。wiliwili 以 1280×720 为基准逻辑分辨率；blbl 的 `UiDensity` 以 1920×1080@1.5 为基准，即 1280×720 dp。因此 wiliwili XML 中的 `width/height/fontSize/margin/padding` 在默认基准下可按 1 单位≈1dp/1sp 落地，再通过 `sw600dp`/`w600dp` 和用户缩放做适配。
5. **播放器不要移植 MPV OSD 代码**。保留 blbl 的 Media3/Ijkplayer、`activity_player.xml` 和 `PlayerActivityTouch.kt` 手势层；只把 wiliwili 的 OSD 功能项（播放/暂停、清晰度、倍速、弹幕、投屏、锁定、简介）映射进去。
6. **首批 MVP 的瓶颈不是页面数量，而是三个可复用组件**：视频卡片、二级 Tab 容器、焦点/返回导航。先把这三者稳定下来，再批量接入各内容页。

---

## 1. 页面 / 导航结构清单

### 1.1 全局壳层

来源：`resources/xml/activity/main.xml`、`wiliwili/source/activity/main_activity.cpp`。

```text
AppletFrame（隐藏系统 header，保留底部按键提示）
└── Box(column, 100%×100%)
    ├── AutoTabFrame(sidebarPosition=left, sidebarWidth=100)
    │   ├── Tab[图标] 首页     -> HomeTab
    │   ├── Tab[图标] 动态     -> DynamicTab
    │   └── Tab[图标] 我的     -> MineTab
    └── 绝对定位底部操作区(width=100, bottom=20)
        ├── CustomButton 48×48 消息（圆角50）
        └── CustomButton 48×48 设置（圆角50）
```

关键规则：

- 一级侧栏默认**只显示图标**，`label=""`；AutoTabFrame 的图标盒为 34×34，但主壳外部按钮的 SVG 是 48×48。
- 侧栏背景使用 `@theme/color/grey_1`，激活图标使用 `ico-*-activate.svg`（粉色 `#FF6699`）。
- 消息按钮需要先检查登录，未登录不进入消息盒；设置按钮支持点击和手柄焦点。
- `MainActivity` 把 `BUTTON_BACK` 和 `BUTTON_START` 都注册为打开设置页；Android 上不建议保留“Back 打开设置”，Back 应走系统返回/退出语义，设置使用菜单键、侧栏按钮或显式工具栏按钮。

### 1.2 页面树与入口

| 编号 | 页面 / 容器 | 父级或入口 | 实际文件 | 结构要点 | MVP |
|---|---|---|---|---|---|
| M01 | 主壳 | App 启动 | `resources/xml/activity/main.xml`；`wiliwili/source/activity/main_activity.cpp` | 100 宽图标侧栏 + 底部消息/设置；页面内二级 Tab | P0 |
| M02 | 首页 | 主壳“首页” | `resources/xml/fragment/home_tab.xml`；`wiliwili/source/fragment/home_tab.cpp` | 顶部 5 Tab：直播 / 推荐 / 热门 / 追番 / 影视；右上角搜索框 300×35、圆角 6 | P0 |
| M03 | 首页 · 推荐 | 首页 Tab 1（默认 `defaultTab=1`） | `resources/xml/fragment/home_recommends.xml` | `RecyclingGrid`，`spanCount=@style/wiliwili/grid/span/4`，`itemHeight=250` | P0 |
| M04 | 首页 · 直播 | 首页 Tab 0 | `resources/xml/fragment/home_live.xml`；`resources/xml/fragment/home_live_area.xml` | 顶部 180×35 分区选择框，分区弹层；直播卡片 4 列、`itemHeight=200` | P1 |
| M05 | 首页 · 热门 | 首页 Tab 2 | `resources/xml/fragment/home_hots.xml`；`home_hots_all.xml` / `home_hots_rank.xml` / `home_hots_weekly.xml` / `home_hots_history.xml` | 二级 4 Tab：综合热门 / 排行榜 / 每周必看 / 入站必刷；内层 4 列卡片 | P1 |
| M06 | 首页 · 追番 / 影视 | 首页 Tab 3 / 4 | `resources/xml/fragment/home_bangumi.xml`；`resources/xml/fragment/home_cinema.xml`；`wiliwili/source/fragment/home_bangumi.cpp` / `home_cinema.cpp` | 顶部动态分类 Tab；卡片多为竖版 PGC 封面；“筛选”进入 PGC 索引 | P1 |
| M07 | PGC 索引 / 筛选 | 追番/影视“筛选”、`Intent::openPgcFilter` | `resources/xml/activity/pgc_index_activity.xml`；`wiliwili/source/activity/pgc_index_activity.cpp` | 顶部 Tab 分类 + 多组筛选条件 + 5 列竖版卡片 | P2 |
| M08 | 动态 | 主壳“动态” | `resources/xml/fragment/dynamic_tab.xml`；`wiliwili/source/fragment/dynamic_tab.cpp` | 左侧 270 宽 UP 主列表 + 右侧顶部“视频 / 综合”Tab；视频 3 列，综合单列居中（左右各 18% padding） | P1 |
| M09 | 我的 | 主壳“我的” | `resources/xml/fragment/mine_tab.xml`；`wiliwili/source/fragment/mine_tab.cpp` | 用户卡 100 高 + 四项统计 + 6 个 Tab：历史 / 收藏 / 订阅 / 追番 / 追剧 / 稍后再看 | P0（登录 + 历史可见即可） |
| M10 | 收藏夹 / 订阅详情 | 我的或动态深链 | `resources/xml/fragment/mine_collection_video_list.xml`；`wiliwili/source/fragment/mine_collection_video_list.cpp` | 顶部标题/返回 + 视频网格；通过 `Intent::openCollection(mid, type)` 进入 | P1 |
| M11 | 搜索 | 首页搜索框、快捷键、`Intent::openSearch` | `resources/xml/activity/search_activity.xml`；`resources/xml/fragment/search_tab.xml`；`search_order.xml` | 5 个一级搜索 Tab：历史 / 热搜 / 视频 / 番剧 / 影视；视频页再嵌套 5 个排序 Tab | P0 |
| M12 | TV 搜索 | TV 模式 | `resources/xml/activity/search_activity_tv.xml`；`wiliwili/source/activity/search_activity_tv.cpp` | 左侧虚拟键盘 + 搜索/删除/清空，右侧热搜与历史；适合遥控器 | P2 |
| M13 | UGC 视频详情 / 播放 | 视频卡片、`Intent::openBV` | `resources/xml/activity/player_activity.xml`；`resources/xml/views/video_view.xml`；`wiliwili/source/activity/player_activity.cpp` | 左侧视频 + 标题/UP 主/点赞投币收藏分享/播放数据；右侧 Tab：评论 + 动态生成的 P / 合集 / 推荐 / 投稿；播放器 OSD 独立覆盖在视频上 | P0 |
| M14 | 番剧 / 影视详情播放 | PGC 卡片、`Intent::openSeasonByEpId` | `wiliwili/source/activity/player_season_activity.cpp`；复用 `player_activity.xml` 和 `video_view.xml` | 左侧播放 + 右侧分集 / 系列 / 推荐；有剧集顺序、片头片尾等设置 | P1 |
| M15 | 直播播放 | 直播卡片、`Intent::openLive` | `resources/xml/activity/live_player_activity.xml`；`wiliwili/source/activity/live_player_activity.cpp` | 左侧播放器 + 标题/主播；右侧 240 宽实时弹幕流；右下关闭按钮 | P1 |
| M16 | 消息盒 | 主壳底部消息按钮、`Intent::openInbox` | `resources/xml/fragment/inbox_view.xml`；`wiliwili/source/fragment/inbox_view.cpp` | 从右侧出现的 600 宽面板；4 Tab：聊天 / 回复我的 / @我的 / 收到的赞 | P2 |
| M17 | 设置 | 主壳设置按钮、`Intent::openSetting` | `resources/xml/activity/setting_activity.xml`；`wiliwili/source/activity/setting_activity.cpp` | 左侧 240 宽二级侧栏：播放设置 / 界面设置 / 其他设置 / 实用工具 / 关于更多 / 开源许可 | P1 |
| M18 | 动态详情 | 动态卡片、`Intent::openActivity` | `resources/xml/fragment/dynamic_detail.xml`；`wiliwili/include/activity/dynamic_activity.hpp` | 单列居中图文；适合做成独立 Activity | P2 |
| M19 | 图片浏览 | 动态图片、`Intent::openGallery` | `resources/xml/activity/gallery_activity.xml`；`wiliwili/source/activity/gallery_activity.cpp` | 黑底全屏、左右切换、关闭 | P2 |
| M20 | 首次教程 / DLNA / 扫码 | 启动或设置入口 | `resources/xml/activity/hint_activity.xml`；`resources/xml/fragment/player_dlna_search.xml`；`resources/xml/fragment/mine_qr_login.xml` | 教程、投屏搜索、二维码登录 | P1（扫码登录）/ P2（其余） |

### 1.3 导航与返回规则

- **一级导航**：首页 / 动态 / 我的由主壳持有；搜索、设置、消息等是二级入口。
- **二级 Tab 激活**：wiliwili 的 `AutoSidebarItem` 在“获得焦点”时切换内容（`wiliwili/source/view/auto_tab_frame.cpp`）。触摸和遥控必须区分：
  - 触摸：点击 Tab 才切换；不要因为触摸焦点变化就切换，避免误触。
  - 遥控/键盘：D-pad 左右移动焦点，Tab 获得焦点时可切换（blbl 已有 `selectOnFocusProvider` 配置，可沿用）。
- **返回**：wiliwili 中页面内二级 Tab 的 `B` 会先回到侧栏，再退出页面；Android 建议保持“内容 → 二级 Tab → 一级侧栏 → 退出”的层级返回策略，但使用 `OnBackPressedCallback`/`BackPressHandler` 实现，而不是直接复制 borealis action。
- **深链**：所有页面跳转应由 Android 的 Intent/导航层统一承接，对应 wiliwili 的 `wiliwili/include/utils/activity_helper.hpp`：
  - `openBV` / `openAV` → UGC 播放入口
  - `openSeasonByEpId` / `openSeasonBySeasonId` → PGC 播放
  - `openLive` → 直播
  - `openSearch` / `openTVSearch` → 搜索
  - `openCollection` → 收藏夹
  - `openSetting` / `openInbox` / `openGallery` / `openActivity` → 对应二级页面

---

## 2. 设计令牌表

### 2.1 坐标与密度换算

| 项目 | wiliwili | Android 建议 | 依据 |
|---|---|---|---|
| 基准逻辑画布 | 1280×720 | 1280×720dp（blbl 1080p@1.5 等价） | `wiliwili/source/utils/register_helper.cpp`；`library/borealis/library/include/borealis/core/application.hpp`；`blbl-main/app/src/main/java/blbl/cat3399/core/ui/UiDensity.kt` |
| 大屏档位 | 1600×900、1920×1080 | 用 `w900dp`、`w1200dp` 等限定符；不要硬编码 1920px | `register_helper.cpp` 的 span 分支 |
| 小屏档位 | 960×544 | `sw400dp`/小屏横屏分支，按钮仍保持 ≥48dp 触摸目标 | `register_helper.cpp` 的 544 分支；`blbl-main/app/src/main/res/values/dimens.xml` |
| 用户缩放 | wiliwili 提供 544p/720p/900p/1080p 档位 | 复用 blbl `UiScale.factor()` / `cloneInUserScale()`，不要在每个 View 里手乘 | `wiliwili/source/utils/config_helper.cpp`；`blbl-main/app/src/main/java/blbl/cat3399/core/ui/UiScale.kt` |
| 可点击区域 | 图标常见 30–48；卡片整张可点 | 交互控件最小 48×48dp；焦点描边外扩不裁剪 | `main.xml` 外部按钮 48；`blbl-main/res/values/dimens.xml` |

> 换算原则：布局、圆角、间距按 **dp**；文字按 **sp**；不要用 px 写死。若需要精确复刻 720p 截图，优先以 1280dp 宽为设计基准。

### 2.2 色彩令牌

来源：`wiliwili/source/utils/register_helper.cpp` 第 124–198 行；基础主题色来自 `library/borealis/library/lib/core/theme.cpp`。wiliwili 使用浅/深两套值，下表“浅色/深色”均指这两套。

| 语义 | wiliwili token | 浅色值 | 深色值 | 使用位置 | Android 建议 |
|---|---|---|---|---|---|
| 品牌主色 | `color/bilibili` | `#FF6699` | `#FF6699` | 一级/二级 Tab 激活文字、选中态、粉色 SVG | `?attr/blblAccent`；wiliwili 皮肤下设为 `#FF6699` |
| 主色浅底 | `color/pink_1` | ≈`#FFEDF3` | ≈`#240E15` | 扁平 Tab 激活背景、部分二级 Tab | `?attr/blblAccentContainer` |
| 侧栏 / 弱底色 | `color/grey_1` | `#F5F6F7` | `#333435` | 主侧栏背景、用户卡、推荐理由底 | 建议新增 `?attr/blblSurfaceMuted` |
| 二级切换底色 | `color/grey_2` | `#F5F5F5` | `#333537` | 直播分区选择、二级 Tab 底、搜索 TV 按钮 | 可复用 `?attr/blblSurfaceMuted` 或新增 `blblSurfaceMutedAlt` |
| 骨架屏 | `color/grey_3` | `rgba(200,200,200,16)` | `rgba(160,160,160,160)` | Recycler 骨架屏背景 | 新增 `?attr/blblSkeleton`；SVG/动画不可用 fixed color |
| 深色半透明角标 | `color/grey_4` | `#303030A0` | `#303030A0` | 历史记录右上角 Badge | 新增 `?attr/blblScrim` |
| 次要文字 | `font/grey` | `#9499A0` | `#9499A0` | 卡片 UP 主、播放量、说明文字 | `?attr/blblOnPageBackdropSecondary` 或 `?android:attr/textColorSecondary` |
| 警告 / 推荐理由底 | `color/yellow_1` | `#FDF6E6` | `#2E2111` | 入站必刷、已关注、推荐理由 | 新增 `?attr/blblWarningSurface` |
| 警告文字 | `font/yellow_1` | `#D97607` | `#D97607` | 黄色推荐/成就文字 | 新增 `?attr/blblWarningText` |
| 链接 | `color/link` | `#6693B6` | `#6693B6` | 链接、可点击文本 | 新增 `?attr/blblLink` |
| 分割线 | `color/line` | `#D0D0D0` | `#646464` | 动态左侧列表分割线、统计分隔线 | 新增 `?attr/blblDivider`；或 `?attr/colorControlNormal` 的低透明度版本 |
| 小红点 / 危险 | `color/tip/red` | `#FA5857` | `#D33F40` | 未读、错误提示 | 新增 `?attr/blblDanger`；删除类操作使用现有 danger token |
| 通用白 | `color/white` | `#FFFFFF` | `#FFFFFFB4` | 深色素材文字、图标 | 媒体覆盖层上的固定文字可直接 `#FFFFFF` + scrim；普通 UI 使用 `?attr/colorOnSurface` |
| 图片说明 | `captioned_image/caption` | `#02B0B7` | `#33BAE3` | 加载中/图片来源说明 | 可选新增 `?attr/blblCaption` |
| 通用背景 | `brls/background` | `#EBEBEB` | `#2D2D2D` | 无内容页背景 | `?attr/colorBackground` |
| 通用正文 | `brls/text` | `#2D2D2D` | `#FFFFFF` | 普通标题/正文 | `?attr/colorOnSurface` |
| 通用禁用文字 | `brls/text_disabled` | `#8C8C8C` | `#505050` | 禁用态 | `?android:attr/textColorTertiary` 或新增 |
| 遮罩 | `brls/backdrop` | `#000000B2` | `#000000B2` | 弹窗、图片浏览、消息面板外遮罩 | `?attr/blblFocusBgRound` 不适用；新增 `?attr/blblBackdrop` 或使用 `@color/player_overlay_bg` 的语义化版本 |
| 焦点高亮 | `brls/highlight/color1` / `color2` | `#0DB6D5` / `#50EFD9` | `#198AC6` / `#89F1F2` | 默认 focus 外框 | 不要照搬青色；Android 使用 blbl 现有 `?attr/blblFocusBgRound` + `?attr/blblFocusStrokeColor` |

**主题对接硬约束：**

- blbl 的 `app/build.gradle.kts` 有 `checkThemeTokens`，会拒绝 layout 里的固定色 `@color/blbl_bg`、`@color/blbl_surface`、`@color/blbl_text` 等；wiliwili 布局中的 `#E3E5E7`、`#18191c`、`#FFFFFF` 也必须在接入时转换成语义 attr/selector。
- wiliwili 的 `SVGImage` 只替换 SVG 文本中的 `#FF6699`/`#ff6699`（`wiliwili/source/view/svg_image.cpp`）；这不能替代 Android 的主题系统。Android 应使用 `ImageView` 的 `app:tint`、`ColorStateList` + theme attr，而不是依赖字符串替换。
- 现有 blbl 的 TV Pink 品牌色是 `#FB7299`，wiliwili 是 `#FF6699`。**两者不要混用**：若目标是 wiliwili 复刻，增加一个 `WiliPink` 主题 preset，将 `blblAccent` 设为 `#FF6699`；其他预设保持 blbl 原值。

### 2.3 圆角、描边、焦点

| 用途 | wiliwili 值 | 来源 | Android 落地 |
|---|---|---|---|
| 视频封面 / 小角标 | `4` | `resources/xml/views/video_card.xml:17,79,117,140` | `ShapeableImageView` 或 `MaterialCardView` `app:cardCornerRadius="4dp"` |
| 搜索框 / 用户卡 / 骨架块 | `6` | `home_tab.xml:55`、`mine_tab.xml:13`、`auto_tab_frame.cpp` 骨架 `nvgRoundedRect(...,6)` | `6dp` |
| 卡片焦点框 / 播放器锁定按钮 | `8` | `video_card.xml:3`、`video_view.xml` 锁按钮 `cornerRadius=8` | `8dp`；焦点描边 2dp |
| blbl 现有卡片焦点 | `10` | `blbl-main/app/src/main/res/drawable/blbl_focus_bg_round.xml` | 视频卡片、侧栏项、设置项统一 `10dp`；wiliwili 风格卡片也可用 8dp |
| 药丸/圆形按钮 | `50` 或 `60` | `main.xml:51-52,64`；`mine_tab.xml:19` | `50%` / `ViewOutlineProvider` 圆形；触摸目标仍按方形 48dp |
| OSD 提示框 | `4` | `video_view.xml` 中央/速度提示 | `4dp` |
| 焦点描边 | `2` 宽 | blbl focus drawable | `2dp`，颜色用 `?attr/blblFocusStrokeColor`；不要用 wiliwili 的青色高亮 |
| 焦点缩放 | `1.04`，120ms | `blbl-main/app/src/main/res/animator/blbl_focus_scale.xml` | 手柄/遥控焦点态可用；触摸不要缩放 |
| 按压反馈 | 200ms 高亮动画 | `register_helper.cpp:205`；borealis `view.cpp` | 统一 120–200ms；不要每个页面单独实现 |

### 2.4 字体令牌

| 语义 | 大小 | 来源 | Android 建议 |
|---|---|---|---|
| 最小辅助文字 | 12 | 卡片角标 `video_card.xml:86`；`autoSidebar/subtitle_label` | `12sp`，单行，必要时 `ellipsize` |
| 小辅助文字 | 13 | 推荐理由、播放状态 `video_card.xml:129,152`；`video_view.xml` 人数 | `13sp` |
| 搜索/提示 | 14 | `home_tab.xml:57,64`；`search_activity.xml:29` | `14sp` |
| 元信息 / 次级按钮 | 16 | 卡片 UP 主/播放量；OSD 按钮标签 | `16sp` |
| 一级 Tab / 动作标签 | 18 | `main.xml:19`；`mine_tab.xml:18`；搜索 Tab | `18sp`；激活可 `sans-serif-medium` |
| 默认正文 / 卡片标题 | 20 | `brls/label/default_font_size`（`style.cpp`）；`video_card.xml:67` | `20sp` |
| 二级主 Tab | 22 | `home_tab.xml:12`；动态 Tab；设置左侧 Tab | `22sp`；带图标时 wiliwili 会将字号减 10，Android 图标与文字分开布局，不必复刻该减字逻辑 |
| 页面标题 | 24 | 播放页标题 `player_activity.xml:48`；搜索 TV 标题 | `24sp`，单行省略 |
| 图标盒 | 26 / 30 / 34 / 40 / 48 | `resources/svg/*` 的 viewBox；`main.xml:56`；`video_view.xml` | 图标按 SVG viewBox 缩放；交互图标按钮至少 48dp |
| 标题行高 | `1.5` | `video_card.xml:68` | `lineSpacingMultiplier=1.5` 或 Android lineHeight |
| 卡片标题行数 | 2 | `video_card.xml:69` | `maxLines=2`；UP 主/元信息 `singleLine=true` |
| 字体族 | 内置 `resources/font/switch_font.ttf`；可覆盖 `font.ttf` | `config_helper.cpp:1073`；`desktop_font.cpp` | Android 默认 `sans-serif`/`Noto Sans CJK`；不建议直接打包 8.0MB 的 `switch_font.ttf`，如必须复刻再引入并配置 fallback |

### 2.5 间距与骨架尺寸

| 令牌 | 值 / 规则 | 来源 | Android 落地 |
|---|---|---|---|
| 基础间距阶 | `4, 6, 8, 10, 12, 15, 20, 30, 40, 50` | 各 XML 中高频值 | 收敛为 `dimens.xml` 中的 `space_4/6/8/...`，避免散落 |
| 内容左右 padding | `20` | 视频网格普遍 `paddingLeft/Right=20` | `20dp`；搜索视频左 30/右 20 |
| 卡片外边距 | `10` | `video_card.xml:63` 文本区 `margin=10` | 与 blbl 现有 `video_card_margin=8dp` 合并；推荐 `8dp` 外间距 + `10dp` 内文距 |
| 顶部一级 Tab 高度 | `70` | `home_tab.xml:6`；动态 Tab 70；`inbox_view` 70 | `70dp`；小屏可 56dp |
| 二级/筛选 Tab 高度 | `50`；搜索排序 `45`；我的 `65` | `home_hots.xml`；`search_order.xml`；`mine_tab.xml` | `48–50dp`；排序条 `45dp` |
| 搜索框 | `300×35`，圆角 6，左 padding 15 | `home_tab.xml:49-55` | 用 `TextInputLayout` / MaterialCardView + EditText；小屏变 `match_parent` 宽度 |
| 一级侧栏宽度 | `100`（AutoTabFrame 默认） | `auto_tab_frame.cpp` 的默认 side width | blbl 保留 `77dp` 侧栏；wiliwili 皮肤可加宽到 `96–100dp` |
| 设置页二级侧栏 | `240` | `setting_activity.xml:14` | `228–240dp`；手机上改为全宽列表 |
| 用户卡高度 | `100` | `mine_tab.xml:9` | `96–100dp` |
| 播放页左栏宽度 | `windowWidth - 480`；544 档固定 600 | `register_helper.cpp:211,245` | 平板/横屏：`0dp + weight` 或 `ConstraintLayout` 比例；竖屏改为上下布局 |
| 播放器 OSD 顶部 | `80` 高 | `video_view.xml:11` | 顶部 scrim `80dp`，包含标题与右侧投屏/设置 |
| 播放器 OSD 底部 | `120` 高 | `video_view.xml` 底部控制盒 | 底部 scrim `120dp`；触摸设备可缩短/自动隐藏 |
| OSD 按钮容器 | `60×60`；播放主按钮 `100×100`；图标 30 | `video_view.xml` | 容器 `56–64dp`；主按钮 `96–100dp`；均大于 48dp |
| 骨架屏圆角 | `6` | `recycling_grid.cpp` SkeletonCell | `6dp` + 轻微 shimmer；`?attr/blblSkeleton` |
| 列表预取 | `preFetchLine=1/2` | 各 `RecyclingGrid` | RecyclerView `prefetchDistance` + 触底加载；不能用 itemHeight 硬编码模拟 |

### 2.6 响应式栅格

`register_helper.cpp` 第 207–256 行按逻辑分辨率设置 `spanCount`；`auto_tab_frame` 只负责 Tab 尺寸，不负责栅格。对应表：

| 逻辑高度档位 | `wiliwili/grid/span/5` | `span/4` | `span/3` | `span/2` | 备注 |
|---|---:|---:|---:|---:|---|
| 544p（960×544） | 4 | 3 | 2 | 2 | 播放器宽固定 600；内容 padding 用 20；动态卡片 padding 10 |
| 720p（1280×720，默认） | 5 | 4 | 3 | 2 | 主推荐/搜索/我的列表使用 4 列；追番/收藏用 5 列 |
| 900p（1600×900） | 6 | 5 | 4 | 3 | 字体与间距同步放大 |
| 1080p（1920×1080） | 7 | 6 | 5 | 4 | 播放器宽 `windowWidth-480`；内容 padding 42/60 |

**Android 落地建议：**

- 视频卡片默认用 `GridLayoutManager`；`spanCount` 不要写死为 `4`，建立 `WiliGridSpan`：
  - 普通视频网格：`span4`（1280dp 下 4 列）。
  - 番剧/影视/收藏：`span5`（1280dp 下 5 列）。
  - 动态视频：`span3`。
  - 搜索结果番剧/影视：`span2`。
- 与 blbl 对接时优先复用 `blbl-main/app/src/main/java/blbl/cat3399/core/ui/GridSpanPolicy.kt`：
  - `fixedSpanCountForWidthDp(widthDp, override)` 在 `widthDp >= 1100` 时返回 4，正好覆盖 1280dp 的 4 列；PGC 5 列需要通过 `overrideSpanCount=5` 或 `autoSpanCount` 指定。
  - 现有 `gridSpanCount` 用户设置继续生效；wiliwili 皮肤可将其默认值设为 4，PGC 页面默认 5。
- 卡片不要固定 `itemHeight=250dp` 后再让封面占 60%；Android 用 **16:9 封面 + 自适应文本区**，在 1280dp 基准下视觉高度接近 250dp 即可。这样可避免不同 dpi/宽高比裁切。

---

## 3. 交互映射表

### 3.1 全局与浏览页

| 场景 | wiliwili 手柄 | wiliwili 键盘（默认） | wiliwili 触摸 | Android 触摸 | Android 遥控 / 键盘 | 建议落点 |
|---|---|---|---|---|---|---|
| 焦点移动 | 左摇杆 / D-pad | 方向键 | 无（直接点） | 单指点击/滑动 | `KEYCODE_DPAD_*` | 使用原生 focus；列表用 `DpadGridController` 防焦点逃逸 |
| 确认 / 打开 | A | Enter / Space | 点击卡片/按钮 | `OnClickListener` | `DPAD_CENTER` / `ENTER` | 卡片整体 `clickable+focused`；`stateListAnimator` 仅焦点态 |
| 返回 | B | Esc / Backspace（按页面） | Android 返回手势/返回键 | `OnBackPressedCallback` | `KEYCODE_BACK` | 内容 → 二级 Tab → 侧栏 → 退出；不要用 Back 直接打开设置 |
| 一级导航 | D-pad 上/下（侧栏内） | Tab 切换或方向键 | 点击侧栏图标 | 点击/滑动 | 侧栏内 `DPAD_UP/DOWN`， `DPAD_RIGHT` 进入内容 | 侧栏为 RecyclerView，选中项自动展开文字（blbl 已有） |
| 二级 Tab 切换 | `LB/RB`；播放器资料页 `LT/RT` | `PgUp/PgDn`，二级 `Shift+PgUp/PgDn` | 点击 Tab | `TabLayout` 点击；D-pad 左右 | `KEYCODE_DPAD_LEFT/RIGHT`；可选 focus-to-select | 复用 blbl `TabLayoutFocus` + `ViewPager2`；触摸禁止 focus-to-select |
| 横向翻页 | 列表内 D-pad 左右 | 方向键 | 横向滑动 | `RecyclerView` 横向/纵向滑动 | `DPAD_LEFT/RIGHT` 在边界切 Tab | 列表右边界只在明确配置时切 Tab，避免焦点逃逸 |
| 纵向滚动 / 加载更多 | D-pad 上/下 | 方向键 / PageUp/PageDown | 拖动滚动 | `RecyclerView` 惯性滚动；SwipeRefresh | `DPAD_UP/DOWN` | 触底提前 2 行请求下一页；对应 `RecyclingGrid::onNextPage` |
| 首页搜索 | `Y` | `Ctrl+F`（macOS `Meta+F`） | 点击搜索框 | 点击输入框 → IME | `KEYCODE_SEARCH` / `MENU` 可选 | 输入为空时聚焦输入框；提交后进入搜索页 |
| 刷新列表 | `X` | `Ctrl+R`（macOS `Meta+R`） | 下拉刷新或按钮 | `SwipeRefreshLayout` | `KEYCODE_MENU` 可选 | 与 blbl 现有 `RefreshKeyHandler` 对接；X 在评论/弹幕页语义不同 |
| 切换/更多操作 | 视页面而定 `X` / `Y` | 对应快捷键 | 长按卡片 / 更多按钮 | 长按或 overflow 菜单 | `DPAD_CENTER` 长按 | 长按用于“稍后再看/不感兴趣/删除”等动作，避免主点击被污染 |
| 消息 / 设置 | 主壳底部按钮；`Start` 设置 | 侧栏/菜单 | 点击 48×48 按钮 | 点击 | 侧栏按钮 / `KEYCODE_SETTINGS` | 消息先校验登录；设置保持独立 Activity |

### 3.2 播放器 OSD

wiliwili 的播放器交互来源：`wiliwili/source/view/video_view.cpp`（约 74–525、1669–1745 行）、`wiliwili/source/utils/gesture_helper.cpp`；键盘默认值来源：`wiliwili/source/utils/config_helper.cpp:717-748`。

| 功能 | wiliwili 手柄 | wiliwili 键盘 | wiliwili 触摸手势 | Android 触摸 | Android 遥控 | 实现建议 |
|---|---|---|---|---|---|---|
| 显示/隐藏 OSD | `Y` | `O` | 单击视频 | 单击视频区域切换 OSD；控件显示时再次点空白隐藏 | `DPAD_CENTER` 在非控件焦点时呼出 OSD | 复用 blbl `PlayerActivityTouch` 单击逻辑 |
| 播放/暂停 | `A`（全屏态）或 OSD 播放按钮 | `Space` | 双击视频 | 双击视频中心；或点播放按钮 | `DPAD_CENTER`（控件聚焦播放键）/ `MEDIA_PLAY_PAUSE` | ExoPlayer/Ijkplayer 统一封装 `togglePlayPause` |
| 快进 / 快退 | `RB` / `LB`（视频焦点）；`LT/RT` 在右侧资料区切 Tab | `]` / `[` | 横向拖动视频区域 | 左右滑动预览进度，松手 seek；双击左右边缘可步进 | `MEDIA_FAST_FORWARD` / `MEDIA_REWIND`，或 D-pad 左右在进度条聚焦时 | 复用 blbl `PlayerTouchController` 的 seek 预览；按钮和触摸共用同一 seek delegate |
| 音量 | `RT` + D-pad 上/下 | `0` / `9` | 右侧竖滑 | 右半屏上下滑调音量；音量键 | `KEYCODE_VOLUME_UP/DOWN` | 使用系统音频流；保留 `PlayerActivityTouch.kt` 的右侧竖滑 |
| 亮度 | 平台支持时左侧竖滑 | — | 左半屏上下滑 | 左半屏上下滑改 `window.attributes.screenBrightness` | 无 | 仅手机/平板启用；TV 隐藏亮度手势 |
| 倍速 | OSD 倍速入口 | `F3`；长按 `P` 临时加速 | 长按视频右侧/长按画面 | 长按加速（可参考 blbl 右边缘长按 2x） | `MENU`/`SETTINGS` 或 OSD 菜单 | 保留 wiliwili 的“长按临时倍速、松手回 1.0”；速度选择列表用 BottomSheet |
| 弹幕开关 | `X` | `D` | 弹幕按钮 | 点击弹幕按钮；长按/更多进弹幕设置 | `KEYCODE_CAPTIONS` 可选 | 按钮图标用 `bpx-svg-sprite-danmu-switch-on/off`；切换后立即刷新 |
| 清晰度 | `Start` | `F2` | 清晰度文字按钮 | 点击清晰度 → BottomSheet | `KEYCODE_SETTINGS` / `MENU` | 选项来自现有播放引擎支持的 qn/音轨/编码；不在 UI 层硬编码 |
| 播放器设置 | `Back` / OSD 设置按钮 | `F5` | 设置图标 | 点击设置图标 → 面板 | `KEYCODE_MENU` / `SETTINGS` | 面板：字幕、画面比例、镜像、均衡器、着色器、片头片尾；MVP 只保留字幕/比例 |
| 投屏 | OSD 投屏图标 | — | 点击投屏 | 点击 → 搜索设备 | `KEYCODE_MEDIA_*` 不映射 | P2；复用 blbl 现有 DLNA/投屏能力，不复制 wiliwili 的 DLNA activity |
| 锁定 OSD | 锁定按钮 | — | 点击锁图标 | 锁定后单击只显示解锁按钮，滑动/点击不触发播放器操作 | 无 | 必须移植；对应 `view_player_touch_overlay.xml` 的 `btn_touch_lock` |
| 全屏 / 退出全屏 | `A` 进入；`B` 退出 | Esc | 全屏按钮 / 系统旋转 | 点击全屏；旋转传感器横屏 | `KEYCODE_BACK` 先退全屏再退页面 | 只允许 `sensorLandscape` 播放 Activity；退出全屏回到详情/列表 |
| 简介 / 详情 | 标题 `A` | `F1` | 点标题/简介区域 | 点击标题 → 简介弹层 | `DPAD_CENTER` 在标题焦点 | 简介可复用 `PlayerEvaluate` 的 WebView/文本；MVP 用文本弹层 |
| 视频列表 / 分集 | 右侧 Tab `P` / 合集等 | `F4` | 点击右侧 Tab | 右侧列表；手机端改为 BottomSheet | `DPAD_LEFT/RIGHT` 切 Tab | 复用 blbl 已有 playlist/side panel，避免重复造播放列表 |
| 弹幕设置 | OSD 弹幕设置按钮 | — | 点击弹幕设置 | 点击 → 全屏/抽屉设置 | `KEYCODE_CAPTIONS` 可选 | 必留：开关、透明度、字号、速度、显示区域、屏蔽词 |

**播放器交互硬规则：**

1. **手机/平板优先使用 blbl 已有手势层**：`blbl-main/app/src/main/java/blbl/cat3399/feature/player/PlayerActivityTouch.kt` 已实现单击 OSD、双击播放/暂停、横向 seek 预览、左右竖滑亮度/音量、触摸锁定、右边缘长按加速。不要重新移植 `OsdGestureRecognizer`，否则会出现两套手势状态机互相抢事件。
2. wiliwili 的 `MAX_DELTA_MOVEMENT=24`、长按 250ms（`gesture_helper.cpp`）是旧逻辑；Android 应使用 `ViewConfiguration.getScaledTouchSlop()` 与 `getLongPressTimeout()`，适配不同 dpi。
3. 手柄与触摸的按钮语义必须共享同一状态源（播放器 `isPlaying`、音量、亮度、倍速），禁止各自维护一份；否则 OSD 显示会和真实状态不一致。
4. 播放页 `Back` 的优先级：关闭侧栏/弹层 → 退出全屏 → 返回详情/列表；不要直接结束 Activity。

### 3.3 焦点顺序与焦点可见性

| 区域 | 进入方式 | 离开方式 | 规则 |
|---|---|---|---|
| 一级侧栏 | 从内容按 `D-pad Left`；或从侧栏自身上下 | 按 `D-pad Right` 进入当前内容；按 Back 上一级 | 侧栏项获得焦点时选中并展开标题（blbl 已有） |
| 二级 Tab | 从内容向上按 `D-pad Up`；或 `LB/RB` / `PgUp/PgDn` | 向下进入当前 Tab 内容；左右切 Tab | Tab 条本身可获得焦点；触摸点击不触发 focus ring |
| 卡片网格 | Tab 的 `DPAD_DOWN` 或首项自动聚焦 | 到左边界可按 `Left` 回侧栏/上一级；到顶可按 `Up` 回 Tab | 网格内部禁止焦点逃逸；`DpadGridController` 已处理边缘和加载更多 |
| 播放器 OSD | 视频获得焦点按确认；或触摸呼出 | 自动隐藏/返回键 | OSD 隐藏时视频本体的 D-pad 左右可用于 seek（wiliwili TV 模式） |
| 弹窗/面板 | 打开时自动聚焦首个可操作项 | Back 关闭面板并恢复之前焦点 | 使用 `FocusReturn`/显式保存焦点；不要回到列表顶部 |

### 3.4 触摸与遥控的差异结论

- **触摸不应复制“焦点即选中”**：wiliwili 的 Tab 在焦点获得时切换，是因为手柄只靠焦点移动；Android 触摸必须使用 `OnClickListener`/`TabLayout.OnTabSelectedListener`。
- **遥控必须有可见焦点环**：所有可操作卡片/按钮在 `state_focused` 下显示 2dp 描边，不能只靠按压色。
- **触摸目标不得小于 48dp**：wiliwili 的 30/34/40 图标只是图标尺寸，外面必须包 48dp 触摸容器。
- **滑动与点击冲突**：列表用 `RecyclerView` 原生滚动；卡片点击用 `GestureDetector`/`onTouch` 时不拦截已进入滚动阈值的移动事件；播放器触摸层独立于列表滚动。
- **长按/双击**：只用于播放器或明确的卡片高级操作；列表普通卡片不要用双击。

---

## 4. 首批 MVP 页面与组件优先级

### P0：没有这些就不能称为可用的 Android 移植

| 优先级 | 页面 / 组件 | 依赖 | 交付标准 |
|---|---|---|---|
| P0-1 | 主壳与一级导航 | blbl `activity_main.xml`、`MainActivity.kt`、`SidebarNavAdapter.kt` | 保留 blbl 侧栏；wiliwili 首页/动态/我的可从侧栏进入；选中态、展开态、返回态正常 |
| P0-2 | 主题 token + 基础 dimens | `register_helper.cpp`、blbl `attrs.xml`/`themes.xml` | 主色、弱底、文字、分割线、危险色、焦点色全部 theme attr；`checkThemeTokens` 通过 |
| P0-3 | 二级 Tab 容器 | `AutoTabFrame` 行为 + blbl `TabLayout/ViewPager2` | 顶部 Tab 可触摸点击；遥控 D-pad 可聚焦并切换；Tab 与内容滚动位置可保存/恢复 |
| P0-4 | 视频卡片 | `video_card.xml` + `item_video_card.xml` | 16:9 封面、2 行标题、UP 主、播放/弹幕数、时长、推荐原因；焦点环、点击、长按动作可用 |
| P0-5 | 首页推荐流 | `home_recommends.xml`、`HomeFragment.kt`、`VideoGridFragment.kt` | 首屏加载、空态、重试、下拉刷新、触底分页、骨架屏；D-pad 可连续滚动 |
| P0-6 | 搜索 | `search_activity.xml`、`search_tab.xml`、blbl `SearchFragment.kt` | 输入法输入、历史/热搜/视频/番剧/影视 Tab、排序、结果点击进入播放器 |
| P0-7 | 视频播放器 OSD | `video_view.xml` + blbl `activity_player.xml`、`PlayerActivityTouch.kt` | 可播放/暂停、进度、清晰度、倍速、弹幕开关、音量、全屏、锁定、返回；触摸/遥控入口一致 |
| P0-8 | 登录 + 我的最小闭环 | `mine_tab.xml`、`mine_qr_login.xml` + blbl `QrLoginActivity` | 扫码登录、用户信息、历史 / 收藏 / 稍后再看入口可用 |
| P0-9 | 基础设置 | `setting_activity.xml` + blbl `SettingsActivity` | 播放、界面、网络三组设置可读写；主题切换提示重启；设置项持久化 |

### P1：内容完整性

| 优先级 | 页面 / 组件 | 依赖 | 交付标准 |
|---|---|---|---|
| P1-1 | 首页直播 / 热门 / 追番 / 影视 | `home_live.xml`、`home_hots*.xml`、`home_bangumi.xml`、`home_cinema.xml` | 5 个首页 Tab 全部可进入；热门 4 个二级 Tab；PGC 卡片竖版；筛选入口可见 |
| P1-2 | 动态页 | `dynamic_tab.xml` | 左侧 UP 主列表 + 右侧视频/综合 Tab；动态详情可打开 |
| P1-3 | 我的更多 Tab | `mine_tab.xml` 的 6 个 Tab | 收藏/订阅/追番/追剧/稍后再看有加载/空态/错误态 |
| P1-4 | 收藏夹 / 订阅详情 | `mine_collection_video_list.xml` | 列表分页、返回、标题、空态 |
| P1-5 | PGC 播放页 | `player_season_activity.cpp`、`video_view.xml` | 分集/系列/推荐 Tab；剧集切换、片头片尾、清晰度 |
| P1-6 | 直播播放页 | `live_player_activity.xml` | 直播视频 + 实时弹幕侧栏；关闭按钮；弱网重连提示 |
| P1-7 | 消息盒 | `inbox_view.xml` | 4 个 Tab；登录校验；面板开合焦点恢复 |
| P1-8 | 设置扩展 | `setting_activity.xml` | 实用工具 / 关于 / 开源许可；DLNA、二维码、语言、键位说明入口 |

### P2：增强与平台特性

- P2-1 TV 搜索：`search_activity_tv.xml` 的虚拟键盘 + 热搜/历史双栏。
- P2-2 动态详情、图片浏览：`dynamic_detail.xml`、`gallery_activity.xml`。
- P2-3 DLNA 投屏：`player_dlna_search.xml`。
- P2-4 PGC 索引筛选：`pgc_index_activity.xml`。
- P2-5 自定义主题、字体/图标覆盖、Anime4K/着色器、手柄振动测试、TV 风格 OSD。
- P2-6 启动教程与多语言完整覆盖。

### MVP 建议验收场景

1. **纯触摸**：1280×720dp 模拟器（或平板横屏）完成“进入首页 → 点推荐卡片 → 播放 → 双击暂停 → 横滑 seek → 返回”。
2. **纯遥控**：仅 D-pad + OK + Back 完成“侧栏 → 首页 Tab → 卡片 → 播放器 OSD → 清晰度 → 返回”，焦点不能丢失/跳到错误面板。
3. **混合**：触摸打开 OSD 后切到手柄，手柄操作应控制同一个播放器；手柄退出 OSD 后触摸仍可双击暂停。
4. **主题**：切换浅色/深色/自定义主题，所有文字/背景/焦点对比度正常，`checkThemeTokens` 无固定色违规。
5. **多语言**：zh-Hans / zh-Hant / en-US 至少三套完整翻译；长英文标题不撑破卡片，2 行省略。
6. **小屏**：544p 档位下不出现按钮重叠、卡片标题被截断、OSD 被安全区遮挡。

---

## 5. 与 blbl Android 现有布局的对接建议

### 5.1 对接总原则

1. **保留 blbl 的工程骨架**：`activity_main.xml`、`MainActivity.kt`、`SidebarNavAdapter.kt`、`BaseActivity`、`PlayerActivity`、`SettingsActivity`、`PlayerActivityTouch.kt` 是宿主；wiliwili 提供内容规格、视觉令牌和资源，不替换宿主导航/播放器引擎。
2. **根导航不重排**：blbl 的根导航 ID 是持久化状态的一部分（`SidebarNavAdapter.ID_SEARCH/HOME/CATEGORY/DYNAMIC/LIVE/MY`），不要为了复刻 wiliwili 三图标侧栏而改 ID 或删除页面。
3. **wiliwili 的“二级结构”嵌入 blbl 的根页面**：首页五个 Tab 嵌入 `HomeFragment`，动态两个 Tab 嵌入 `DynamicFragment`，我的六个 Tab 嵌入 `MyTabsFragment`。
4. **wiliwili 的固定像素值统一转 dp/sp**：现有 Android 工程已有 `UiDensity` 1.5 基准和 `UiScale`，接入时不要再按物理 px 写布局。
5. **所有新布局使用 theme attr**：遵守 `app/build.gradle.kts` 的 `checkThemeTokens`；wiliwili XML 中的十六进制色必须转换。

### 5.2 根导航映射表

| wiliwili 页面 | blbl 现有入口 | 落地方式 | 备注 |
|---|---|---|---|
| 首页（直播/推荐/热门/追番/影视） | `HomeFragment`（`tab_recommend/popular/bangumi/cinema`） | 在 `HomePagerAdapter.kt` 增加 `KEY_LIVE`，Tab 顺序对齐 wiliwili：直播 / 推荐 / 热门 / 追番 / 影视；推荐页复用 `VideoGridFragment` | `fragment_home.xml` 已有 `TabLayout + ViewPager2`，是最自然的接入点 |
| 动态（视频/综合） | `DynamicFragment` / `fragment_dynamic.xml` | 左侧 UP 主列表保持 blbl 现有 `recycler_following`；右侧增加“视频/综合”Tab；综合内容加载 `dynamic_detail` 样式 | 不要把动态改成 wiliwili 主壳的一级图标 Tab；动态在 blbl 是根导航 |
| 我的（历史/收藏/订阅/追番/追剧/稍后再看） | `MyTabsFragment` / `fragment_my_tabs.xml` / `MyPagerAdapter.kt` | 在 `MyTabs` 中补齐 `subscription`；复用 `KEY_FAV` 承载收藏夹，`KEY_TOVIEW` 承载稍后再看 | `MyTabsFragment` 已有 D-pad 焦点和返回策略，直接用 |
| 搜索 | `SearchFragment`（`fragment_search.xml`） | 保留 blbl 搜索框和虚拟键盘；结果 Tab 对齐 wiliwili 历史/热搜/视频/番剧/影视；排序条参考 `search_order.xml` | blbl 搜索已支持 D-pad 键盘，优先复用 |
| 分类 / PGC 筛选 | `CategoryFragment` / `PgcRecommendGridFragment` | wiliwili 追番/影视筛选改成 Android 的 chips/抽屉；PGC 索引可做成独立 Activity | 不要引入 wiliwili 的 `<AutoTabFrame>` 标签 |
| 直播 | `LiveFragment` + `HomeTabs.KEY_LIVE` | 二选一：保留直播根页面，同时首页加直播 Tab；两者共用一个 `LiveGridFragment` | 直播在 wiliwili 中是首页 Tab，在 blbl 中是根页面，**共用内容组件**即可避免重复 |
| 设置 | `SettingsActivity` / `activity_settings.xml` | 将 wiliwili 六组设置映射到 blbl 设置列表；新增 wiliwili 播放/界面项 | 不建议复制 wiliwili 的左侧 240 侧栏到手机；TV 可用双栏，手机用单栏 |
| 消息盒 | 暂无完全对应 | 新增 `InboxActivity` 或右侧面板，布局参考 `inbox_view.xml` | 需要登录校验；P2 |
| 视频详情/播放 | `PlayerActivity` / `activity_player.xml` | 以 wiliwili 的信息架构补全：标题/UP 主/点赞/投币/收藏/分享/数据；右侧评论/分集/推荐复用 blbl side panel | 播放器内核保留 ExoPlayer/Ijkplayer；不接 MPV |
| 番剧/影视播放 | blbl 现有视频/番剧入口 | 复用同一个 `PlayerActivity`，按内容类型切换分集/系列面板 | 不要新建第二套播放器 Activity |

### 5.3 布局文件级接入建议

| wiliwili 布局 | blbl 对接文件 | 具体做法 |
|---|---|---|
| `resources/xml/activity/main.xml` | `app/src/main/res/layout/activity_main.xml` | 保留 `sidebar` + `main_container`；主壳图标风格换成 wiliwili 皮肤，但侧栏导航逻辑沿用 `SidebarNavAdapter` |
| `resources/xml/fragment/home_tab.xml` | `app/src/main/res/layout/fragment_home.xml` | `TabLayout` 的 Tab 顺序改为直播/推荐/热门/追番/影视；右上角搜索框改为 `TextInputLayout` 或 `MaterialCardView + EditText`（wiliwili 的 300×35 圆角 6 规格） |
| `resources/xml/fragment/home_recommends.xml` | `app/src/main/res/layout/fragment_video_grid.xml` | 保留 `SwipeRefreshLayout + RecyclerView`；`padding=8dp` 调整为 wiliwili 的左右 20dp；视频卡片用统一 item layout |
| `resources/xml/views/video_card.xml` | `app/src/main/res/layout/item_video_card.xml` | 现有 16:9 封面、10dp 圆角、focus 描边、2 行标题可直接作为基底；增加 wiliwili 的推荐理由/成就角标（`video/card/rcmd_box`、`achievement_box`） |
| `resources/xml/fragment/dynamic_tab.xml` | `app/src/main/res/layout/fragment_dynamic.xml` | 保持左侧 `RecyclerView` 宽度 220dp 左右；右侧加 TabLayout + ViewPager2；视频网格和综合单列分别用两个 Fragment |
| `resources/xml/fragment/mine_tab.xml` | `app/src/main/res/layout/fragment_my_container.xml`、`fragment_my_tabs.xml` | 用户头部卡改为 `MaterialCardView` 或普通 `LinearLayout`；统计数字用 `?attr/colorOnSurface`，标签用 secondary；下面接 `MyPagerAdapter` |
| `resources/xml/activity/player_activity.xml` | `app/src/main/res/layout/activity_player.xml` | 左侧播放器区域不再套 wiliwili 的 `VideoView`；在 `PlayerView` 上叠加统一的 OSD 容器和弹幕 `DanmakuView`；信息区用右侧 panel/BottomSheet |
| `resources/xml/views/video_view.xml` | `app/src/main/res/layout/view_player_touch_overlay.xml` + `activity_player.xml` 的 OSD 部分 | 采用 wiliwili 的顶部 80dp / 底部 120dp 分区和按钮层级；手势实现使用 blbl 已有 `PlayerActivityTouch.kt` |
| `resources/xml/activity/setting_activity.xml` | `app/src/main/res/layout/activity_settings.xml` | 复用 blbl `SettingsLeftAdapter` / `SettingsEntryAdapter`；wiliwili 六组改成设置分类，不复制 240 宽侧栏的视觉尺寸到手机 |
| `resources/xml/fragment/inbox_view.xml` | 新建 `activity_inbox.xml` 或 `view_inbox_panel.xml` | 600 宽右侧面板在平板上可用；手机上改为全屏 Activity + Toolbar 返回 |
| `resources/xml/activity/search_activity_tv.xml` | `app/src/main/res/layout/fragment_search.xml` 的 TV 分支 | 已有很多 D-pad 键盘与搜索逻辑，建议只对齐 Tab 名称和排序项，不重写输入法 |

### 5.4 主题 / 样式接入

1. 在 `app/src/main/res/values/attrs.xml` 增加 wiliwili 需要的语义 attr（不要直接使用 wiliwili 的固定色）：
   - `blblSurfaceMuted`：灰 1/灰 2 背景
   - `blblSkeleton`：骨架屏
   - `blblScrim`：半透明角标/遮罩
   - `blblWarningSurface` / `blblWarningText`：`yellow_1` / `font/yellow_1`
   - `blblLink`
   - `blblDivider`
   - `blblDanger`
   - `blblCaption`（可选）
2. 在 `app/src/main/res/values/themes.xml` 为以下主题补值：
   - `Theme.Blbl.Base.Dark`：映射到 wiliwili 深色值
   - `Theme.Blbl.Base.PinkLight`：映射到 wiliwili 浅色值
   - 新建 `Theme.Blbl.WiliPink`（或 `Theme.Blbl.Base.Wili`）：`blblAccent = #FF6699`，`blblAccentContainer = #FFEDF3`（浅色）/ 由主题生成；不要改掉现有 `blbl_accent_tv_pink = #FB7299`
3. 所有 layout 使用 `?attr/blblAccent`、`?attr/colorOnSurface`、`?attr/blblSurfaceMuted` 等；禁止新增固定 `#FF6699`、`#E3E5E7` 到 layout。
4. 图片/SVG 的着色：
   - 普通可交互图标：`app:tint="?attr/blblOnPageBackdropSecondary"` 或 `?attr/blblAccent`（选中态）。
   - 播放器图标：保持白色 `#FFFFFF`，由黑色 scrim 保证对比。
   - 粉色主题 SVG：优先使用 vector drawable tint，而不是静态 `#FF6699` 填充。
5. 字体：
   - 默认 `sans-serif` / `sans-serif-medium`（Android 自带 Noto CJK 覆盖中日韩）；
   - 如需自定义字体，放在 `res/font/wili_regular.ttf`，通过 `fontFamily` 引用；不要把 8MB 的 `switch_font.ttf` 直接打包为默认字体；
   - 数字文本建议 `fontFeatureSettings="tnum"`，避免播放量跳动。

### 5.5 资源复用方式

| wiliwili 资源 | 数量/规模 | 可复用性 | 接入做法 |
|---|---|---|---|
| `resources/svg/*.svg` | 129 个，约 532KB | 高 | 优先转 Android VectorDrawable；同语义图标优先用 blbl 现有 `ic_nav_*` / `ic_player_*`，只补缺失图标（投屏、弹幕设置、全屏、音量、锁定、点赞/投币/收藏等）。转换后用 `tint` 控制颜色 |
| `resources/pictures/*.png` | 空态、默认头像、视频卡片底图等 | 中 | 复制为 `res/drawable-nodpi/` 或压缩为 WebP；`video-card-bg.png` 只做占位，不要作为卡片背景铺满 |
| `resources/xml/**` | 101 个 XML（10 activity、56 fragment、35 view） | 结构参考，不可直接 inflate | 按第 5.3 节映射到 Android XML/Compose；保留 id 语义（建议 `wili_` 前缀）方便对照排查 |
| `resources/i18n/*/wiliwili.json` | 7 个 locale，每个 359 个叶子键 | 高 | 将嵌套 JSON 展平为 `strings.xml`；key 转换示例：`wiliwili.home.live.tab` → `wili_strings_home_live_tab`；不要直接硬编码中文 |
| `resources/i18n/*/hints.json` | 7 个 locale，每个 17 个键 | 高 | 与 `hints` 命名空间合并；`ok/back/cancel` 等通用键优先用 Android 现有 `strings.xml` 或改名避免冲突 |
| `resources/font/*.ttf` | 8.9MB，含 8MB `switch_font.ttf` | 低 | 不默认打包；仅作为可选主题字体。`keymap_*.ttf` 是手柄/键盘提示图标字体，Android 用普通 SVG 图标替代 |
| `resources/material/MaterialIcons-Regular.ttf` | 125KB | 低 | 现有工程已有自己的图标体系，不引入以减小包体 |
| `resources/icon/*`、`pictures/*` | 图标、空态、加载失败图 | 高 | 按原用途转为 drawable；启动图标/横幅需要 Android mipmap 专项适配 |

**i18n 落地细节：**

- wiliwili 的 i18n 文件按目录语言加载，JSON 是嵌套结构；borealis 以 `/wiliwili/home/live/tab` 路径查询（见 `library/borealis/library/lib/core/i18n.cpp`）。
- Android 建议保留“`wiliwili.` 命名空间 + 下划线化”的 key，例如：
  - `wiliwili/home/live/tab` → `wili_home_live_tab`
  - `wiliwili/player/quality` → `wili_player_quality`
  - `wiliwili/setting/app/ui/header` → `wili_setting_app_ui_header`
- locale 映射：
  - `zh-Hans` → `values-zh-rCN`（可再提供 `values-zh` fallback）
  - `zh-Hant` → `values-zh-rTW` / `values-zh-rHK`
  - `en-US` → `values`（默认英文）或 `values-en`
  - `ja` → `values-ja`；`ko` → `values-ko`；`it` → `values-it`
  - `ja-RYU` 无标准 Android locale 时保留原始 JSON asset 作为高级回退，或映射到 `values-ja` 的衍生资源。
- 翻译文件中的 `{}` 占位符（如 `current_speed: "{} 倍速播放中"`）需要转换为 Android `%1$s` / `%1$d` 格式；不要直接把 `{}` 交给 `String.format`。

### 5.6 播放器对接具体建议

1. **保留 blbl 播放器引擎和 Activity**：
   - `app/src/main/res/layout/activity_player.xml` 已有 `PlayerView`、`DanmakuView`、`player_touch_overlay_stub`、`top_bar`、UP 快捷卡等；
   - `app/src/main/java/blbl/cat3399/feature/player/PlayerActivityTouch.kt` 已有完整的触摸手势状态机；
   - `PlayerActivityControlsSeek.kt` / `PlayerActivitySidePanels.kt` 已有 D-pad seek 和侧栏焦点。
2. **只补 wiliwili 的 OSD 信息架构**：
   - 顶部：标题、在线人数、投屏、设置；
   - 中央：加载 spinner、倍速提示、音量/亮度/seek 提示；
   - 底部：播放/暂停、进度条、时间、音量、弹幕设置、弹幕开关、清晰度、倍速、全屏；
   - 左上/右侧：视频简介和 OSD 锁定按钮。
3. **不要移植 `VideoView`/`MPVCore`**：wiliwili 的 `wiliwili/source/view/video_view.cpp` 依赖 MPV 事件和 OSD 绘制；Android 应由 `PlayerActivity` + Media3/Ijkplayer 统一驱动。
4. **清晰度/倍速选项来源**：
   - 清晰度用现有 `PlaybackSettingChoices` / `VideoPlayStream` 的 qn 列表；
   - 音轨/字幕/编码沿用现有播放器设置；
   - 不做 wiliwili 的 `VideoDetail::defaultQuality` 复制，统一到 blbl 设置。
5. **触摸锁**：`view_player_touch_overlay.xml` 的 `btn_touch_lock` 可直接承载 wiliwili 的 OSD 锁；锁定后只允许单击显示解锁按钮，不允许 seek/音量/亮度/双击。
6. **全屏和返回**：
   - 播放 Activity 保持 `sensorLandscape`；
   - Back 顺序：关闭 BottomSheet/侧栏 → 退出全屏 → 结束播放页；
   - 不复制 wiliwili 的 `BUTTON_BACK` 打开简介行为，简介改为点击标题/信息按钮。

### 5.7 焦点与触摸对接建议

- **焦点层直接复用 blbl**：
  - 网格：`DpadGridController.kt`，已有左右边界、上下行、触底加载更多、焦点保护；
  - Tab：`TabLayoutFocus.kt`、`TabContentFocusTarget.kt`、`requestFocusSelectedTab()`；
  - 返回：`BackPressHandler` + `MainActivity` 的 `OnBackPressedCallback`；
  - 焦点恢复：`FocusReturn.kt`、`FocusTreeUtils.kt`。
- **wiliwili 的 `AutoTabFrame` 行为转译为 Android**：
  - `sidebarPosition=top` → `TabLayout` + `ViewPager2`；
  - `sidebarPosition=left` → 主壳侧栏 RecyclerView，二级设置页可用左列表；
  - `demandMode=false` → ViewPager2 `offscreenPageLimit=所有页` 或懒加载 Fragment；`demandMode=true` → 仅创建当前页；
  - `defaultTab` → `ViewPager2.setCurrentItem(index, false)`。
- **触摸时禁用焦点环**：非输入控件用 `focusableInTouchMode=false`；只有 EditText 和明确需要触摸焦点的控件允许。
- **列表卡片动作**：wiliwili 的 `TapGestureRecognizer` 只区分点击；Android 长按菜单使用 `setOnLongClickListener`，不要把一个普通点击拆成多个手势阶段。

### 5.8 建议的落地顺序（可直接拆 PR）

1. **PR-1 令牌与主题**：`attrs.xml`、`themes.xml`、`dimens.xml`、基础 focus drawable；不改业务页面。
2. **PR-2 壳层与首页 Tab**：在 `HomePagerAdapter` 增加直播 Tab；把首页 Tab 改为 wiliwili 顺序；右侧搜索框样式对齐。
3. **PR-3 视频卡片与网格**：升级 `item_video_card.xml`，接入推荐理由/成就角标、16:9 封面；统一 `WiliVideoGridFragment`。
4. **PR-4 搜索闭环**：搜索 Tab 名称/排序对齐；结果卡片复用 PR-3。
5. **PR-5 播放器 OSD**：在现有 `activity_player.xml` 上叠加 wiliwili OSD 样式；不换播放器内核；先完成播放/暂停、seek、清晰度、倍速、弹幕、锁。
6. **PR-6 动态 / 我的 / 设置**：逐页替换内容容器；保留 blbl 的焦点和返回框架。
7. **PR-7 资源与 i18n**：SVG 批量转 VectorDrawable，i18n JSON 展平生成 `strings.xml`，删除临时硬编码。
8. **PR-8 TV/小屏收口**：544p、800dp、1280dp、1920dp 四档截图回归；D-pad-only 全链路回归。

### 5.9 对接验收清单

- [ ] `activity_main.xml` 的 blbl 根导航 ID、状态保存、侧栏收展逻辑未破坏。
- [ ] 首页五个 Tab 与 wiliwili `home_tab.xml` 顺序一致：直播 / 推荐 / 热门 / 追番 / 影视。
- [ ] `HomeTabs`、`MyTabs` 的用户可见 Tab 配置和偏好保存兼容。
- [ ] 视频卡片在触摸、D-pad、遥控鼠标三种输入模式下都能点击到同一内容。
- [ ] 播放器无第二套手势状态机；所有输入共享同一播放状态。
- [ ] 所有新增颜色通过 theme attr 取自 `themes.xml`，`checkThemeTokens` 通过。
- [ ] 所有 UI 文案来自 `strings.xml`（由 wiliwili i18n 生成），无新增硬编码中文。
- [ ] 至少验证 `zh-Hans`、`zh-Hant`、`en-US`；长英文标题不越界。
- [ ] 1280×720dp 截图与 wiliwili `docs/images/screenshot-1.png`、`screenshot-2.png` 的层级、尺寸关系一致；允许色值因 blbl 主题预设不同。

---

## 附录 A：wiliwili 自定义 XML 组件 → Android 组件映射

来源：`wiliwili/source/utils/register_helper.cpp` 的 `initCustomView()`。

| wiliwili 自定义标签 | Android 建议实现 | 备注 |
|---|---|---|
| `AutoTabFrame` | `TabLayout + ViewPager2`（顶部）；或 `RecyclerView + FragmentContainerView`（左侧） | 保留 `sidebarPosition`、`defaultTab`、`demandMode` 语义 |
| `RecyclingGrid` | `RecyclerView + GridLayoutManager/StaggeredGridLayoutManager` | `spanCount`、`itemHeight`、`onNextPage` 映射到 span/预取/触底加载 |
| `VideoView` | `PlayerView` + `DanmakuView` + 自定义 OSD `FrameLayout` | 不移植 MPV |
| `VideoProgressSlider` | `SeekBar` / Material `Slider` + 时间预览/缓冲条 | wiliwili 有分段/高能点，MVP 可后置 |
| `SVGImage` | `AppCompatImageView` + VectorDrawable | 支持 `app:tint` |
| `QRImage` | `ImageView`（二维码 bitmap） | 复用现有 `QrLoginActivity` |
| `TextBox` | `TextView`/`AppCompatTextView`（多行、行高、`maxRows`） | 2 行标题用 `maxLines=2` + `ellipsize=end` |
| `CustomButton` | `MaterialCardView`/`ImageButton`/`MaterialButton` | 触摸目标 ≥48dp |
| `GalleryView` | `ViewPager2` + `PhotoView`/手势缩放 | 参考 `gallery_activity.xml` |
| `HintLabel` | `TextView`（小号按键提示） | 触摸设备可隐藏；TV/遥控显示 |
| `UserInfoView` | 自定义组合 View / `item_video_detail_header` | 头像、昵称、关注数、勋章 |
| `UpUserSmall` | 自定义 Adapter item | 动态左侧列表 |
| `VideoComment` | 自定义 RecyclerView item | 评论树/回复 |
| `ButtonClose` | `ImageButton` 48dp | 圆形/圆角 50 |
| `CheckBox` | `MaterialCheckBox` | 设置页 |
| `SelectorCell` | 设置项 TextView + chevron | 设置页 |
| `AnimationImage` | `ImageView` + `AnimationDrawable`/Lottie（按需） | 投币动画等 |
| `ShareBox` | `BottomSheetDialog`/自定义 Dialog | 分享面板 |
| `DynamicVideoCardView` / `DynamicArticleView` | RecyclerView item types | 动态双列/单列适配 |

## 附录 B：关键文件索引

### wiliwili 侧

| 文件 | 用途 |
|---|---|
| `resources/xml/activity/main.xml` | 主壳、侧栏、底部消息/设置 |
| `wiliwili/source/activity/main_activity.cpp` | 主壳动作、焦点自定义、消息/设置点击 |
| `resources/xml/fragment/home_tab.xml` | 首页五个 Tab 与右上搜索 |
| `resources/xml/fragment/home_recommends.xml` | 推荐网格 |
| `resources/xml/fragment/home_hots.xml` | 热门二级 Tab |
| `resources/xml/fragment/home_live.xml` | 直播分区与网格 |
| `resources/xml/fragment/dynamic_tab.xml` | 动态双栏 |
| `resources/xml/fragment/mine_tab.xml` | 我的用户卡与 6 Tab |
| `resources/xml/fragment/search_tab.xml` | 搜索一级 Tab |
| `resources/xml/fragment/search_order.xml` | 搜索结果排序二级 Tab |
| `resources/xml/activity/player_activity.xml` | UGC 播放页信息架构 |
| `resources/xml/views/video_view.xml` | 播放器 OSD 骨架 |
| `resources/xml/views/video_card.xml` | 普通视频卡片 |
| `resources/xml/activity/setting_activity.xml` | 设置页分组 |
| `resources/xml/activity/live_player_activity.xml` | 直播播放页 |
| `resources/xml/fragment/inbox_view.xml` | 消息盒 |
| `wiliwili/source/utils/register_helper.cpp` | 主题色、自定义 metric、组件注册 |
| `wiliwili/source/view/video_card.cpp` | 卡片数据绑定、推荐理由/成就 |
| `wiliwili/source/view/auto_tab_frame.cpp` | 侧栏/顶部 Tab 交互与尺寸 |
| `wiliwili/source/view/video_view.cpp` | 播放器 OSD 动作与快捷键 |
| `wiliwili/source/utils/gesture_helper.cpp` | 播放器触摸手势状态机 |
| `wiliwili/source/utils/config_helper.cpp` | 快捷键默认值、UI 缩放档位 |
| `wiliwili/source/utils/shortcut_helper.cpp` | 快捷键解析 |
| `wiliwili/source/view/svg_image.cpp` | SVG 加载与 `#FF6699` 替换 |
| `wiliwili/source/utils/activity_helper.cpp` | 页面导航深链 |
| `resources/i18n/*/wiliwili.json` | 7 语言文案 |
| `resources/i18n/*/hints.json` | 通用按键/提示文案 |

### blbl Android 侧（当前机器 `/Users/mac/Documents/trae_projects/blbl-main`）

| 文件 | 用途 |
|---|---|
| `app/src/main/res/layout/activity_main.xml` | 侧栏 + `main_container` |
| `app/src/main/java/blbl/cat3399/ui/MainActivity.kt` | 根导航、焦点、返回 |
| `app/src/main/java/blbl/cat3399/ui/SidebarNavAdapter.kt` | 侧栏项与选中态 |
| `app/src/main/java/blbl/cat3399/ui/MainRootNavRegistry.kt` | 根导航映射 |
| `app/src/main/res/layout/fragment_home.xml` | 首页 TabLayout + ViewPager2 |
| `app/src/main/java/blbl/cat3399/feature/home/HomeFragment.kt` | 首页 Tab 焦点/返回 |
| `app/src/main/java/blbl/cat3399/feature/home/HomePagerAdapter.kt` | 首页 Tab 列表（增加直播 Tab 的入口） |
| `app/src/main/res/layout/fragment_video_grid.xml` | 视频网格 + 下拉刷新 |
| `app/src/main/res/layout/item_video_card.xml` | 现有视频卡片基底 |
| `app/src/main/res/layout/fragment_my_tabs.xml` | 我的 TabLayout + ViewPager2 |
| `app/src/main/java/blbl/cat3399/feature/my/MyPagerAdapter.kt` | 我的 Tab 映射 |
| `app/src/main/res/layout/activity_player.xml` | 现有播放器布局 |
| `app/src/main/res/layout/view_player_touch_overlay.xml` | 触摸层与锁定按钮 |
| `app/src/main/java/blbl/cat3399/feature/player/PlayerActivityTouch.kt` | 触摸手势状态机 |
| `app/src/main/java/blbl/cat3399/feature/player/PlayerActivityControlsSeek.kt` | D-pad seek/按键处理 |
| `app/src/main/java/blbl/cat3399/core/ui/DpadGridController.kt` | 网格焦点控制与加载更多 |
| `app/src/main/java/blbl/cat3399/core/ui/GridSpanPolicy.kt` | 栅格列数策略 |
| `app/src/main/java/blbl/cat3399/core/ui/UiDensity.kt` / `UiScale.kt` | 1.5 基准密度与用户缩放 |
| `app/src/main/res/values/attrs.xml` | theme 语义 attr |
| `app/src/main/res/values/themes.xml` | 深/浅/品牌主题 |
| `app/src/main/res/values/colors.xml` / `dimens.xml` | 现有颜色与尺寸 |
| `app/src/main/res/animator/blbl_focus_scale.xml` | 焦点缩放动画 |
| `app/src/main/res/drawable/blbl_focus_bg_round.xml` | 焦点背景/描边 |
| `app/build.gradle.kts` | `checkThemeTokens` 约束 |

## 附录 C：风险与处理

| 风险 | 影响 | 处理 |
|---|---|---|
| `#FF6699` 与 blbl `#FB7299` / 紫色默认主题冲突 | 视觉不一致、用户主题切换后颜色跳变 | 新建 `WiliPink` preset；所有业务控件只读 `?attr/blblAccent`；不在 layout 写死 |
| wiliwili XML 中大量固定色 | blbl `checkThemeTokens` 失败，深色模式不可用 | 逐项映射到 theme attr；增加 lint/检查；禁止新增固定色 |
| 逻辑 1280×720 与真实手机窄屏差异 | 5 列卡片溢出、右侧栏挤压 | 用 `w600dp`/`sw600dp` 分支；手机上把播放器信息区改为上下布局、侧栏改抽屉 |
| 固定 `itemHeight` 造成封面变形 | 卡片视觉不还原 | Android 用 16:9 封面 + 自适应文本区；只在基准截图回归时对照 250dp 视觉高度 |
| `RecyclingGrid` 与 RecyclerView 焦点行为不同 | D-pad 焦点跳到侧栏、长列表无法触底加载 | 采用 `DpadGridController`；补 `onTopEdge/onLeftEdge/canLoadMore`；测试边界 |
| `AutoTabFrame` 焦点即切换与 Android 触摸冲突 | 触摸时误切换 Tab | `TabLayout` 只在 `OnTabSelected`/触摸点击切换；TV 可选 focus-to-select |
| 播放器双状态机（wiliwili 手势 + blbl 手势） | 双击/滑动重复触发、播放状态错乱 | 只保留 blbl `PlayerActivityTouch`；wiliwili 只提供 OSD 布局和功能清单 |
| i18n `{}` 占位符与 Android `%1$s` 不兼容 | 运行时崩溃/显示原文 | 生成脚本转换占位符；CI 检查未翻译/格式错误 |
| 大量 SVG 转 VectorDrawable 失败 | 图标缺失或体积膨胀 | 先转换高频 20–30 个；低优先图标保留 PNG；检查 `fill`/`stroke` 和 tint |
| 遥控器 Back 与系统手势冲突 | 退出应用或误退全屏 | 统一 `OnBackPressedCallback`/`BackPressHandler`；播放页明确返回层级 |

---

### 参考截图

- `docs/images/screenshot-1.png`：搜索/播放器分栏/评论侧栏/PGC 筛选的整体层级参考。
- `docs/images/screenshot-2.png`：首页二级 Tab、推荐/热门/追番/影视、动态双栏和卡片密度参考。
- `docs/images/screenshot-3.jpg`：2560×1440 桌面高分辨率下的主壳、我的、搜索、播放页细节参考。
