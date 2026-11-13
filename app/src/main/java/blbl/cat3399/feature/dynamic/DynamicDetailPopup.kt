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
import blbl.cat3399.databinding.ActivityUpDetailBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun showDynamicDetailPopup(
    context: Context,
    article: BiliApi.DynamicArticleCard,
    scope: androidx.lifecycle.LifecycleCoroutineScope,
) {
    var loadJob: kotlinx.coroutines.Job? = null
    var detail: BiliApi.DynamicDetail? = null
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
        root
    }
}
