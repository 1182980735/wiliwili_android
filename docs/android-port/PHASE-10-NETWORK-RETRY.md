# Android 移植阶段十：播放/刷新失败排查与网络重试

> 个人自用、仅技术验证，不对外分发。

## 排查结果

模拟器重启并重新连接 adb 后，对当前安装包进行复现：

- 首页连续点击 X 刷新 8 次：8 次全部成功
- 动态页连续点击 X 刷新 5 次：5 次全部成功
- 连续打开 3 个不同视频并播放：均成功输出首帧并进入 READY

播放日志示例：

```text
BLBL/Player: playurl:done
BLBL/Player: exo:firstFrame pos=0ms
BLBL/Player: player:state state=READY pos=0ms
BLBL/Player: player:state state=READY pos=492ms
```

刷新日志示例：

```text
BLBL/WebVideoApi: recommend source=web items=22
BLBL/VideoGrid: load ok source=recommend rid=0 page=1 add=22 total=22 cost=427ms
```

排查期间未发现：

- `FATAL EXCEPTION`
- `PlaybackException`
- `onPlayerError`
- HTTP 4xx/5xx
- B 站接口风控错误码
- WBI 签名异常

因此当前版本没有稳定复现的播放器或接口错误。之前的问题更可能来自模拟器网络短暂异常、
应用旧版本残留状态，或详情页第一版非法 SVG 导致的崩溃。

## 网络容错改进

为降低偶发网络抖动导致页面直接“加载失败”的概率，`BiliClient` 增加 GET 请求自动重试：

- 最多 3 次尝试
- `IOException` 自动重试
- HTTP 429 / 5xx 自动重试
- 退避间隔 250ms、500ms
- POST 请求不自动重试，避免重复提交
- 重试日志标签为 `BLBL/BiliClient`

## 验证

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`

均通过。

安装最新包后再次验证：

- 详情页正常打开
- 点击播放进入 `PlayerActivity`
- 视频首帧正常输出
- 无播放错误或崩溃
