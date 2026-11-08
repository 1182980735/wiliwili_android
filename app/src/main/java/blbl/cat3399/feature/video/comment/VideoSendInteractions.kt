package blbl.cat3399.feature.video.comment

import android.content.Intent
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.feature.login.QrLoginActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun showSendDanmakuDialog(
    activity: ComponentActivity,
    cid: Long,
    aid: Long?,
    bvid: String?,
    progressMs: Long,
    onSuccess: (() -> Unit)? = null,
) {
    if (!ensurePostingSession(activity)) return
    if (cid <= 0L) {
        AppToast.show(activity, "当前视频缺少 cid")
        return
    }
    AppPopup.input(
        context = activity,
        title = "发送弹幕",
        initial = "",
        hint = "发一条友善的弹幕",
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        minLines = 1,
        positiveText = "发送",
        negativeText = "取消",
        validate = { text ->
            val length = text.trim().length
            when {
                length == 0 -> {
                    AppToast.show(activity, "弹幕不能为空")
                    false
                }
                length > 100 -> {
                    AppToast.show(activity, "弹幕不能超过 100 个字符")
                    false
                }
                else -> true
            }
        },
        onPositive = { text ->
            activity.lifecycleScope.launch {
                try {
                    BiliApi.sendVideoDanmaku(
                        cid = cid,
                        aid = aid,
                        bvid = bvid,
                        message = text,
                        progressMs = progressMs,
                    )
                    AppToast.show(activity, "弹幕已发送")
                    onSuccess?.invoke()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.w("DanmakuSend", "send failed cid=$cid", t)
                    AppToast.showLong(activity, "弹幕发送失败：${t.message ?: "未知错误"}")
                }
            }
        },
    )
}

internal fun showAddCommentDialog(
    activity: ComponentActivity,
    oid: Long,
    root: Long? = null,
    parent: Long? = null,
    onSuccess: (() -> Unit)? = null,
) {
    if (!ensurePostingSession(activity)) return
    if (oid <= 0L) {
        AppToast.show(activity, "当前视频缺少 aid")
        return
    }
    AppPopup.input(
        context = activity,
        title = "写评论",
        initial = "",
        hint = "说点什么…",
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
        minLines = 3,
        positiveText = "发布",
        negativeText = "取消",
        validate = { text ->
            val length = text.trim().length
            when {
                length == 0 -> {
                    AppToast.show(activity, "评论不能为空")
                    false
                }
                length > 1000 -> {
                    AppToast.show(activity, "评论不能超过 1000 个字符")
                    false
                }
                else -> true
            }
        },
        onPositive = { text ->
            activity.lifecycleScope.launch {
                try {
                    BiliApi.addVideoComment(oid = oid, message = text, root = root, parent = parent)
                    AppToast.show(activity, "评论已发布")
                    onSuccess?.invoke()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.w("CommentSend", "comment failed oid=$oid", t)
                    AppToast.showLong(activity, "评论发布失败：${t.message ?: "未知错误"}")
                }
            }
        },
    )
}

internal fun showCommentActionDialog(
    activity: ComponentActivity,
    oid: Long,
    item: VideoCommentItem,
    onChanged: (() -> Unit)? = null,
) {
    AppPopup.singleChoice(
        context = activity,
        title = "评论操作",
        items = listOf("点赞", "点踩", "回复"),
        checkedIndex = -1,
        onPicked = { index, _ ->
            when (index) {
                0 -> runCommentReaction(activity, oid, item.rpid, like = true, onChanged = onChanged)
                1 -> runCommentReaction(activity, oid, item.rpid, like = false, onChanged = onChanged)
                2 -> {
                    val root = item.rootRpid.takeIf { it > 0L } ?: item.rpid
                    showAddCommentDialog(
                        activity = activity,
                        oid = oid,
                        root = root,
                        parent = item.rpid,
                        onSuccess = onChanged,
                    )
                }
            }
        },
    )
}

private fun ensurePostingSession(activity: ComponentActivity): Boolean {
    if (!BiliClient.cookies.hasSessData()) {
        AppPopup.confirm(
            context = activity,
            title = "需要登录",
            message = "发送弹幕和评论需要先登录 B 站账号。",
            positiveText = "去登录",
            negativeText = "取消",
            onPositive = { activity.startActivity(Intent(activity, QrLoginActivity::class.java)) },
        )
        return false
    }
    if (BiliClient.cookies.getCookieValue("bili_jct").orEmpty().trim().isBlank()) {
        AppPopup.confirm(
            context = activity,
            title = "登录状态异常",
            message = "当前登录 Cookie 缺少 CSRF 令牌，请退出后重新扫码登录。",
            positiveText = "重新登录",
            negativeText = "取消",
            onPositive = { activity.startActivity(Intent(activity, QrLoginActivity::class.java)) },
        )
        return false
    }
    return true
}

private fun runCommentReaction(
    activity: ComponentActivity,
    oid: Long,
    rpid: Long,
    like: Boolean,
    onChanged: (() -> Unit)? = null,
) {
    if (!ensurePostingSession(activity)) return
    activity.lifecycleScope.launch {
        try {
            if (like) {
                BiliApi.likeVideoComment(oid = oid, rpid = rpid)
            } else {
                BiliApi.dislikeVideoComment(oid = oid, rpid = rpid)
            }
            AppToast.show(activity, if (like) "已点赞" else "已点踩")
            onChanged?.invoke()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLog.w("CommentAction", "comment reaction failed oid=$oid rpid=$rpid like=$like", t)
            AppToast.showLong(activity, "操作失败：${t.message ?: "未知错误"}")
        }
    }
}
