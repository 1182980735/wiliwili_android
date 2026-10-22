# Android 移植阶段四十：详情页右侧四个 Tab 等宽

> 个人自用、仅技术验证，不对外分发。

## 问题

视频详情页右侧：

- 评论
- 分集
- 推荐
- 投稿

四个 Tab 的文字长度不同（尤其带数量后），视觉宽度不一致。

## 修改

- 四个 Tab 的父容器继续使用 `layout_weight=1`，保证点击区域四等分。
- 四个文字 TextView 的宽度统一为 `96dp`。
- 文字保持 `gravity=center`、单行、末尾省略。
- 选中态颜色和下方 28dp 指示线逻辑不变。
- 不改变 Tab 点击、焦点和切换逻辑。

## 验证

- 实机详情页右侧四个 Tab 的文字占位宽度一致。
- 选中评论时，评论/分集/推荐/投稿四个槽位等距。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
