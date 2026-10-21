# Android 移植阶段七：全部动态按最近更新 UP 排序

> 个人自用、仅技术验证，不对外分发。

## 需求

进入“动态”页时，“全部动态”左侧 UP 列表需要按最近更新视频的 UP 主排序，
最近有更新的 UP 放在最上面。

## 实现

- `BiliApi.dynamicRecentUpdateUpMids()` 改为返回有序 `List<Long>`，
  保留 B 站 portal 接口 `up_list.items` 的返回顺序。
- `DynamicFragment` 新增 `recentUpdateOrderMids`，用于按接口顺序排序。
- 首次加载关注列表时，无论“最近更新红点”设置是否开启，都请求最近更新顺序。
- `orderedFollowings()` 按 `recentUpdateOrderMids` 的索引排序：
  1. 最近有更新的 UP
  2. 未更新 UP
- 每次重新进入动态页时，在 `onResume` 中刷新一次更新顺序并重新渲染左栏。
- “最近更新红点”仍由原设置控制，排序不依赖该设置。

## 验证

日志实测：

```text
dynamicRecentUpdateUpMids upList=50 updated=26
following order refreshed updated=26
```

- 首次进入动态页正常加载并按更新顺序显示。
- 切到首页再返回动态页，会再次拉取更新顺序并重新排序。
- 未出现 `FATAL EXCEPTION`。
- `./gradlew assembleDebug` 与 `./gradlew testDebugUnitTest` 通过。
