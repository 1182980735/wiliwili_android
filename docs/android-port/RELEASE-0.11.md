# wiliwili Android 0.11

## 版本

- versionName: `0.11`
- versionCode: `11`
- package: `cn.xfangfang.wiliwili.poc`
- 构建类型：debug（固定 debug 签名，可覆盖安装 0.10）

## 自 0.10 以来的更新

- 详情页手柄 `LT/RT` 改为音量减/加
- 全屏播放器手柄 `LT/RT` 改为音量减/加
- 播放器引擎增加统一音量控制接口（ExoPlayer / IjkPlayer）
- `LB/RB` 保留原有快退/快进行为
- 左侧栏消息与设置图标尺寸对齐
- 新增 Android 上线手册

## 安装说明

APK 使用项目固定 debug 签名，可直接覆盖安装 0.10。

```text
wiliwili-android-0.11.apk
```

SHA-256：

```text
feaa45116210f8ea4c152c239cf8ce50005396115fa391d31c2858640a2ec162
```

## 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- 实机安装启动无 `FATAL EXCEPTION`
- 详情页 LT：100% → 95%，RT：95% → 100%
- 全屏 LT：100% → 95%，RT：95% → 100%

## 已知问题

- 部分动态专栏的纯文字/图片动态可能显示“动态”且封面为空
- DLNA 仅设备发现，不包含实际投屏推送
- 聊天图片/表情未在当前测试会话中做视觉验收
