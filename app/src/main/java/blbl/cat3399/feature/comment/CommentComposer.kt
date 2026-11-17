package blbl.cat3399.feature.comment

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.ThemeColor
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.core.ui.popup.PopupHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 评论输入框。点击“＠关注的人”会异步读取我的关注列表，
 * 以关注列表顺序提供 @ 候选，插入到当前光标位置。
 */
internal fun showCommentComposer(
    context: Context,
    scope: LifecycleCoroutineScope,
    title: String,
    initial: String = "",
    hint: String = "说点什么…",
    positiveText: String = "发布",
    maxLength: Int = 1000,
    onPositive: (String) -> Unit,
) {
    val density = context.resources.displayMetrics.density
    val accent = ThemeColor.resolve(context, R.attr.blblAccent, R.color.blbl_accent_wiliwili)
    fun dp(v: Int) = (v * density).toInt()
    var handle: PopupHandle? = null
    var input: EditText? = null
    var mentionsLoaded = false
    val followingNames = ArrayList<String>()

    handle =
        AppPopup.custom(
            context = context,
            title = title,
            cancelable = true,
            actions =
                listOf(
                    PopupAction(role = PopupActionRole.NEGATIVE, text = "取消"),
                    PopupAction(role = PopupActionRole.POSITIVE, text = positiveText, dismissOnClick = false) {
                        val text = input?.text?.toString().orEmpty()
                        when {
                            text.trim().isEmpty() -> {
                                AppToast.show(context, "内容不能为空")
                                return@PopupAction
                            }
                            text.trim().length > maxLength -> {
                                AppToast.show(context, "内容不能超过 $maxLength 个字符")
                                return@PopupAction
                            }
                        }
                        onPositive(text)
                        handle?.dismiss()
                    },
                ),
            preferredActionRole = PopupActionRole.POSITIVE,
            autoFocus = false,
            onModalAttached = { input?.post { input?.requestFocus() } },
        ) { dialogContext ->
            val column = LinearLayout(dialogContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
            val edit =
                EditText(dialogContext).apply {
                    setText(initial)
                    setSelection(text?.length ?: 0)
                    this.hint = hint
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    minLines = 3
                    maxLines = 8
                }
            input = edit
            column.addView(
                edit,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            val mentionButton =
                TextView(dialogContext).apply {
                    text = "＠关注的人"
                    textSize = 14f
                    setTextColor(accent)
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    isClickable = true
                    isFocusable = true
                }
            column.addView(mentionButton)

            val suggestions = HorizontalScrollView(dialogContext).apply {
                visibility = View.GONE
                isHorizontalScrollBarEnabled = false
            }
            val row = LinearLayout(dialogContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            suggestions.addView(row)
            column.addView(suggestions)

            fun addMentionChip(name: String) {
                row.addView(
                    TextView(dialogContext).apply {
                        text = "@$name"
                        textSize = 13f
                        setTextColor(accent)
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val start = edit.selectionStart.coerceAtLeast(0)
                            edit.text.insert(start, "@$name ")
                            edit.setSelection((start + name.length + 2).coerceAtMost(edit.text.length))
                        }
                    },
                )
            }

            fun showFollowingSuggestions() {
                row.removeAllViews()
                if (followingNames.isEmpty()) {
                    row.addView(TextView(dialogContext).apply {
                        text = "暂无关注用户"
                        textSize = 13f
                        setTextColor(ContextCompat.getColor(dialogContext, android.R.color.darker_gray))
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                    })
                } else {
                    followingNames.take(30).forEach(::addMentionChip)
                }
                suggestions.visibility = View.VISIBLE
            }

            fun loadFollowingSuggestions() {
                if (mentionsLoaded) {
                    suggestions.visibility = if (suggestions.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    return
                }
                if (!BiliClient.cookies.hasSessData()) {
                    AppToast.show(dialogContext, "登录后才能读取关注列表")
                    return
                }
                suggestions.visibility = View.VISIBLE
                row.removeAllViews()
                row.addView(TextView(dialogContext).apply {
                    text = "加载关注列表…"
                    textSize = 13f
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                })
                scope.launch {
                    try {
                        val nav = BiliApi.nav().optJSONObject("data")
                        val mid = nav?.optLong("mid") ?: 0L
                        if (mid <= 0L) return@launch
                        val page = BiliApi.followingsPage(vmid = mid, pn = 1, ps = 50)
                        followingNames.clear()
                        page.items.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().forEach(followingNames::add)
                        mentionsLoaded = true
                        showFollowingSuggestions()
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        row.removeAllViews()
                        row.addView(TextView(dialogContext).apply {
                            text = "关注列表加载失败"
                            textSize = 13f
                            setPadding(dp(8), dp(6), dp(8), dp(6))
                        })
                    }
                }
            }

            mentionButton.setOnClickListener { loadFollowingSuggestions() }
            column
        }
    }
