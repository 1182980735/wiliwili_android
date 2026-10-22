# Android 移植阶段三十一：聊天历史、富消息、已读同步、我的动态数与热搜上限

> 个人自用、仅技术验证，不对外分发。

## 1. 我的页“动态”数量

- 修正统计字段来源。
- 使用 `x/dynamic/feed/space/dyn_num?uid_str=<mid>` 获取 `dyn_num`。
- 与关注数、粉丝数、硬币数并行加载。
- 实机显示用户动态数 1832，不再显示 0。

## 2. 聊天历史分页

- 首次加载最近 30 条。
- 滚动到聊天列表顶部时加载更早消息。
- 使用 `fetch_session_msgs` 的 `begin_seqno` / `end_seqno`。
- 按 `msg_seqno` / `msg_key` 去重。
- 插入旧消息后恢复原来的滚动位置。
- 增加“加载更早消息…”提示。

## 3. 聊天图片与表情

- `msg_type=2` 解析 `content.url/width/height`。
- 新增图片消息卡片并加载真实缩略图。
- 解析接口 `data.e_infos` 的表情映射。
- 文本中的表情 token 使用 `Spannable` + `ImageSpan` 渲染真实表情图片。
- 表情图片加载失败时保留原 token。

## 4. 聊天已读同步

- 打开会话取得最新 `max_seqno` 后调用：
  `session_svr/v1/session_svr/update_ack`
- 表单包含 `talker_id`、`session_type=1`、`ack_seqno`、`csrf`。
- 成功后同步清除本地缓存和会话列表的未读标记。
- 失败时提示并保留未读状态。

## 5. 热门搜索数量

- 热门搜索请求上限从 12 调整到 50。
- 搜索页仍为三列布局，热门列表可滚动查看更多。
- 排名、新/热/独家标记保持原有逻辑。

## 验证

- 我的页动态数量实机显示 1832。
- 热门搜索页成功加载更多条目。
- 打开未读会话后返回，对应聊天列表红点已清除。
- 聊天输入框、发送按钮、历史消息和视频卡片保持可用。
- 无 `FATAL EXCEPTION`。
- `assembleDebug` 和 `testDebugUnitTest` 通过。

## 构建

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
