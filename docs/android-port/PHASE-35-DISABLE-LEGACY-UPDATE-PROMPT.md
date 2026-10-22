# Android 移植阶段三十五：关闭旧 blbl 更新提示

> 个人自用、仅技术验证，不对外分发。

## 问题现象

首次打开应用时弹出“发现新版本 0.1.31”。

## 原因

项目内置了来自参考项目 `1182980735/blbl` 的自动更新检查：

- 更新日志地址：`1182980735/blbl` GitHub Releases 的 `CHANGELOG.md`
- 当前应用版本：`0.1.0-wiliwili-poc`
- 远端旧项目版本：`0.1.31`
- `ApkUpdater.isRemoteNewer()` 判定远端版本更高
- `autoUpdateCheckEnabled` 默认值原先为 `true`
- `MainActivity` 启动后调用 `maybeStartAutoUpdateCheck()`

因此即使本项目没有面向 `wiliwili_android` 的发布更新源，也会弹出旧 blbl 项目的更新提示。

## 修改

1. `autoUpdateCheckEnabled` 默认值改为 `false`。
2. 移除 `MainActivity.onCreate()` 中的自动更新检查调用。
3. 移除首次免责声明关闭后的自动更新检查调用。
4. 从设置“关于应用”中移除：
   - 自动检查更新
   - 检查更新
5. 保留更新相关底层代码，但当前没有任何启动/设置入口可触发，后续如需更新功能应改为本项目 `wiliwili_android` 的正确发布源。

## 验证

- `assembleDebug` 通过
- `testDebugUnitTest` 通过
- 启动流程不再调用自动更新检查
- 设置页不再显示更新入口

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
