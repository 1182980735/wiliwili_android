package blbl.cat3399.feature.dynamic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.databinding.ActivityDynamicDetailBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class DynamicDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityDynamicDetailBinding
    private var dynamicId: Long = 0L
    private var commentOid: Long = 0L
    private var commentType: Int = 17
    private var liked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDynamicDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        dynamicId = intent.getLongExtra(EXTRA_ID, 0L)
        commentOid = intent.getLongExtra(EXTRA_COMMENT_OID, dynamicId)
        commentType = intent.getIntExtra(EXTRA_COMMENT_TYPE, 17)
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "动态详情" }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnBack.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                binding.actionBar.getChildAt(0)?.requestFocus()
                true
            } else false
        }
        setupActions()
        loadDetail()
        loadComments()
    }

    private fun action(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(MaterialColorsOnSurface())
            setPadding(18, 8, 18, 8)
            isClickable = true
            isFocusable = true
            background = getDrawable(blbl.cat3399.R.drawable.bg_wiliwili_page_tab)
            setOnClickListener { onClick() }
        }

    private fun MaterialColorsOnSurface(): Int =
        com.google.android.material.color.MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)

    private fun setupActions() {
        binding.actionBar.removeAllViews()
        binding.actionBar.addView(action("点赞") {
            lifecycleScope.launch {
                runCatching { BiliApi.dynamicLike(dynamicId, !liked) }
                    .onSuccess { liked = !liked; AppToast.show(this@DynamicDetailActivity, if (liked) "已点赞" else "已取消点赞") }
                    .onFailure { toastError(it) }
            }
        })
        binding.actionBar.addView(action("评论") {
            AppPopup.input(this, "评论动态", "", "输入评论内容", inputType = InputType.TYPE_CLASS_TEXT, onPositive = { text ->
                if (text.isNotBlank()) lifecycleScope.launch {
                    runCatching { BiliApi.dynamicComment(dynamicId, text) }
                        .onSuccess { AppToast.show(this@DynamicDetailActivity, "评论已发送"); loadComments(force = true) }
                        .onFailure { toastError(it) }
                }
            })
        })
        binding.actionBar.addView(action("转发") {
            AppPopup.input(this, "转发动态", "", "转发理由（可留空）", onPositive = { text ->
                lifecycleScope.launch {
                    runCatching { BiliApi.dynamicRepost(dynamicId, text) }
                        .onSuccess { AppToast.show(this@DynamicDetailActivity, "已转发") }
                        .onFailure { toastError(it) }
                }
            })
        })
        binding.actionBar.addView(action("删除") {
            lifecycleScope.launch {
                runCatching { BiliApi.dynamicDelete(dynamicId) }
                    .onSuccess { AppToast.show(this@DynamicDetailActivity, "动态已删除"); finish() }
                    .onFailure { toastError(it) }
            }
        })
    }

    private fun loadDetail() {
        binding.loading.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val d = BiliApi.dynamicDetail(dynamicId)
                binding.tvTitle.text = d.title.takeIf { it.isNotBlank() && it != "动态" } ?: "动态详情"
                renderDetail(d)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                binding.tvTitle.text = "动态加载失败"
                AppLog.w("DynamicDetail", "load failed id=$dynamicId", t)
                AppToast.showLong(this@DynamicDetailActivity, t.message ?: "动态加载失败")
            } finally {
                binding.loading.visibility = android.view.View.GONE
            }
        }
    }

    private fun renderDetail(d: BiliApi.DynamicDetail) {
        val content = binding.content
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = listOfNotNull(d.authorName.takeIf { it.isNotBlank() }, d.pubTime).joinToString(" · ")
            setTextColor(Color.GRAY); textSize = 13f
        })
        if (d.title.isNotBlank() && d.title != "动态") content.addView(TextView(this).apply {
            text = d.title; setTextColor(MaterialColorsOnSurface()); textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, 10, 0, 0)
        })
        d.text?.takeIf { it.isNotBlank() && it != d.title }?.let {
            content.addView(TextView(this).apply {
                text = it; textSize = 16f; setTextColor(MaterialColorsOnSurface())
                setTextIsSelectable(true); setPadding(0, 10, 0, 0)
            })
        }
        d.imageUrls.forEach { url ->
            content.addView(ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(0, 12, 0, 0)
                ImageLoader.loadInto(this, ImageUrl.original(url))
            })
        }
        val stats = listOfNotNull(d.likeCount?.let { "点赞 $it" }, d.commentCount?.let { "评论 $it" }).joinToString(" · ")
        if (stats.isNotBlank()) content.addView(TextView(this).apply {
            text = stats; setTextColor(Color.GRAY); textSize = 12f; setPadding(0, 12, 0, 0)
        })
    }

    private fun loadComments(force: Boolean = false) {
        lifecycleScope.launch {
            try {
                val data = BiliApi.commentPage(type = commentType, oid = commentOid, sort = 1, pn = 1, ps = 20, noHot = 1)
                val replies = data.optJSONArray("replies") ?: org.json.JSONArray()
                val box = LinearLayout(this@DynamicDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 24, 0, 0)
                }
                box.addView(TextView(this@DynamicDetailActivity).apply {
                    text = "评论"; textSize = 17f; setTextColor(MaterialColorsOnSurface())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                if (replies.length() == 0) box.addView(TextView(this@DynamicDetailActivity).apply {
                    text = "暂无评论"; textSize = 14f; setTextColor(Color.GRAY); setPadding(0, 8, 0, 0)
                })
                for (i in 0 until replies.length()) {
                    val o = replies.optJSONObject(i) ?: continue
                    val member = o.optJSONObject("member") ?: org.json.JSONObject()
                    val c = o.optJSONObject("content") ?: org.json.JSONObject()
                    val name = member.optString("uname", "").trim().ifBlank { "用户" }
                    val msg = c.optString("message", "").trim()
                    if (msg.isBlank()) continue
                    val rpid = o.optLong("rpid", 0L)
                    val replyCount = o.optInt("count", 0).coerceAtLeast(0)
                    val row = LinearLayout(this@DynamicDetailActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.TOP
                        setPadding(0, 10, 0, 0)
                    }
                    row.addView(TextView(this@DynamicDetailActivity).apply {
                        text = "$name：$msg"; textSize = 14f; setTextColor(Color.DKGRAY)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    if (replyCount > 0 && rpid > 0L) {
                        row.addView(TextView(this@DynamicDetailActivity).apply {
                            text = "展开$replyCount"; textSize = 13f; setTextColor(Color.GRAY)
                            setPadding(12, 0, 12, 0); isClickable = true; isFocusable = true
                            setOnClickListener {
                                showDynamicReplyThread(
                                    context = this@DynamicDetailActivity,
                                    comment = DynamicCommentUi(
                                        rpid = rpid,
                                        oid = commentOid,
                                        type = commentType,
                                        name = name,
                                        message = msg,
                                        like = o.optInt("like", 0),
                                        replyCount = replyCount,
                                    ),
                                    scope = lifecycleScope,
                                )
                            }
                        })
                    }
                    box.addView(row)
                }
                binding.content.addView(box)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                binding.content.addView(TextView(this@DynamicDetailActivity).apply {
                    text = "评论加载失败：${(t as? BiliApiException)?.apiMessage ?: t.message.orEmpty()}"
                    textSize = 13f; setTextColor(Color.GRAY); setPadding(0, 20, 0, 0)
                })
            }
        }
    }

    private fun toastError(t: Throwable) {
        val msg = (t as? BiliApiException)?.apiMessage?.takeIf { it.isNotBlank() } ?: t.message.orEmpty().ifBlank { "操作失败" }
        AppToast.showLong(this, msg)
    }

    companion object {
        private const val EXTRA_ID = "dynamic_id"
        private const val EXTRA_COMMENT_OID = "dynamic_comment_oid"
        private const val EXTRA_COMMENT_TYPE = "dynamic_comment_type"
        private const val EXTRA_TITLE = "dynamic_title"

        fun intentFor(context: Context, article: BiliApi.DynamicArticleCard): Intent =
            Intent(context, DynamicDetailActivity::class.java)
                .putExtra(EXTRA_ID, article.id)
                .putExtra(EXTRA_COMMENT_OID, article.commentOid ?: article.aid ?: article.id)
                .putExtra(EXTRA_COMMENT_TYPE, article.commentType ?: if (article.aid != null) 1 else 17)
                .putExtra(EXTRA_TITLE, article.title)
    }
}
