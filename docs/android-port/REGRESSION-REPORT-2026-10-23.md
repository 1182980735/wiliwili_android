# Android 全面功能回测报告（2026-10-23）

> 个人自用、仅技术验证，不对外分发。

## 测试环境

- MuMu Android 12，arm64 兼容环境
- 横屏 2560x1440
- 应用：wiliwili POC 0.1.0-wiliwili-poc

## 覆盖页面

- 首页：直播、推荐、热门、追番、影视
- 动态页：视频、专栏
- 我的页：历史记录、投稿、收藏、追番、追剧、稍后再看、最近点赞
- 搜索页：搜索键盘、热门搜索、历史、结果页
- 视频详情页：内嵌播放、右侧评论/分集/推荐/投稿 Tab、画质菜单
- 视频卡片长按菜单
- 消息中心：聊天列表、回复我的、@我的、收到的赞、聊天详情
- 设置页：通用设置、关于应用
- 全屏播放器：此前已覆盖，最新改动主要影响内嵌播放

## 发现并修复的问题

### 1. 内嵌播放器播放 4-5 秒后卡死/绿屏

**严重级别：高**

日志：

```text
BLBL/DanmakuPerf play=false
BLBL/InlinePlayer playback stalled position=...
状态 BUFFERING
```

原因：

- 内嵌请求 `fnval=1`，主要使用 progressive/durl
- 单 URL + 普通 OkHttpDataSource，无 CDN failover
- 曾强制软件解码回退 480P
- 新视频加载时首帧状态未及时重置

修复：

- 请求改为 `fnval=4048`
- 优先生成带 SegmentBase 的 DASH MPD
- 视频/音频接入 CDN failover
- stall retry 同步切换 CDN preferred index
- 取消强制 480P 回退
- 提前重置首帧和 stall watchdog 状态

结果：

- 多个视频连续播放超过 80 秒/17 秒/14 秒
- 未再出现固定 4-5 秒卡死
- 日志无 `FATAL EXCEPTION`、无 `ANR`、无 `ExoPlaybackException`

### 2. 视频卡片长按菜单第一项错误显示勾选

**严重级别：低**

“播放视频”菜单项显示勾选标记，像单选状态。

修复：

- `AppPopup.singleChoice` 支持 `checkedIndex=-1`
- 视频卡片操作菜单不再显示勾选

## 日志结论

最终回测日志：

```text
FATAL EXCEPTION: 0
ANR: 0
ExoPlaybackException: 0
app 级 ERROR/WARN: 0
```

仅有系统层正常编码器提示：

```text
CCodec: behavior subject to change
MediaCodec: mapFormat: no mediaType information
```

这些是设备/解码器提示，不是应用崩溃。

## 已知但未在本次修复的问题

- 动态专栏个别纯文字/图片动态卡片显示“动态”且封面为空。
- 我的页 Tab 结构与 wiliwili 仍不完全一致（我的订阅等）。
- 搜索页结果 Tab 仍有“直播/用户”额外入口。
- 聊天图片/表情当前测试会话没有样本，未做视觉验收。
- 播放结束提示清理未用自然播放到结尾做完整长视频回归。
- 全屏播放器 DLNA 仍是设备发现，不包含实际投屏推送。

## 结论

本轮未发现新的崩溃或 ANR。主要高优先级回归是内嵌播放器缓冲卡死，已通过 DASH MPD + CDN failover 修复；其余为已知功能差异或低优先级 UI 问题。
