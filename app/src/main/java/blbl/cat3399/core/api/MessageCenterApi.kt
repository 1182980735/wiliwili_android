package blbl.cat3399.core.api

import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MessageCenterItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeSec: Long,
    val avatarUrl: String?,
    val uri: String?,
    val unread: Boolean = false,
    val talkerId: Long? = null,
)

data class ChatVideoCard(
    val bvid: String,
    val aid: Long?,
    val title: String,
    val coverUrl: String?,
    val durationSec: Int,
    val viewCount: Long?,
    val danmakuCount: Long?,
    val description: String?,
)

data class ChatMessageItem(
    val key: String,
    val text: String,
    val outgoing: Boolean,
    val timeSec: Long,
    val video: ChatVideoCard? = null,
)

object MessageCenterApi {
    private const val TAG = "MessageCenterApi"
    private const val MSG_REFERER = "https://message.bilibili.com/"

    suspend fun loadChatSessions(): List<MessageCenterItem> {
        val url =
            "https://api.vc.bilibili.com/session_svr/v1/session_svr/get_sessions" +
                "?session_type=1&group_id=0&build=0&mobi_app=web"
        val data = requestData(url)
        val sessions = data.optJSONArray("session_list") ?: JSONArray()
        val mids = LinkedHashSet<Long>()
        for (i in 0 until sessions.length()) {
            val session = sessions.optJSONObject(i) ?: continue
            val mid = session.optLong("talker_id", 0L)
            if (mid > 0L) mids.add(mid)
        }
        val users = loadUserCards(mids)
        val out = ArrayList<MessageCenterItem>(sessions.length())
        for (i in 0 until sessions.length()) {
            val session = sessions.optJSONObject(i) ?: continue
            val mid = session.optLong("talker_id", 0L)
            val user = users[mid]
            val lastMessage = session.optJSONObject("last_msg")
            val content = parseChatContent(lastMessage)
            val timestamp = lastMessage?.optLong("timestamp", 0L) ?: session.optLong("session_ts", 0L)
            val groupName = session.optString("group_name").trim()
            val name =
                groupName.takeIf { it.isNotBlank() }
                    ?: user?.optString("name").orEmpty().trim().takeIf { it.isNotBlank() }
                    ?: user?.optString("uname").orEmpty().trim().takeIf { it.isNotBlank() }
                    ?: "用户 $mid"
            val avatar =
                user?.optString("face").orEmpty().trim().takeIf { it.isNotBlank() }
                    ?: user?.optString("avatar").orEmpty().trim().takeIf { it.isNotBlank() }
            out +=
                MessageCenterItem(
                    id = "chat:$mid",
                    title = name,
                    subtitle = content.text.ifBlank { "暂无消息" },
                    timeSec = normalizeSeconds(timestamp),
                    avatarUrl = avatar,
                    uri = content.uri,
                    unread = session.optInt("unread_count", 0) > 0,
                    talkerId = mid.takeIf { it > 0L },
                )
        }
        AppLog.d(TAG, "chat sessions=${out.size}")
        return out
    }

    suspend fun loadReplies(): List<MessageCenterItem> =
        loadFeed("https://api.bilibili.com/x/msgfeed/reply?platform=web&build=0&mobi_app=web", "reply")

    suspend fun loadMentions(): List<MessageCenterItem> =
        loadFeed("https://api.bilibili.com/x/msgfeed/at?platform=web&build=0&mobi_app=web", "at")

    suspend fun loadChatMessages(talkerId: Long): List<ChatMessageItem> {
        val safeTalkerId = talkerId.takeIf { it > 0L } ?: return emptyList()
        val url =
            "https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs" +
                "?talker_id=$safeTalkerId&session_type=1&size=50&sender_device_id=1&build=0&mobi_app=web"
        val data = requestData(url)
        val messages = data.optJSONArray("messages") ?: JSONArray()
        val out = ArrayList<ChatMessageItem>(messages.length())
        for (i in 0 until messages.length()) {
            val obj = messages.optJSONObject(i) ?: continue
            val senderUid = obj.optLong("sender_uid", 0L)
            val text = parseChatMessageText(obj)
            if (text.isBlank()) continue
            val key = obj.optString("msg_key", "").trim().ifBlank { "msg:$i" }
            out +=
                ChatMessageItem(
                    key = key,
                    text = text,
                    outgoing = senderUid != safeTalkerId,
                    timeSec = normalizeSeconds(obj.optLong("timestamp", 0L)),
                    video = if (obj.optInt("msg_type", 0) == 11) parseChatVideoCard(obj) else null,
                )
        }
        AppLog.d(TAG, "chat messages talker=$safeTalkerId count=${out.size}")
        return out
    }

    /**
     * 发送私信文本。
     *
     * B 站 web 端接口使用 application/x-www-form-urlencoded，消息正文本身是
     * `{"content":"..."}` 形式的 JSON 字符串。发送者身份与 CSRF 均复用当前 Cookie。
     */
    suspend fun sendChatMessage(talkerId: Long, text: String): ChatMessageItem {
        val safeTalkerId = talkerId.takeIf { it > 0L }
            ?: throw BiliApiException(apiCode = -400, apiMessage = "invalid_talker_id")
        val safeText = text.trim()
        if (safeText.isBlank()) throw BiliApiException(apiCode = -400, apiMessage = "消息内容不能为空")

        val senderUid =
            BiliClient.cookies.getCookieValue("DedeUserID")
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: throw BiliApiException(apiCode = -101, apiMessage = "账号未登录")
        val csrf =
            BiliClient.cookies.getCookieValue("bili_jct")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw BiliApiException(apiCode = -111, apiMessage = "missing_csrf")

        val url = "https://api.vc.bilibili.com/web_im/v1/web_im/send_msg"
        val timestamp = System.currentTimeMillis() / 1000L
        val content = JSONObject().put("content", safeText).toString()
        val form =
            linkedMapOf(
                "msg[msg_type]" to "1",
                "msg[content]" to content,
                "msg[sender_uid]" to senderUid.toString(),
                "msg[receiver_id]" to safeTalkerId.toString(),
                "msg[receiver_type]" to "1",
                "msg[msg_status]" to "0",
                "msg[timestamp]" to timestamp.toString(),
                "msg[dev_id]" to "0",
                "msg[new_face_version]" to "1",
                "csrf" to csrf,
            )
        val headers =
            mapOf(
                "Referer" to MSG_REFERER,
                "w_sender_uid" to senderUid.toString(),
                "w_receiver_id" to safeTalkerId.toString(),
            )
        val json = BiliClient.postFormJson(url, form = form, headers = headers)
        val code = json.optInt("code", 0)
        if (code != 0) {
            val message = json.optString("message", json.optString("msg", ""))
            throw BiliApiException(apiCode = code, apiMessage = message)
        }
        val data = json.optJSONObject("data") ?: JSONObject()
        val serverMsgKey = data.optString("msg_key", "").trim()
        AppLog.d(TAG, "sent chat message talker=$safeTalkerId")
        return ChatMessageItem(
            key = serverMsgKey.ifBlank { "sent:${System.currentTimeMillis()}" },
            text = safeText,
            outgoing = true,
            timeSec = timestamp,
        )
    }

    suspend fun loadLikes(): List<MessageCenterItem> {
        val data = requestData("https://api.bilibili.com/x/msgfeed/like?platform=web&build=0&mobi_app=web")
        val items = data.optJSONObject("total")?.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<MessageCenterItem>(items.length())
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val users = obj.optJSONArray("users") ?: JSONArray()
            val first = users.optJSONObject(0)
            val userName = first?.optString("nickname").orEmpty().trim().ifBlank { "有人" }
            val title = if (users.length() > 1) "$userName 等 ${users.length()} 人" else userName
            val item = obj.optJSONObject("item") ?: JSONObject()
            val videoTitle = item.optString("title").trim()
            val detail =
                buildString {
                    append("赞了你的内容")
                    if (videoTitle.isNotBlank()) append(" · ").append(videoTitle)
                }
            out +=
                MessageCenterItem(
                    id = "like:${obj.optLong("id", i.toLong())}",
                    title = title,
                    subtitle = detail,
                    timeSec = normalizeSeconds(obj.optLong("like_time", 0L)),
                    avatarUrl = first?.optString("avatar").orEmpty().trim().takeIf { it.isNotBlank() },
                    uri = item.optString("uri").trim().takeIf { it.isNotBlank() },
                )
        }
        AppLog.d(TAG, "like items=${out.size}")
        return out
    }

    private suspend fun loadFeed(url: String, kind: String): List<MessageCenterItem> {
        val data = requestData(url)
        val items = data.optJSONArray("items") ?: JSONArray()
        val out = ArrayList<MessageCenterItem>(items.length())
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val user = obj.optJSONObject("user") ?: JSONObject()
            val item = obj.optJSONObject("item") ?: JSONObject()
            val name = user.optString("nickname").trim().ifBlank { "有人" }
            val detailTitle = item.optString("detail_title").trim()
            val videoTitle = detailTitle.ifBlank { item.optString("title").trim() }
            val message =
                item.optString("target_reply_content").trim()
                    .ifBlank { item.optString("source_content").trim() }
                    .ifBlank { item.optString("desc").trim() }
                    .ifBlank { item.optString("message").trim() }
            val action = if (kind == "at") "提到了我" else "回复了我的评论"
            val subtitle =
                buildString {
                    append(action)
                    if (videoTitle.isNotBlank()) append(" · ").append(videoTitle)
                    if (message.isNotBlank()) append("\n").append(message)
                }
            val timeSec =
                if (kind == "at") obj.optLong("at_time", 0L) else obj.optLong("reply_time", 0L)
            out +=
                MessageCenterItem(
                    id = "$kind:${obj.optLong("id", i.toLong())}",
                    title = name,
                    subtitle = subtitle,
                    timeSec = normalizeSeconds(timeSec),
                    avatarUrl = user.optString("avatar").trim().takeIf { it.isNotBlank() },
                    uri = item.optString("uri").trim().takeIf { it.isNotBlank() },
                )
        }
        AppLog.d(TAG, "$kind items=${out.size}")
        return out
    }

    private suspend fun requestData(url: String): JSONObject {
        val json = BiliClient.getJson(url, headers = mapOf("Referer" to MSG_REFERER))
        val code = json.optInt("code", 0)
        if (code != 0) {
            val message = json.optString("message", json.optString("msg", ""))
            throw BiliApiException(apiCode = code, apiMessage = message)
        }
        return json.optJSONObject("data") ?: JSONObject()
    }

    private suspend fun loadUserCards(mids: Set<Long>): Map<Long, JSONObject> {
        if (mids.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            runCatching {
                val uids = mids.take(50).joinToString(",")
                val json =
                    BiliClient.getJson(
                        "https://api.vc.bilibili.com/account/v1/user/cards?uids=$uids",
                        headers = mapOf("Referer" to MSG_REFERER),
                    )
                if (json.optInt("code", -1) != 0) return@runCatching emptyMap()
                val arr = json.optJSONArray("data") ?: JSONArray()
                val out = HashMap<Long, JSONObject>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val mid = obj.optLong("mid", 0L)
                    if (mid > 0L) out[mid] = obj
                }
                out
            }.getOrElse {
                AppLog.w(TAG, "load user cards failed", it)
                emptyMap()
            }
        }
    }

    private fun parseChatVideoCard(obj: JSONObject): ChatVideoCard? {
        val raw = obj.optString("content", "").trim()
        if (raw.isBlank()) return null
        val content = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val bvid = content.optString("bvid", "").trim()
        val aid = content.optLong("rid", 0L).takeIf { it > 0L }
        if (bvid.isBlank() && aid == null) return null
        val cover = content.optString("cover", "").trim().takeIf { it.isNotBlank() }
        return ChatVideoCard(
            bvid = bvid,
            aid = aid,
            title = content.optString("title", "").trim().ifBlank { "视频消息" },
            coverUrl = cover,
            durationSec = content.optInt("times", 0).coerceAtLeast(0),
            viewCount = content.optLong("view", 0L).takeIf { it > 0L },
            danmakuCount = content.optLong("danmaku", 0L).takeIf { it > 0L },
            description = content.optString("desc", "").trim().takeIf { it.isNotBlank() && it != "-" },
        )
    }

    private fun parseChatMessageText(obj: JSONObject): String {
        val type = obj.optInt("msg_type", 0)
        val raw = obj.optString("content", "").trim()
        if (raw.isBlank()) return ""
        if (type == 2) return "[图片]"
        if (type == 7) return "[撤回消息]"
        if (type != 1) {
            val video = runCatching { JSONObject(raw) }.getOrNull()?.optString("title").orEmpty()
            return video.takeIf { it.isNotBlank() } ?: "[消息]"
        }
        return runCatching { JSONObject(raw).optString("content", "").trim() }
            .getOrElse { raw }
    }

    private data class ChatContent(val text: String, val uri: String?)

    private fun parseChatContent(lastMessage: JSONObject?): ChatContent {
        if (lastMessage == null) return ChatContent("", null)
        val raw = lastMessage.optString("content").trim()
        if (raw.isBlank()) return ChatContent("", null)
        val contentObj = runCatching { JSONObject(raw) }.getOrNull()
        if (contentObj == null) return ChatContent(raw, null)
        val bvid = contentObj.optString("bvid").trim()
        val title = contentObj.optString("title").trim()
        val text = contentObj.optString("content").trim().ifBlank { contentObj.optString("desc").trim() }
        val uri = bvid.takeIf { it.isNotBlank() }?.let { "https://www.bilibili.com/video/$it" }
        val summary =
            when {
                title.isNotBlank() -> "[视频] $title"
                text.isNotBlank() -> text
                else -> raw
            }
        return ChatContent(summary, uri)
    }

    private fun normalizeSeconds(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value > 10_000_000_000L) value / 1_000_000L else value
    }
}
