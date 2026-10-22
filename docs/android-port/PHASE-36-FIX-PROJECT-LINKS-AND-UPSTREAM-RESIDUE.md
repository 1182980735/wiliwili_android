# Android 移植阶段三十六：修正项目地址与上游信息残留

> 个人自用、仅技术验证，不对外分发。

## 修正项

### 1. 设置页项目地址

- 旧值：`https://github.com/cat3399/blbl`
- 新值：`https://github.com/1182980735/wiliwili_android`

涉及 `SettingsConstants.PROJECT_URL`，设置页“关于应用 → 项目地址”的复制和打开入口同步生效。

### 2. SponsorBlock 请求 Referer

- 旧值：`https://github.com/cat3399/blbl`
- 新值：`https://github.com/1182980735/wiliwili_android`

涉及 `SponsorBlockApi` 的 `Origin` / `Referer` 请求头。

### 3. 更新源仓库

即使更新入口已关闭，`ApkUpdater` 仍保留旧仓库常量：

- 旧仓库：`1182980735/blbl`
- 新仓库：`1182980735/wiliwili_android`
- APK 资产名改为：
  - `wiliwili-latest-debug.apk`
  - `wiliwili-latest-release.apk`

当前更新功能仍不可从启动或设置入口触发，仅修正底层常量，避免以后误用旧更新源。

### 4. 上游 QQ 群残留

- 移除设置“关于应用”中的“QQ交流群”。
- 移除 `SettingsConstants.QQ_GROUP`、设置项枚举和处理分支。
- 原因：该群号来自上游 blbl，不是本项目维护的群。

## 保留项说明

- README 中保留上游 `cat3399/blbl`、`1182980735/blbl`、wiliwili 等链接，属于来源与致谢说明，不是应用内项目地址。
- 默认自动跳过服务器 `https://bsbsb.top` 暂时保留，属于播放器空降助手的功能配置；如果后续不再使用该服务，需要单独替换。
- 包名 `blbl.cat3399` 和 applicationId `cn.xfangfang.wiliwili.poc` 属于内部技术命名，本次未改，避免迁移配置和安装标识。

## 验证

- 设置页“项目地址”显示为：
  `https://github.com/1182980735/wiliwili_android`
- 关于应用页不再显示 QQ 交流群和更新入口。
- `assembleDebug` 和 `testDebugUnitTest` 通过。
- 无 `FATAL EXCEPTION`。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
