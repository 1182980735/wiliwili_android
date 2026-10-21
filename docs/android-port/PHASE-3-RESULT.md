# Android 移植阶段三结果

> 个人自用、仅技术验证，不对外分发。来源记录见 android/SOURCE_RECORD.md。

## 1. SVG 图标批量转换

- 工具：`android/tools/svg_to_vector.py`
- 输入：`resources/svg/*.svg`，共 129 个
- 输出：`android/app/src/main/res/drawable/wiliwili_*.xml`，共 129 个
- 映射清单：`android/app/src/main/assets/wiliwili_icons_manifest.json`
- 支持：path、circle、ellipse、rect、line、polygon/polyline、fill/stroke、
  fill-rule、opacity、简单 CSS class、线性渐变、use、常见 translate/scale/rotate/matrix
- 说明：带 filter/mask 的播放/暂停状态图只保留几何与填充输出，未复刻阴影/遮罩滤镜；
  其余图标已按 SVG 几何转换。

主要资源名：

- `wiliwili_ico_home` / `wiliwili_ico_dynamic` / `wiliwili_ico_mine`
- `wiliwili_ico_setting`
- `wiliwili_header_search`
- `wiliwili_widget_video_play_count_grey`
- `wiliwili_widget_video_danmaku_grey`
- `wiliwili_widget_agree` / `wiliwili_widget_coin` / `wiliwili_widget_favorite`
- `wiliwili_bpx_svg_sprite_sort`

## 2. 视频卡片

- 12dp 圆角深灰卡片与圆角封面
- 粉色标题强调条
- 封面统计、时长、剧集信息改为半透明胶囊
- 两层标题 + UP/日期信息层级
- 焦点覆盖层与白色描边
- 长按动作面板保留，图标已替换为 wiliwili 图标

相关文件：

- `android/app/src/main/res/layout/item_video_card.xml`
- `android/app/src/main/res/values/dimens_video_wiliwili.xml`
- `android/app/src/main/res/drawable/bg_wiliwili_video_*`

## 3. 视频详情页头部

- 深灰圆角头部容器与圆角封面
- 粉色标题强调条、30sp 标题
- 半透明粉色统计胶囊
- UP 主卡片、角色文案和进入箭头
- 简介区、标签、合集/分 P 区域
- 粉色播放按钮
- 点赞/投币/收藏/评论动作按钮，带标签、激活色和白色焦点描边

相关文件：

- `android/app/src/main/res/layout/item_video_detail_header.xml`
- `android/app/src/main/java/blbl/cat3399/feature/video/VideoDetailHeaderAdapter.kt`
- `android/app/src/main/res/drawable/bg_wiliwili_detail_*`

## 4. 搜索 / 动态 / 我的

### 搜索

- 顶部 52dp 圆角搜索卡，粉色搜索图标强调
- 键盘、历史、热搜分成独立圆角面板
- 历史/热搜列表项统一圆角
- 结果区 Tab 使用粉色下划线与选中文字
- 排序按钮改为粉色胶囊
- 保留输入、长按删除历史、分页、返回与 D-pad 逻辑

### 动态

- 左侧 240dp UP 列表面板与标题栏
- 右侧内容卡增加视频/综合 Tab 和分割线
- D-pad 在 Tab 与内容网格之间联动
- 保留刷新与返回逻辑；未登录态提供二维码登录入口

### 我的

- 顶部 104dp 用户卡
- 头像、昵称、等级、UID、经验与四项统计
- 六个 Tab 的圆角 Tab 容器
- 未登录态登录卡与登录按钮
- 保留 ViewPager2、Tab 焦点、返回和刷新逻辑

相关文件：

- `android/app/src/main/res/layout/fragment_search.xml`
- `android/app/src/main/res/layout/fragment_dynamic.xml`
- `android/app/src/main/res/layout/fragment_my_tabs.xml`
- `android/app/src/main/res/layout/fragment_my_login.xml`
- `android/app/src/main/res/layout/fragment_my_container.xml`
- `android/app/src/main/res/values/dimens_pages_wiliwili.xml`
- `android/app/src/main/res/drawable/bg_wiliwili_page_*`

## 5. 验证

- `./gradlew assembleDebug`：通过
- `./gradlew testDebugUnitTest`：通过
- 真机/模拟器 Android 12、2560×1440 横屏：
  - 首页视频卡片与转换图标正常显示
  - 搜索页布局正常
  - 动态/我的未登录态布局正常
  - 长按卡片进入详情动作正常
  - 视频详情页头部正常渲染
  - 强制横屏保持正常
- 当前未登录，动态/我的登录后内容区仅完成静态布局和编译验证，未做登录态真机回归。

## 6. 登录态回归补充

在真实扫码登录后继续验证，发现并修复了一个缓存问题：

- 现象：扫码成功后头像已显示，但已缓存的动态 Fragment 仍停留在未登录提示。
- 原因：主界面 Activity 未重建，动态/我的旧 Fragment 视图没有随 Cookie 状态刷新。
- 修复：`MainActivity` 记录创建时的登录状态；`onResume` 检测到登录/退出登录变化时自动
  `recreate()` 主壳，使动态、我的等根页面按新登录态重新创建。

登录态验证结果：

- 动态页：左侧关注 UP 列表加载正常，右侧视频/综合 Tab 正常，视频网格与时长/统计胶囊正常。
- 我的页：用户昵称、头像、Lv6、UID、经验、动态/关注/粉丝/银币统计正常。
- 我的页 Tab：历史内容正常；收藏 Tab 可切换到收藏夹列表，内容数量正常。
- 搜索页：输入 `music` 并搜索，视频结果页正常返回，多列卡片、Tab、排序按钮和图标渲染正常。
- 搜索结果点击可进入 Media3 播放器。
- 无 `FATAL EXCEPTION`，强制横屏保持正常。

当前未覆盖：

- 真机物理遥控器/手柄的完整焦点路径（仅做了编译和按键事件冒烟）。
- 动态页综合 Tab、我的页追番/追剧/稍后再看/最近点赞的逐项加载回归。
