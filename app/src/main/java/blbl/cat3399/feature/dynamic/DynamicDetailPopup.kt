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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class DynamicCommentUi(
    val rpid: Long,
    val oid: Long,
    val type: Int,
    val name: String,
    val message: String,
    val like: Int,
    val replyCount: Int,
    val timeText: String? = null,
    val location: String? = null,
    val avatarUrl: String? = null,
    val pictures: List<String> = emptyList(),
)

private data class DynamicReplyUi(
    val name: String,
    val message: String,
    val avatarUrl: String? = null,
    val pictures: List<String> = emptyList(),
)

internal fun showDynamicDetailPopup(
    context: Context,
    article: BiliApi.DynamicArticleCard,
    scope: androidx.lifecycle.LifecycleCoroutineScope,
) {
    var loadJob: Job? = null
    var detail: BiliApi.DynamicDetail? = null
    val comments = ArrayList<DynamicCommentUi>()
    val replies = HashMap<Long, List<DynamicReplyUi>>()
    val replyLoading = HashSet<Long>()
    var commentPage = 1
    var commentsStarted = false
    var commentsLoading = false
    var commentsEnd = false
    var commentError: String? = null
    var resolvedOid = article.commentOid ?: article.aid ?: article.id
    var resolvedType = article.commentType ?: if (article.aid != null) 1 else 17
    var rerender: (() -> Unit)? = null
    var scrollView: ScrollView? = null
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
            setPadding(dp(18), dp(10), dp(18), dp(8))
        }
        val toolbar = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val content = LinearLayout(dialogContext).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(dialogContext).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(content)
        }
        scrollView = scroll
        val loading = ProgressBar(dialogContext).apply { isIndeterminate = true }

        fun action(text: String, onClick: () -> Unit): TextView =
            TextView(dialogContext).apply {
                this.text = text
                textSize = 13f
                setPadding(dp(9), dp(8), dp(9), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }

        fun run(success: String, block: suspend () -> Unit) {
            scope.launch {
                try {
                    block()
                    AppToast.show(dialogContext, success)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    val msg = (t as? BiliApiException)?.apiMessage?.takeIf { it.isNotBlank() }
                        ?: t.message.orEmpty().ifBlank { "操作失败" }
                    AppToast.showLong(dialogContext, msg)
                }
            }
        }

        fun currentTarget(): Pair<Long, Int> {
            val d = detail
            val bvid = d?.bvid?.takeIf { it.isNotBlank() } ?: article.bvid?.takeIf { it.isNotBlank() }
            val aid = article.aid?.takeIf { it > 0L }
            return if (aid != null) {
                aid to 1
            } else if (bvid != null) {
                resolvedOid to resolvedType
            } else {
                resolvedOid to resolvedType
            }
        }

        fun parseRootComments(data: org.json.JSONObject): List<DynamicCommentUi> {
            val arr = data.optJSONArray("replies") ?: org.json.JSONArray()
            val target = currentTarget()
            val out = ArrayList<DynamicCommentUi>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val member = o.optJSONObject("member") ?: org.json.JSONObject()
                val c = o.optJSONObject("content") ?: org.json.JSONObject()
                val rpid = o.optLong("rpid", 0L)
                val message = c.optString("message", "").trim()
                if (rpid <= 0L || message.isBlank()) continue
                out.add(
                    DynamicCommentUi(
                        rpid = rpid,
                        oid = target.first,
                        type = target.second,
                        name = member.optString("uname", "").trim().ifBlank { "用户" },
                        message = message,
                        like = o.optInt("like", 0),
                        replyCount = o.optInt("count", 0).coerceAtLeast(0),
                        timeText = o.optLong("ctime", 0L).takeIf { it > 0L }?.let { formatRelativeTime(it) },
                        location = o.optJSONObject("reply_control")?.optString("location", "")
                            ?.trim()?.takeIf { it.isNotBlank() },
                        avatarUrl = member.optString("avatar", "").trim().takeIf { it.isNotBlank() },
                        pictures = parseCommentPictures(c),
                    ),
                )
            }
            return out
        }

        fun loadComments() {
            if (commentsLoading || commentsEnd || detail == null) return
            commentsLoading = true
            commentError = null
            scope.launch {
                try {
                    val data = BiliApi.commentPage(
                        type = resolvedType,
                        oid = resolvedOid,
                        sort = 1,
                        pn = commentPage,
                        ps = 20,
                        noHot = 1,
                    )
                    val pageItems = parseRootComments(data)
                    comments.addAll(pageItems)
                    commentsStarted = true
                    commentPage++
                    val page = data.optJSONObject("page") ?: org.json.JSONObject()
                    val count = page.optInt("count", 0)
                    commentsEnd = pageItems.size < 20 || (count > 0 && comments.size >= count)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    commentError = (t as? BiliApiException)?.apiMessage ?: t.message.orEmpty()
                } finally {
                    commentsLoading = false
                }
                rerender?.invoke()
            }
        }

        fun loadRepliesFor(comment: DynamicCommentUi) {
            if (replyLoading.contains(comment.rpid) || replies.containsKey(comment.rpid)) return
            replyLoading.add(comment.rpid)
            scope.launch {
                try {
                    val out = ArrayList<DynamicReplyUi>()
                    var pn = 1
                    while (pn <= 20) {
                        val data = BiliApi.commentRepliesPage(
                            type = comment.type,
                            oid = comment.oid,
                            rootRpid = comment.rpid,
                            pn = pn,
                            ps = 20,
                        )
                        val arr = data.optJSONArray("replies") ?: org.json.JSONArray()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val member = o.optJSONObject("member") ?: org.json.JSONObject()
                            val c = o.optJSONObject("content") ?: org.json.JSONObject()
                            val msg = c.optString("message", "").trim()
                            if (msg.isNotBlank()) {
                                out.add(
                                    DynamicReplyUi(
                                        name = member.optString("uname", "").trim().ifBlank { "用户" },
                                        message = msg,
                                        avatarUrl = member.optString("avatar", "").trim().takeIf { it.isNotBlank() },
                                        pictures = parseCommentPictures(c),
                                    ),
                                )
                            }
                        }
                        val page = data.optJSONObject("page") ?: org.json.JSONObject()
                        val count = page.optInt("count", 0)
                        if (arr.length() == 0 || pn * 20 >= count) break
                        pn++
                    }
                    replies[comment.rpid] = out
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppToast.showLong(dialogContext, "回复加载失败")
                } finally {
                    replyLoading.remove(comment.rpid)
                    rerender?.invoke()
                }
            }
        }

        fun avatarView(url: String?, sizeDp: Int): ShapeableImageView =
            ShapeableImageView(dialogContext).apply {
                layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply {
                    marginEnd = dp(10)
                }
                shapeAppearanceModel =
                    ShapeAppearanceModel.builder()
                        .setAllCornerSizes(ShapeAppearanceModel.PILL)
                        .build()
                scaleType = ImageView.ScaleType.CENTER_CROP
                ImageLoader.loadInto(this, ImageUrl.avatar(url))
            }

        fun render() {
            val oldY = scroll.scrollY
            content.removeAllViews()
            val d = detail
            if (d != null) {
                content.addView(TextView(dialogContext).apply {
                    text = listOfNotNull(d.authorName.takeIf { it.isNotBlank() }, d.pubTime).joinToString(" · ")
                    setTextColor(Color.GRAY)
                    textSize = 13f
                })
                if (d.title.isNotBlank() && d.title != "动态") {
                    content.addView(TextView(dialogContext).apply {
                        text = d.title
                        setTextColor(Color.DKGRAY)
                        textSize = 19f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding(0, dp(8), 0, 0)
                    })
                }
                if (d.contentBlocks.isNotEmpty()) {
                    d.contentBlocks.forEach { block ->
                        if (block.imageUrls.isNotEmpty()) {
                            block.imageUrls.forEach { url ->
                                content.addView(ImageView(dialogContext).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    )
                                    adjustViewBounds = true
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setPadding(0, dp(10), 0, 0)
                                    ImageLoader.loadInto(this, ImageUrl.original(url))
                                })
                            }
                        }
                        block.text?.takeIf { it.isNotBlank() }?.let { raw ->
                            val heading = block.kind == "heading"
                            val quote = block.kind == "quote"
                            val code = block.kind == "code"
                            content.addView(TextView(dialogContext).apply {
                                text = formatDynamicBody(raw)
                                textSize = if (heading) 19f else 15f
                                setTextColor(Color.DKGRAY)
                                setTextIsSelectable(true)
                                setLineSpacing(dp(if (heading) 5 else 3).toFloat(), 1f)
                                setPadding(if (quote) dp(12) else 0, dp(if (heading) 12 else 7), 0, dp(if (quote) 7 else 0))
                                if (block.bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                                if (code) typeface = android.graphics.Typeface.MONOSPACE
                                if (quote) setBackgroundColor(Color.rgb(245, 245, 247))
                                gravity =
                                    when (block.align) {
                                        1 -> Gravity.CENTER_HORIZONTAL
                                        2 -> Gravity.END
                                        else -> Gravity.START
                                    }
                            })
                        }
                    }
                } else {
                    d.text?.takeIf { it.isNotBlank() && it != d.title }?.let { raw ->
                        content.addView(TextView(dialogContext).apply {
                            text = formatDynamicBody(raw)
                            textSize = 15f
                            setTextColor(Color.DKGRAY)
                            setTextIsSelectable(true)
                            setLineSpacing(dp(3).toFloat(), 1f)
                            setPadding(0, dp(8), 0, 0)
                        })
                    }
                    d.imageUrls.forEach { url ->
                        content.addView(ImageView(dialogContext).apply {
                            adjustViewBounds = true
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setPadding(0, dp(10), 0, 0)
                            ImageLoader.loadInto(this, ImageUrl.original(url))
                        })
                    }
                }
                d.forward?.let { forward ->
                    val card = MaterialCardView(dialogContext).apply {
                        radius = dp(10).toFloat()
                        cardElevation = 0f
                        setCardBackgroundColor(Color.rgb(245, 246, 248))
                        setContentPadding(dp(12), dp(10), dp(12), dp(10))
                    }
                    val column = LinearLayout(dialogContext).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val head = LinearLayout(dialogContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    head.addView(avatarView(forward.authorFace, 24))
                    head.addView(TextView(dialogContext).apply {
                        text = "转发自 @${forward.authorName}"
                        textSize = 13f
                        setTextColor(Color.GRAY)
                    })
                    column.addView(head)
                    forward.title?.takeIf { it.isNotBlank() }?.let { title ->
                        column.addView(TextView(dialogContext).apply {
                            text = formatDynamicBody(title)
                            textSize = 16f
                            setTextColor(Color.DKGRAY)
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                            setPadding(0, dp(8), 0, 0)
                        })
                    }
                    if (forward.contentBlocks.isNotEmpty()) {
                        forward.contentBlocks.forEach { block ->
                            block.text?.takeIf { it.isNotBlank() }?.let { text ->
                                column.addView(TextView(dialogContext).apply {
                                    this.text = formatDynamicBody(text)
                                    textSize = 14f
                                    setTextColor(Color.DKGRAY)
                                    setLineSpacing(dp(2).toFloat(), 1f)
                                    setPadding(0, dp(6), 0, 0)
                                })
                            }
                            block.imageUrls.forEach { url ->
                                column.addView(ImageView(dialogContext).apply {
                                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    adjustViewBounds = true
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setPadding(0, dp(8), 0, 0)
                                    ImageLoader.loadInto(this, ImageUrl.original(url))
                                })
                            }
                        }
                    } else {
                        forward.text?.takeIf { it.isNotBlank() }?.let { text ->
                            column.addView(TextView(dialogContext).apply {
                                this.text = formatDynamicBody(text)
                                textSize = 14f
                                setTextColor(Color.DKGRAY)
                                setLineSpacing(dp(2).toFloat(), 1f)
                                setPadding(0, dp(6), 0, 0)
                            })
                        }
                        forward.imageUrls.forEach { url ->
                            column.addView(ImageView(dialogContext).apply {
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setPadding(0, dp(8), 0, 0)
                                ImageLoader.loadInto(this, ImageUrl.original(url))
                            })
                        }
                    }
                    card.addView(column)
                    content.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
                }
                val stats = listOfNotNull(d.likeCount?.let { "点赞 $it" }, d.commentCount?.let { "评论 $it" }).joinToString(" · ")
                if (stats.isNotBlank()) {
                    content.addView(TextView(dialogContext).apply {
                        text = stats
                        setTextColor(Color.GRAY)
                        textSize = 12f
                        setPadding(0, dp(10), 0, 0)
                    })
                }
            }

            content.addView(TextView(dialogContext).apply {
                text = "评论"
                setTextColor(Color.DKGRAY)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(18), 0, dp(4))
            })
            if (!commentsStarted && !commentsLoading) {
                content.addView(TextView(dialogContext).apply {
                    text = commentError ?: "评论加载中…"
                    setTextColor(Color.GRAY)
                    textSize = 13f
                })
            } else if (comments.isEmpty()) {
                content.addView(TextView(dialogContext).apply {
                    text = commentError ?: "暂无评论"
                    setTextColor(Color.GRAY)
                    textSize = 13f
                })
            }
            comments.forEach { comment ->
                val rootRow = LinearLayout(dialogContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(0, dp(12), 0, dp(2))
                }
                rootRow.addView(avatarView(comment.avatarUrl, 36))
                val column = LinearLayout(dialogContext).apply {
                    orientation = LinearLayout.VERTICAL
                }
                column.addView(TextView(dialogContext).apply {
                    text = comment.name
                    setTextColor(Color.DKGRAY)
                    textSize = 14f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                val meta = listOfNotNull(comment.timeText, comment.location).joinToString(" · ")
                if (meta.isNotBlank()) column.addView(TextView(dialogContext).apply {
                    text = meta
                    setTextColor(Color.GRAY)
                    textSize = 11f
                })
                column.addView(TextView(dialogContext).apply {
                    text = comment.message
                    setTextColor(Color.DKGRAY)
                    textSize = 14f
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setPadding(0, dp(4), 0, 0)
                })
                if (comment.pictures.isNotEmpty()) {
                    column.addView(commentPictureRow(dialogContext, comment.pictures, 84))
                }
                val actions = LinearLayout(dialogContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                if (comment.replyCount > 0) {
                    val expanded = replies[comment.rpid]
                    if (expanded == null) {
                        actions.addView(action(if (replyLoading.contains(comment.rpid)) "加载中…" else "共${comment.replyCount}条回复") {
                            loadRepliesFor(comment)
                        })
                    }
                }
                actions.addView(action("赞 ${comment.like}".trim()) {
                    run("已点赞评论") { BiliApi.dynamicCommentReaction(comment.type, comment.oid, comment.rpid, true) }
                })
                actions.addView(action("回复") {
                    AppPopup.input(
                        context = dialogContext,
                        title = "回复 ${comment.name}",
                        initial = "",
                        hint = "输入回复内容",
                        inputType = InputType.TYPE_CLASS_TEXT,
                        onPositive = { text ->
                            if (text.isNotBlank()) run("回复已发送") {
                                BiliApi.dynamicCommentReply(comment.type, comment.oid, text, comment.rpid, comment.rpid)
                                replies.remove(comment.rpid)
                                loadRepliesFor(comment)
                            }
                        },
                    )
                })
                column.addView(actions)
                replies[comment.rpid]?.forEach { reply ->
                    val replyRow = LinearLayout(dialogContext).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                        setPadding(dp(4), dp(9), 0, 0)
                    }
                    replyRow.addView(avatarView(reply.avatarUrl, 24))
                    val replyText = TextView(dialogContext).apply {
                        text = "${reply.name}：${reply.message}"
                        textSize = 13f
                        setTextColor(Color.DKGRAY)
                        setLineSpacing(dp(2).toFloat(), 1f)
                    }
                    replyRow.addView(replyText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    column.addView(replyRow)
                    if (reply.pictures.isNotEmpty()) {
                        column.addView(commentPictureRow(dialogContext, reply.pictures, 64))
                    }
                }
                rootRow.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                content.addView(rootRow)
            }
            if (commentsLoading) {
                content.addView(TextView(dialogContext).apply {
                    text = "加载更多评论…"
                    textSize = 13f
                    setTextColor(Color.GRAY)
                    setPadding(0, dp(12), 0, 0)
                })
            } else if (!commentsEnd && commentsStarted) {
                content.addView(action("加载更多评论") { loadComments() })
            } else if (commentsEnd && comments.isNotEmpty()) {
                content.addView(TextView(dialogContext).apply {
                    text = "已加载全部评论"
                    textSize = 12f
                    setTextColor(Color.GRAY)
                    setPadding(0, dp(12), 0, 0)
                })
            }
            scroll.post { scroll.scrollTo(0, oldY) }
        }

        fun maybeLoadMore() {
            val body = scroll.getChildAt(0) ?: return
            if (!commentsEnd && !commentsLoading && scroll.scrollY + scroll.height >= body.height - dp(160)) {
                loadComments()
            }
        }

        scroll.setOnScrollChangeListener { _, _, _, _, _ -> maybeLoadMore() }

        var liked = false
        toolbar.addView(action("点赞") {
            run(if (liked) "已取消点赞" else "已点赞") {
                BiliApi.dynamicLike(article.id, !liked)
                liked = !liked
            }
        })
        toolbar.addView(action("评论") {
            AppPopup.input(
                context = dialogContext,
                title = "评论动态",
                initial = "",
                hint = "输入评论内容",
                inputType = InputType.TYPE_CLASS_TEXT,
                onPositive = { text ->
                    if (text.isNotBlank()) run("评论已发送") {
                        BiliApi.dynamicComment(
                            dynamicId = article.id,
                            message = text,
                            commentType = resolvedType,
                            oid = resolvedOid,
                        )
                        comments.clear()
                        commentPage = 1
                        commentsStarted = false
                        commentsEnd = false
                        loadComments()
                    }
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
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)))
        root.addView(loading)
        rerender = {
            render()
            scroll.post { maybeLoadMore() }
        }
        render()

        loadJob = scope.launch {
            try {
                detail = BiliApi.dynamicDetail(article.id)
                val bvid = detail?.bvid?.takeIf { it.isNotBlank() } ?: article.bvid?.takeIf { it.isNotBlank() }
                if (article.aid != null && article.aid > 0L) {
                    resolvedOid = article.aid
                    resolvedType = 1
                } else if (bvid != null) {
                    val aid = runCatching { BiliApi.videoDetail(bvid).aid }.getOrNull()?.takeIf { it > 0L }
                    if (aid != null) {
                        resolvedOid = aid
                        resolvedType = 1
                    }
                }
                loading.visibility = ProgressBar.GONE
                render()
                loadComments()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("Dynamic", "load user dynamic detail failed id=${article.id}", t)
                loading.visibility = ProgressBar.GONE
                content.addView(TextView(dialogContext).apply {
                    text = article.summary ?: article.title
                    textSize = 15f
                    setPadding(0, dp(12), 0, 0)
                })
            }
        }
        root
    }
}

private fun parseCommentPictures(content: org.json.JSONObject): List<String> {
    val arr = content.optJSONArray("pictures") ?: return emptyList()
    val out = ArrayList<String>(minOf(3, arr.length()))
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val url = o.optString("img_src", "").trim().ifBlank { o.optString("url", "").trim() }
        if (url.isNotBlank()) out.add(url)
        if (out.size >= 3) break
    }
    return out
}

private fun commentPictureRow(
    context: Context,
    urls: List<String>,
    sizeDp: Int,
): LinearLayout {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, (5 * density).toInt(), 0, 0)
    }
    urls.take(3).forEach { url ->
        row.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (6 * density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            ImageLoader.loadInto(this, ImageUrl.commentThumbnail(url))
        })
    }
    return row
}

private fun formatDynamicBody(raw: String): String {
    return raw
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\r\n", "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private fun formatRelativeTime(seconds: Long): String {
    val diff = (System.currentTimeMillis() / 1000L - seconds).coerceAtLeast(0L)
    return when {
        diff < 60L -> "刚刚"
        diff < 3600L -> "${diff / 60L}分钟前"
        diff < 86400L -> "${diff / 3600L}小时前"
        diff < 86400L * 7 -> "${diff / 86400L}天前"
        else -> {
            val date = java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).format(java.util.Date(seconds * 1000L))
            date
        }
    }
}

// 保留给独立详情页使用：展开完整楼中楼。
internal fun showDynamicReplyThread(
    context: Context,
    comment: DynamicCommentUi,
    scope: androidx.lifecycle.LifecycleCoroutineScope,
) {
    val lines = ArrayList<String>()
    var render: (() -> Unit)? = null
    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    AppPopup.custom(
        context = context,
        title = "回复 @${comment.name}",
        cancelable = true,
        actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
        preferredActionRole = PopupActionRole.POSITIVE,
        autoFocus = false,
    ) { dc ->
        val content = LinearLayout(dc).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val scroll = ScrollView(dc).apply { addView(content) }
        render = {
            content.removeAllViews()
            content.addView(TextView(dc).apply { text = "${comment.name}：${comment.message}"; textSize = 15f; setTextColor(Color.DKGRAY) })
            if (lines.isEmpty()) content.addView(TextView(dc).apply { text = "回复加载中…"; textSize = 13f; setTextColor(Color.GRAY); setPadding(0, dp(8), 0, 0) })
            lines.forEach { line -> content.addView(TextView(dc).apply { text = line; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(dp(12), dp(7), 0, 0) }) }
        }
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420))
        scope.launch {
            try {
                var pn = 1
                while (pn <= 20) {
                    val data = BiliApi.commentRepliesPage(comment.type, comment.oid, comment.rpid, pn, 20)
                    val arr = data.optJSONArray("replies") ?: org.json.JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val member = o.optJSONObject("member") ?: org.json.JSONObject()
                        val c = o.optJSONObject("content") ?: org.json.JSONObject()
                        val msg = c.optString("message", "").trim()
                        if (msg.isNotBlank()) lines.add("${member.optString("uname", "用户").ifBlank { "用户" }}：$msg")
                    }
                    val count = data.optJSONObject("page")?.optInt("count", 0) ?: 0
                    if (arr.length() == 0 || pn * 20 >= count) break
                    pn++
                }
            } catch (_: Throwable) {
            }
            render?.invoke()
        }
        render?.invoke()
        scroll
    }
}
