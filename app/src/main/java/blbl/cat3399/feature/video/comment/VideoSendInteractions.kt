package blbl.cat3399.feature.video.comment

import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
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
    onSuccess: (() -> Unit)? = null,
) {
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
                    BiliApi.addVideoComment(oid = oid, message = text)
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
