# Android 移植阶段四十二：首页直播子 Tab 关注置首

> 个人自用、仅技术验证，不对外分发。

## 修改

首页直播页子 Tab 顺序由：

```text
推荐 / 关注 / 分区...
```

调整为：

```text
关注 / 推荐 / 分区...
```

- `LiveTabs.all` 中把 `FOLLOWING` 放到 `RECOMMEND` 前。
- 默认第一个子 Tab 现在为“关注”。
- 未登录时仍会自动过滤“关注”，回退为“推荐”。
- 用户自定义可见 Tab 仍按新顺序展示。
- 不改变分区 Tab 和直播内容加载逻辑。

## 验证

- 登录状态下进入首页“直播”Tab，第一个子 Tab 显示“关注”并默认选中。
- 第二个子 Tab 为“推荐”。
- `assembleDebug` 和 `testDebugUnitTest` 通过。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
