# wiliwili Android 0.13

## 版本

- versionName: `0.13`
- versionCode: `13`
- package: `cn.xfangfang.wiliwili.poc`
- 构建类型：debug（固定 debug 签名，可覆盖安装 0.12）
- APK 大小：约 34 MB

## 播放器

- 内嵌播放器抽成 `BlblPlayerEngine` 接口
- 默认 ExoPlayer 硬件解码优先
- ExoPlayer 报错或连续卡顿后自动切 IjkPlayer 同画质软解
- 四套 ABI 的 `libijkplayer.so` 已打进 APK
- 取消自动降 360P/480P，恢复尊重用户默认画质
- ExoPlayer / IjkPlayer 增加音频焦点管理
- 内嵌播放器快捷按钮默认隐藏，点击显示，5 秒无操作自动隐藏

## 动态页

- 专栏从单列列表改为卡片网格
- 专栏使用综合非视频动态流，保留纯文字、图文、转发、文章动态
- 过滤 AV、PGC、直播回放、课程、合集等视频类型
- 纯文字动态读取 `module_desc.text`，卡片显示 UP 主正文
- 无封面卡片显示正文摘要，不再空白
- 详情弹窗改为 BiliClient 动态详情接口 + 原生渲染
- 不再用 WebView 请求 B 站 H5，避免 412 风控和 App 推广层
- 详情图片按原比例完整显示，支持多图和上下滚动
- 长图加载完成后动态计算高度，避免固定高度裁切
- 动态详情弹窗移除重复的顶部标题，只保留正文标题
- 修复纯文字动态因标题与正文相同而正文标题消失的问题

## 其他

- 保持进度缩略图预览
- 保持 buvid3 infoc 修复
- 保持下一个视频优先级：分P/子分段 → 同合集 → 推荐
- 保持电视遥控器/手柄映射与上游 blbl 对齐

## 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- ExoPlayer 硬件解码 480P 连续播放约 38 秒
- ExoPlayer 卡死后自动切 IjkPlayer
- Ijk 软解 `mediacodec-avc = 0`，持续播放超过 16 秒
- ExoPlayer / IjkPlayer 均持有 `USAGE_MEDIA / CONTENT_TYPE_MOVIE` 音频焦点
- 动态专栏实际安装验证：纯文字、图文、转发、文章均显示，视频类型已过滤
- 原生详情弹窗实际打开验证，无 412、无 FATAL / ANR

## 尚未验证

- 真实比亚迪车机 1080P 硬解稳定性
- 导航播报、电话、倒车影像抢占音频焦点
- Ijk 软解长期 CPU、发热和掉帧

APK SHA-256:

```text
42824493328ac3b14b9440e634a3575cfae92d8b60ab1185beef3beaeba9df2c
```
