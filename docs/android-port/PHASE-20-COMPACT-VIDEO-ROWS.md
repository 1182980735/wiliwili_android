# Android 移植阶段二十：详情页紧凑视频列表

> 个人自用、仅技术验证，不对外分发。

## 目标

对齐 wiliwili 视频详情页右侧推荐、分集、投稿列表样式：

- 左侧小缩略图
- 右侧标题
- UP 主和日期
- 一屏显示更多条目
- 保留点击进入视频详情

## 实现

- 新增 `CompactVideoRowAdapter`
- 新增 `item_video_compact_row.xml`
- 替换详情页右侧原来的大号 `VideoCardAdapter`
- 所有右侧列表统一使用紧凑横向条目：
  - 推荐
  - 分集
  - 投稿
- 缩略图显示播放量和时长
- 列表支持焦点高亮、点击打开详情
- 非评论 Tab 的右栏宽度调整为约 26%，保证紧凑条目可读
- 投稿数据单独缓存，避免切换“分集”后被分集列表覆盖

## 验证

- 推荐 Tab 显示紧凑横向条目
- 分集 Tab 显示紧凑横向条目
- 投稿 Tab 显示紧凑横向条目
- 一屏可显示约 6 条视频
- 四个 Tab 的数量标签仍正常显示
- 切换 分集 → 推荐 → 投稿 后列表内容正确
- 点击条目可进入视频详情
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
