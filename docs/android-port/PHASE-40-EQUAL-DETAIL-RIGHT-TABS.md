# Android 移植阶段四十：详情页右侧内容面板等宽

> 个人自用、仅技术验证，不对外分发。

## 实际需求

不是 Tab 点击区域宽度，而是切换：

- 评论
- 分集
- 推荐
- 投稿

后，右侧下方内容面板的宽度不一致。

## 原因

`VideoDetailActivity.updateDetailSplitForTab()` 使用了不同的分割比例：

```text
评论：0.66
分集/推荐/投稿：0.74
```

导致评论 Tab 的右侧面板更宽，其他三个 Tab 的右侧面板更窄，同时内嵌播放器宽度也会变化。

## 修改

- 四个 Tab 统一使用评论 Tab 的分割比例 `0.66f`。
- 切换 Tab 后右侧内容面板宽度保持一致。
- 内嵌播放器宽度也不再因 Tab 切换而变化。
- 撤销上一轮对 Tab 文字区域 96dp 宽度的实验性修改，恢复原布局。
- Tab 点击、焦点、选中颜色和指示线逻辑不变。

## 验证

- 评论、分集、推荐三个页面截图对比，右侧面板左边界一致。
- 切换到投稿使用同样比例。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
