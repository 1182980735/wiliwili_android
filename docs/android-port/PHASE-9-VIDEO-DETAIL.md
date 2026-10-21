# Android 移植阶段九：wiliwili 风格视频详情页

> 个人自用、仅技术验证，不对外分发。

## 行为调整

点击视频卡片默认进入 `VideoDetailActivity`，不再直接全屏启动播放器。

- 详情页左侧展示 16:9 视频预览封面和中央播放按钮
- 点击预览或“播放”按钮后才进入全屏 `PlayerActivity`
- “播放前打开详情页”默认值改为开启
- 原有绿屏/花屏修复继续生效，播放器首帧输出正常

## 页面布局

`activity_video_detail.xml` 改为左右分栏：

- 左侧 66%
  - 视频预览封面
  - 标题、发布时间/播放/点赞等元信息
  - UP 主卡片
  - 简介与标签
  - 播放、点赞、投币、收藏、评论操作
- 右侧 34%
  - Tab：评论 / 合集 / 推荐 / 投稿
  - 评论列表与热门/最新排序
  - 真实相关推荐视频列表
  - 分P/合集视频列表
  - UP 主投稿列表

## 评论

复用现有 `VideoCommentsPanelController`：

- 评论根列表、分页加载
- 热门/最新排序
- 回复线程
- 图片评论入口
- 评论卡片背景改为主题 `colorSurface`，浅色详情页和播放器深色面板都能正确显示

## 相关推荐

- 复用 `BiliApi.archiveRelated()` 获取相关推荐
- 右侧“推荐”Tab 展示真实推荐视频
- 点击推荐视频继续进入详情页

## 合集与投稿

- “合集”Tab 展示当前视频的分P或 UGC 合集卡片
- “投稿”Tab 通过 `BiliApi.spaceArcSearchPage()` 加载当前 UP 主投稿
- 点击卡片继续进入对应视频详情页

## 验证

- 首页点击视频后焦点进入 `VideoDetailActivity`
- 左栏封面、标题、UP、标签和操作区正常显示
- 评论 Tab 加载真实评论
- 推荐 Tab 加载真实推荐视频
- 合集 Tab 显示分P/合集内容
- 投稿 Tab 加载 UP 主真实投稿
- 点击预览播放按钮进入 `PlayerActivity`
- 播放器日志出现 `exo:firstFrame` 和 `state=READY`
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
