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

data class ChatImage(
    val url: String,
    val width: Int,
    val height: Int,
)

data class ChatEmote(
    val text: String,
    val url: String,
    val size: Int = 1,
)

data class ChatMessageItem(
    val key: String,
    val text: String,
    val outgoing: Boolean,
    val timeSec: Long,
    val seqno: Long = 0L,
    val video: ChatVideoCard? = null,
    val image: ChatImage? = null,
)

data class ChatMessagePage(
    val messages: List<ChatMessageItem>,
    val emotes: Map<String, ChatEmote> = emptyMap(),
    val hasMore: Boolean = false,
    val maxSeqno: Long = 0L,
    val minSeqno: Long = 0L,
)

object MessageCenterApi {
    private const val TAG = "MessageCenterApi"
    private const val MSG_REFERER = "https://message.bilibili.com/"
    private const val CHAT_PAGE_SIZE = 30

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

    /**
     * 读取聊天记录。首次不带 seqno，返回最近一页；继续向上翻页时同时传
     * begin_seqno（上一页 max_seqno）与 end_seqno（上一页 min_seqno）。
     */
    suspend fun loadChatMessages(
        talkerId: Long,
        beginSeqno: Long? = null,
        endSeqno: Long? = null,
    ): ChatMessagePage {
        val safeTalkerId = talkerId.takeIf { it > 0L }
            ?: throw BiliApiException(apiCode = -400, apiMessage = "invalid_talker_id")
        val url =
            buildString {
                append("https://api.vc.bilibili.com/svr_sync/v1/svr_sync/fetch_session_msgs")
                append("?talker_id=").append(safeTalkerId)
                append("&session_type=1&size=").append(CHAT_PAGE_SIZE)
                append("&sender_device_id=1&build=0&mobi_app=web")
                if (beginSeqno != null && beginSeqno > 0L) append("&begin_seqno=").append(beginSeqno)
                if (endSeqno != null && endSeqno > 0L) append("&end_seqno=").append(endSeqno)
            }
        val data = requestData(url)
        val messages = data.optJSONArray("messages") ?: JSONArray()
        val out = ArrayList<ChatMessageItem>(messages.length())
        for (i in 0 until messages.length()) {
            val obj = messages.optJSONObject(i) ?: continue
            out += parseChatMessage(obj, safeTalkerId, i) ?: continue
        }
        out.sortBy { it.seqno }
        val emotes = parseChatEmotes(data)
        val maxSeqno =
            data.optLong("max_seqno", 0L)
                .takeIf { it > 0L }
                ?: out.maxOfOrNull { it.seqno }
                ?: 0L
        val minSeqno =
            data.optLong("min_seqno", 0L)
                .takeIf { it > 0L }
                ?: out.minOfOrNull { it.seqno }
                ?: 0L
        AppLog.d(
            TAG,
            "chat messages talker=$safeTalkerId count=${out.size} begin=$beginSeqno end=$endSeqno " +
                "max=$maxSeqno min=$minSeqno more=${data.optInt("has_more", 0)}",
        )
        return ChatMessagePage(
            messages = out,
            emotes = emotes,
            hasMore = data.optInt("has_more", 0) != 0,
            maxSeqno = maxSeqno,
            minSeqno = minSeqno,
        )
    }

    /** 回写会话已读位置；接口与消息列表同一套 Cookie/CSRF。 */
    suspend fun markChatRead(talkerId: Long, ackSeqno: Long) {
        val safeTalkerId = talkerId.takeIf { it > 0L }
            ?: throw BiliApiException(apiCode = -400, apiMessage = "invalid_talker_id")
        val safeAckSeqno = ackSeqno.takeIf { it > 0L }
            ?: throw BiliApiException(apiCode = -400, apiMessage = "invalid_ack_seqno")
        val csrf =
            BiliClient.cookies.getCookieValue("bili_jct")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: throw BiliApiException(apiCode = -111, apiMessage = "missing_csrf")
        val url = "https://api.vc.bilibili.com/session_svr/v1/session_svr/update_ack"
        val form =
            linkedMapOf(
                "talker_id" to safeTalkerId.toString(),
                "session_type" to "1",
                "ack_seqno" to safeAckSeqno.toString(),
                "csrf" to csrf,
                "build" to "0",
                "mobi_app" to "web",
            )
        val json =
            BiliClient.postFormJson(
                url,
                form = form,
                headers = mapOf("Referer" to MSG_REFERER),
            )
        val code = json.optInt("code", 0)
        if (code != 0) {
            val message = json.optString("message", json.optString("msg", ""))
            throw BiliApiException(apiCode = code, apiMessage = message)
        }
        AppLog.d(TAG, "chat ack talker=$safeTalkerId seqno=$safeAckSeqno")
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

    private fun parseChatMessage(obj: JSONObject, talkerId: Long, index: Int): ChatMessageItem? {
        val type = obj.optInt("msg_type", 0)
        val raw = obj.optString("content", "").trim()
        val content = runCatching { JSONObject(raw) }.getOrNull()
        val text = parseChatMessageText(obj)
        val video = if (type == 11) parseChatVideoCard(obj) else null
        val image = if (type == 2) parseChatImage(content) else null
        val fallbackText = when {
            text.isNotBlank() -> text
            image != null -> ""
            else -> "[消息]"
        }
        if (fallbackText.isBlank() && image == null && video == null) return null
        val seqno = obj.optLong("msg_seqno", 0L)
        val key =
            obj.optString("msg_key", "").trim()
                .ifBlank { if (seqno > 0L) "msg:$seqno" else "msg:$index" }
        return ChatMessageItem(
            key = key,
            text = fallbackText,
            outgoing = obj.optLong("sender_uid", 0L) != talkerId,
            timeSec = normalizeSeconds(obj.optLong("timestamp", 0L)),
            seqno = seqno,
            video = video,
            image = image,
        )
    }

    private fun parseChatImage(content: JSONObject?): ChatImage? {
        val obj = content ?: return null
        val url = obj.optString("url", "").trim()
        if (url.isBlank()) return null
        return ChatImage(
            url = url,
            width = obj.optInt("width", 0).coerceAtLeast(0),
            height = obj.optInt("height", 0).coerceAtLeast(0),
        )
    }

    private fun parseChatEmotes(data: JSONObject): Map<String, ChatEmote> {
        val arr = data.optJSONArray("e_infos") ?: return emptyMap()
        val out = LinkedHashMap<String, ChatEmote>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val text = obj.optString("text", "").trim()
            val url = obj.optString("url", "").trim()
            if (text.isBlank() || url.isBlank()) continue
            out[text] = ChatEmote(text = text, url = url, size = obj.optInt("size", 1))
        }
        return out
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
