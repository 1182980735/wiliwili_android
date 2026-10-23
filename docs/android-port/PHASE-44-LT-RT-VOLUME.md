# Android 移植阶段四十四：详情页 LT/RT 改为音量调节

> 个人自用、仅技术验证，不对外分发。

## 修改

视频详情页手柄映射：

- `LB` / `RB`：继续切换右侧 Tab
- `LT`：降低内嵌播放器音量
- `RT`：增大内嵌播放器音量

具体实现：

- 移除 `LT/RT` 切换 Tab 的行为。
- `LT` 每次音量 -5%。
- `RT` 每次音量 +5%。
- 音量范围限制在 0%-100%。
- 调整时更新 OSD 音量文字，并显示“音量 XX%”提示。
- 移除原先 `RT + 方向键上/下` 的组合音量逻辑。

## 验证

- 按 LT：内嵌播放器音量从 100% 降到 95%。
- 按 RT：音量从 95% 升回 100%。
- `LB/RB` 仍用于切换 Tab。
- `assembleDebug` 和 `testDebugUnitTest` 通过。

## 发布说明

本次只提交代码，不创建新的 GitHub Release，也不上传 APK。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
