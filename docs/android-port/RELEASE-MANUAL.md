# wiliwili Android 上线手册

> 个人自用、仅技术验证，不对外分发。本项目不是应用商店上架流程。

## 0. 发布纪律

必须遵守：

1. **只有用户明确要求“发布 Release / 上传 APK / 出新版本”时，才创建 GitHub Release 并上传安装包。**
2. 普通功能提交、Bug 修复、文档更新只提交代码并推送，不生成 Release。
3. 每次正式发布必须配套更新版本号、发布说明和 SHA-256。
4. 不覆盖已发布的 Release 安装包；修复问题应发新版本号。
5. 发布前必须完成构建、实机回归和日志门禁。

## 1. 当前项目信息

| 项目 | 值 |
|---|---|
| 工程目录 | `/Users/mac/Documents/trae_projects/wiliwili/android` |
| GitHub 仓库 | `1182980735/wiliwili_android` |
| 包名 | `cn.xfangfang.wiliwili.poc` |
| 当前正式版本 | `0.10` |
| 当前 versionCode | `10` |
| APK 命名 | `wiliwili-android-<version>.apk` |
| Release tag | `v<version>`，例如 `v0.10` |
| 签名 | 当前使用项目固定 debug 签名，可直接覆盖安装 |

**不要重新生成 `debug.keystore`。** 一旦签名变化，旧版本无法覆盖安装。

## 2. 版本号规则

在：

```text
android/app/build.gradle.kts
```

修改：

```kotlin
defaultConfig {
    versionCode = ... ?: 11
    versionName = ... ?: "0.11"
}
```

规则：

- `versionCode` 每次发布必须 +1，只能变大。
- `versionName` 使用用户指定的版本号。
- 未明确指定版本号时，不要自行发布。
- 本手册示例版本号统一用 `<VERSION>` 表示，例如 `0.11`。

## 3. 构建前检查

```bash
cd /Users/mac/Documents/trae_projects/wiliwili/android
git status --short
```

要求：

- 确认当前分支为 `main`
- 确认没有未预期改动
- 确认本次改动已完成代码审查

检查版本号：

```bash
grep -n 'versionCode\|versionName' app/build.gradle.kts
```

## 4. 构建

固定环境：

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export GRADLE_USER_HOME=/tmp/gradle-home3
export ANDROID_SDK_ROOT=/usr/local/share/android-commandlinetools
export ANDROID_HOME="$ANDROID_SDK_ROOT"
```

构建 debug 包并跑单元测试：

```bash
cd /Users/mac/Documents/trae_projects/wiliwili/android
./gradlew assembleDebug testDebugUnitTest --console=plain
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 关于 release 包

当前 GitHub Release 上传的是 debug 签名包，原因是仓库未提交 `keystore/release.keystore`。

如果以后确实需要 release 签名：

1. 把 release keystore 放到 `keystore/release.keystore`
2. 设置环境变量：
   - `RELEASE_STORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
3. 执行：
   ```bash
   ./gradlew assembleRelease
   ```

在 release keystore 未准备好之前，不要擅自切换为 release 包，否则签名不一致会导致无法覆盖安装。

## 5. 校验 APK

检查包名和版本号：

```bash
AAPT=$(find /usr/local/share/android-commandlinetools/build-tools -name aapt | head -1)
"$AAPT" dump badging app/build/outputs/apk/debug/app-debug.apk | head -1
```

要求输出包含：

```text
package: name='cn.xfangfang.wiliwili.poc' versionCode='<VERSION_CODE>' versionName='<VERSION>'
```

生成发布用文件名和 SHA-256：

```bash
VERSION='<VERSION>'
cp app/build/outputs/apk/debug/app-debug.apk "/tmp/wiliwili-android-${VERSION}.apk"
shasum -a 256 "/tmp/wiliwili-android-${VERSION}.apk"
```

记录 SHA-256，后面写入发布说明和最终回复。

## 6. 实机回归

设备：

```bash
ADB=/usr/local/share/android-commandlinetools/platform-tools/adb
$ADB devices
```

安装：

```bash
$ADB -s 127.0.0.1:16416 install -r "/tmp/wiliwili-android-${VERSION}.apk"
```

启动并清日志：

```bash
$ADB -s 127.0.0.1:16416 shell am force-stop cn.xfangfang.wiliwili.poc
$ADB -s 127.0.0.1:16416 logcat -c
$ADB -s 127.0.0.1:16416 shell am start -n cn.xfangfang.wiliwili.poc/blbl.cat3399.ui.MainActivity
```

### 6.1 冒烟测试清单

首页：

- [ ] 启动无崩溃
- [ ] 推荐、热门、追番、影视可切换
- [ ] 直播子 Tab 为“关注 / 推荐 / 分区”
- [ ] 直播页可用摇杆从顶部 Tab 进入卡片，左右可移动

详情页：

- [ ] 视频卡片可进入详情
- [ ] 内嵌播放器能出首帧，进度持续增长
- [ ] 画质菜单可打开和切换
- [ ] 右侧评论/分集/推荐/投稿可切换
- [ ] `LB/RB` 可切换 Tab
- [ ] `LT/RT` 可调节内嵌播放器音量
- [ ] `A` 确认、`B` 返回正常
- [ ] 全屏可进入，且继承内嵌播放进度

其他页面：

- [ ] 搜索：键盘、热门搜索、历史、结果页正常
- [ ] 动态：视频/专栏可切换
- [ ] 我的：历史/投稿/收藏等 Tab 可切换
- [ ] 消息中心：聊天列表、回复、@、点赞、聊天详情正常
- [ ] 设置：通用设置、播放设置、弹幕设置、关于应用可打开
- [ ] 左侧栏消息/设置图标尺寸一致，点击区域正常
- [ ] 长按视频卡片菜单正常，第一项无错误勾选

### 6.2 播放器观察点

- 观察进度不能长时间停在固定位置
- 观察 `DanmakuPerf play=true`
- 不能出现持续绿屏/黑屏
- 切视频后旧“播放结束”提示不能残留

### 6.3 日志门禁

```bash
$ADB -s 127.0.0.1:16416 logcat -d | grep -E \
  'FATAL EXCEPTION|ANR in cn.xfangfang|am_crash|am_anr|ExoPlaybackException|PlayerError'
```

要求：

- 无 `FATAL EXCEPTION`
- 无 ANR
- 无 `ExoPlaybackException`
- 无应用级崩溃

允许出现的系统层提示：

```text
CCodec / MediaCodec mapFormat
```

但需要确认不是应用异常。

## 7. 发布说明

每次 Release 新建：

```text
docs/android-port/RELEASE-<VERSION>.md
```

至少包含：

- versionName / versionCode
- 包名
- 签名说明
- 主要更新
- 安装方式
- APK SHA-256
- 已知问题

## 8. 提交代码

使用中文提交信息：

```bash
cd /Users/mac/Documents/trae_projects/wiliwili/android
git add <改动文件>
git commit -m "发布：wiliwili Android <VERSION>"
```

推送前检查：

```bash
git status --short
git log --oneline -3
```

## 9. 推送 GitHub

优先尝试普通推送：

```bash
git push origin main
```

如果 GitHub `443` 不可用，使用当前环境已有的 Git Data API 推送脚本：

```bash
python3 /tmp/github_api_push2.py
```

推送后校验远端 tree：

```bash
git rev-parse HEAD^{tree}
```

再通过 GitHub API 查询远端 `main` 的 tree，确认与本地一致。

## 10. 创建 Release 和上传 APK

**只有用户明确要求发布时才执行本节。**

发布前确认：

- [ ] 版本号已更新
- [ ] `assembleDebug` 通过
- [ ] `testDebugUnitTest` 通过
- [ ] 实机冒烟通过
- [ ] 日志无崩溃
- [ ] SHA-256 已记录
- [ ] 发布说明已写

Release 参数：

```text
tag_name: v<VERSION>
name: wiliwili Android <VERSION>
asset: wiliwili-android-<VERSION>.apk
```

上传后验证：

- [ ] Release 为正式发布，不是 draft
- [ ] tag 正确
- [ ] APK asset 状态为 `uploaded`
- [ ] asset 大小与本地一致
- [ ] 下载地址可访问
- [ ] SHA-256 与本地一致

当前 0.10 Release 示例：

```text
https://github.com/1182980735/wiliwili_android/releases/tag/v0.10
https://github.com/1182980735/wiliwili_android/releases/download/v0.10/wiliwili-android-0.10.apk
```

## 11. 发布后检查

1. 在干净的下载目录重新下载 APK。
2. 校验 SHA-256。
3. 覆盖安装到已有版本。
4. 启动并抽查首页、详情播放、消息中心、设置。
5. 确认应用内“关于”显示正确版本号。

## 12. 回滚与热修复

### 发现严重问题时

1. 立即停止继续分发当前版本。
2. 在 GitHub 将 Release 标记为 draft 或补充“已知严重问题”说明。
3. 不覆盖旧 APK；保留现场。
4. 修复后提升 `versionCode` 和 `versionName`。
5. 走完整回归和日志门禁。
6. 发布新版本。

### 不要做的事

- 不要删除旧 tag 后复用同一个版本号。
- 不要覆盖已发布 APK。
- 不要修改 `debug.keystore`。
- 不要在未验证播放器的情况下发布。
- 不要在用户没明确要求时自动创建 Release。

## 13. 常用路径速查

```text
工程目录：/Users/mac/Documents/trae_projects/wiliwili/android
版本文件：android/app/build.gradle.kts
Debug APK：android/app/build/outputs/apk/debug/app-debug.apk
发布说明：android/docs/android-port/RELEASE-<VERSION>.md
回归报告：android/docs/android-port/REGRESSION-REPORT-2026-10-23.md
差异清单：android/docs/android-port/WILIWILI-ANDROID-DIFFERENCES.md
```

## 14. 合规提醒

本项目使用 Bilibili 相关接口和第三方客户端凭证复刻，仅用于个人自用和技术验证。不要公开分发、商业化或用于违反平台条款的用途。上线前如有任何公开传播需求，必须先做合规评估。
