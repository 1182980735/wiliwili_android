# Android 移植阶段四十七：buvid3 修复与下一视频优先级

> 个人自用、仅技术验证，不对外分发。

## 1. buvid3 以 infoc 结尾时重新生成

修复 `WebCookieMaintainer.ensureWebFingerprintCookies()`：

- 识别 `buvid3` 是否以 `infoc` 结尾（忽略大小写和首尾空格）。
- 命中时视为无效指纹，强制重新访问首页并请求 finger/spi。
- 即使 `buvid4` 已存在，也会触发 SPI 请求。
- SPI 返回新 `b_3` 时覆盖旧的 `buvid3`。
- 新增单元测试覆盖 `INFOC`、小写、空值、普通值。

## 2. 下一个视频优先级

手动点击“下一个”以及播放模式 `分P/合集/推荐优先` 时，按以下顺序查找：

```text
子分段（分P）下一段
  > 同合集下一个视频
  > 推荐的下一个视频
```

改动：

- `PlayerActivitySocialActions.kt`
  - 新增 `playNextByContentPriority()`
  - 新增 `playPageOrRecommendedNext()`
  - `NONE`、`EXIT`、`LOOP_ONE` 的手动下一个改为按内容优先级查找
  - `PARTS_LIST_THEN_RECOMMEND` 模式改为分P -> 合集 -> 推荐
- `PlayerActivityAutoNext.kt`
  - 自动连播同样按分P -> 合集 -> 推荐解析目标
  - 分P/合集还有下一页但尚未加载时，不提前跳到推荐

## 3. 进度拖动缩略图预览

全屏播放器已有完整实现，不需要重复添加：

- `PlayerVideoShot.kt`：请求 `/x/player/videoshot`、下载精灵图、切帧缓存
- `VideoShotPreviewView.kt`：绘制缩略图
- `PlayerActivityControlsSeek.kt`：全屏/瞬时 OSD 的缩略图位置和显隐
- `PlayerActivityStartPlayback.kt`：首帧后加载视频快照
- 设置入口：播放设置 -> 缩略图显示，默认 `medium`

本次只做代码路径核对，未改动画质/布局。

## 4. 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- 新增 `WebCookieMaintainerTest` 通过

## 发布说明

本次只提交代码，不创建 GitHub Release，也不上传 APK。
