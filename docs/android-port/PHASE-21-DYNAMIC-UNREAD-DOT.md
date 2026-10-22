# Android 移植阶段二十一：动态页最近更新红点

> 个人自用、仅技术验证，不对外分发。

## 目标

对齐 wiliwili 动态页左侧 UP 列表的最近更新红点提示。

## 实现

- `dynamicFollowingRecentUpdateDotEnabled` 默认值从 `false` 改为 `true`
- 左侧 UP 列表对最近有更新的 UP 显示红点
- 保持最近更新优先排序
- 点击有红点的 UP 后消费最近更新状态并隐藏红点
- 原有设置项仍保留，用户手动关闭后仍可关闭红点

## 验证

- 动态页左侧多个 UP 显示红点
- 红点与最近更新排序同时生效
- 红点不再依赖用户先手动开启设置
- 无 `FATAL EXCEPTION`

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

均通过。
