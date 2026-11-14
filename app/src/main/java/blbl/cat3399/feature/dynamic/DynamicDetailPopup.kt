package blbl.cat3399.feature.dynamic

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class DynamicCommentUi(
    val rpid: Long,
    val oid: Long,
    val type: Int,
    val name: String,
    val message: String,
    val like: Int,
    val replyCount: Int,
)

internal fun showDynamicDetailPopup(
    context: Context,
    article: BiliApi.DynamicArticleCard,
    scope: androidx.lifecycle.LifecycleCoroutineScope,
) {
    var loadJob: kotlinx.coroutines.Job? = null
    var detail: BiliApi.DynamicDetail? = null
    var commentItems: List<DynamicCommentUi>? = null
    var commentError: String? = null
    var rerender: (() -> Unit)? = null
    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    AppPopup.custom(
        context = context,
        title = null,
        cancelable = true,
        actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
        preferredActionRole = PopupActionRole.POSITIVE,
        autoFocus = false,
        onDismiss = { loadJob?.cancel() },
    ) { dialogContext ->
        val root = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val toolbar = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val content = LinearLayout(dialogContext).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(dialogContext).apply { addView(content) }
        val loading = ProgressBar(dialogContext).apply { isIndeterminate = true }
        fun action(text: String, onClick: () -> Unit): TextView = TextView(dialogContext).apply {
            this.text = text
            textSize = 13f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        fun run(success: String, block: suspend () -> Unit) {
            scope.launch {
                try { block(); AppToast.show(dialogContext, success) }
                catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    val msg = (t as? BiliApiException)?.apiMessage?.takeIf { it.isNotBlank() } ?: t.message.orEmpty().ifBlank { "操作失败" }
                    AppToast.showLong(dialogContext, msg)
                }
            }
        }
        var liked = false
        toolbar.addView(action("点赞") { run(if (liked) "已取消点赞" else "已点赞") { BiliApi.dynamicLike(article.id, !liked); liked = !liked } })
        toolbar.addView(action("评论") {
            AppPopup.input(
                context = dialogContext,
                title = "评论动态",
                initial = "",
                hint = "输入评论内容",
                inputType = InputType.TYPE_CLASS_TEXT,
                onPositive = { text ->
                    if (text.isNotBlank()) run("评论已发送") { BiliApi.dynamicComment(article.id, text) }
                },
            )
        })
        toolbar.addView(action("转发") {
            AppPopup.input(
                context = dialogContext,
                title = "转发动态",
                initial = "",
                hint = "转发理由（可留空）",
                inputType = InputType.TYPE_CLASS_TEXT,
                onPositive = { text -> run("已转发") { BiliApi.dynamicRepost(article.id, text) } },
            )
        })
        toolbar.addView(action("删除") {
            run("动态已删除") { BiliApi.dynamicDelete(article.id) }
        })
        root.addView(toolbar)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)))
        root.addView(loading)
        rerender = label@{
            val d = detail ?: return@label
            content.removeAllViews()
            content.addView(TextView(dialogContext).apply {
                text = listOfNotNull(d.authorName.takeIf { it.isNotBlank() }, d.pubTime).joinToString(" · ")
                setTextColor(Color.GRAY); textSize = 13f
            })
            if (d.title.isNotBlank() && d.title != "动态") content.addView(TextView(dialogContext).apply {
                text = d.title; setTextColor(Color.DKGRAY); textSize = 19f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
            d.text?.takeIf { it.isNotBlank() && it != d.title }?.let {
                content.addView(TextView(dialogContext).apply {
                    text = it; textSize = 15f; setTextColor(Color.DKGRAY)
                    setTextIsSelectable(true); setPadding(0, dp(8), 0, 0)
                })
            }
            d.imageUrls.forEach { url ->
                content.addView(ImageView(dialogContext).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(0, dp(8), 0, 0)
                    ImageLoader.loadInto(this, ImageUrl.original(url))
                })
            }
            val stats = listOfNotNull(d.likeCount?.let { "点赞 $it" }, d.commentCount?.let { "评论 $it" }).joinToString(" · ")
            if (stats.isNotBlank()) content.addView(TextView(dialogContext).apply {
                text = stats; setTextColor(Color.GRAY); textSize = 12f; setPadding(0, dp(10), 0, 0)
            })
            content.addView(TextView(dialogContext).apply {
                text = "评论"; setTextColor(Color.DKGRAY); textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(4))
            })
            val items = commentItems
            if (items == null) {
                content.addView(TextView(dialogContext).apply {
                    text = commentError ?: "评论加载中…"; setTextColor(Color.GRAY); textSize = 13f
                })
            } else if (items.isEmpty()) {
                content.addView(TextView(dialogContext).apply {
                    text = "暂无评论"; setTextColor(Color.GRAY); textSize = 13f
                })
            } else {
                items.forEach { comment ->
                    val row = LinearLayout(dialogContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                        setPadding(0, dp(8), 0, 0)
                    }
                    row.addView(TextView(dialogContext).apply {
                        text = "${comment.name}：${comment.message}" + if (comment.like > 0) "  ·  ${comment.like}赞" else ""
                        setTextColor(Color.DKGRAY); textSize = 14f
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (comment.replyCount > 0) {
                        row.addView(action("展开${comment.replyCount}") {
                            showDynamicReplyThread(dialogContext, comment, scope)
                        })
                    }
                    row.addView(action("赞") {
                        run("已点赞评论") { BiliApi.dynamicCommentReaction(comment.type, comment.oid, comment.rpid, true) }
                    })
                    row.addView(action("回复") {
                        AppPopup.input(
                            context = dialogContext,
                            title = "回复 ${comment.name}",
                            initial = "",
                            hint = "输入回复内容",
                            onPositive = { text ->
                                if (text.isNotBlank()) run("回复已发送") {
                                    BiliApi.dynamicCommentReply(comment.type, comment.oid, text, comment.rpid, comment.rpid)
                                }
                            },
                        )
                    })
                    content.addView(row)
                }
            }
        }
        loadJob = scope.launch {
            try {
                detail = BiliApi.dynamicDetail(article.id)
                loading.visibility = ProgressBar.GONE
                rerender?.invoke()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("Dynamic", "load user dynamic detail failed id=${article.id}", t)
                loading.visibility = ProgressBar.GONE
                content.addView(TextView(dialogContext).apply {
                    text = article.summary ?: article.title
                    textSize = 15f
                })
            }
        }
        scope.launch {
            try {
                var waitLoops = 0
                while (detail == null && waitLoops < 30) {
                    delay(100)
                    waitLoops++
                }
                val aid =
                    article.aid?.takeIf { it > 0L }
                        ?: detail?.bvid?.takeIf { it.isNotBlank() }?.let {
                            runCatching { BiliApi.videoDetail(it).aid }.getOrNull()?.takeIf { v -> v > 0L }
                        }
                        ?: article.bvid?.takeIf { it.isNotBlank() }?.let {
                            runCatching { BiliApi.videoDetail(it).aid }.getOrNull()?.takeIf { v -> v > 0L }
                        }
                val directOid = article.commentOid?.takeIf { it > 0L }
                val directType = article.commentType?.takeIf { it > 0 }
                val resolvedOid = directOid ?: aid ?: article.id
                val resolvedType = directType ?: if (aid != null) 1 else 17
                val data = BiliApi.commentPage(type = resolvedType, oid = resolvedOid, sort = 1, pn = 1, ps = 10, noHot = 1)
                val replies = data.optJSONArray("replies") ?: org.json.JSONArray()
                commentItems =
                    buildList {
                        for (i in 0 until replies.length()) {
                            val o = replies.optJSONObject(i) ?: continue
                            val member = o.optJSONObject("member") ?: org.json.JSONObject()
                            val contentObj = o.optJSONObject("content") ?: org.json.JSONObject()
                            val name = member.optString("uname", "").trim().ifBlank { "用户" }
                            val message = contentObj.optString("message", "").trim()
                            val rpid = o.optLong("rpid", 0L)
                            if (message.isBlank() || rpid <= 0L) continue
                            add(
                                DynamicCommentUi(
                                    rpid = rpid,
                                    oid = resolvedOid,
                                    type = resolvedType,
                                    name = name,
                                    message = message,
                                    like = o.optInt("like", 0),
                                    replyCount = o.optInt("count", 0).coerceAtLeast(0),
                                ),
                            )
                        }
                    }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                commentError = "评论加载失败：${(t as? BiliApiException)?.apiMessage ?: t.message.orEmpty()}"
            }
            rerender?.invoke()
        }
        root
    }
}


internal fun showDynamicReplyThread(
    context: Context,
    comment: DynamicCommentUi,
    scope: androidx.lifecycle.LifecycleCoroutineScope,
) {
    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    var rerender: (() -> Unit)? = null
    var items: List<String>? = null
    var error: String? = null
    AppPopup.custom(
        context = context,
        title = "回复 @${comment.name}",
        cancelable = true,
        actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
        preferredActionRole = PopupActionRole.POSITIVE,
        autoFocus = false,
    ) { dialogContext ->
        val root = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val content = LinearLayout(dialogContext).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(dialogContext).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))
        rerender = {
            content.removeAllViews()
            content.addView(TextView(dialogContext).apply {
                text = "${comment.name}：${comment.message}"
                textSize = 15f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, 0, dp(10))
            })
            val lines = items
            if (lines == null) {
                content.addView(TextView(dialogContext).apply {
                    text = error ?: "回复加载中…"; textSize = 13f; setTextColor(Color.GRAY)
                })
            } else if (lines.isEmpty()) {
                content.addView(TextView(dialogContext).apply {
                    text = "暂无回复"; textSize = 13f; setTextColor(Color.GRAY)
                })
            } else {
                lines.forEach { line ->
                    content.addView(TextView(dialogContext).apply {
                        text = line; textSize = 14f; setTextColor(Color.DKGRAY)
                        setPadding(dp(12), dp(7), 0, 0)
                    })
                }
            }
        }
        scope.launch {
            try {
                val out = ArrayList<String>()
                var pn = 1
                while (pn <= 20) {
                    val data = BiliApi.commentRepliesPage(
                        type = comment.type,
                        oid = comment.oid,
                        rootRpid = comment.rpid,
                        pn = pn,
                        ps = 20,
                    )
                    val replies = data.optJSONArray("replies") ?: org.json.JSONArray()
                    for (i in 0 until replies.length()) {
                        val o = replies.optJSONObject(i) ?: continue
                        val member = o.optJSONObject("member") ?: org.json.JSONObject()
                        val textObj = o.optJSONObject("content") ?: org.json.JSONObject()
                        val name = member.optString("uname", "").trim().ifBlank { "用户" }
                        val message = textObj.optString("message", "").trim()
                        if (message.isNotBlank()) out.add("$name：$message")
                    }
                    val pageObj = data.optJSONObject("page") ?: org.json.JSONObject()
                    val count = pageObj.optInt("count", 0)
                    if (replies.length() == 0 || pn * 20 >= count) break
                    pn++
                }
                items = out
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                error = "回复加载失败：${(t as? BiliApiException)?.apiMessage ?: t.message.orEmpty()}"
            }
            rerender?.invoke()
        }
        root
    }
}
