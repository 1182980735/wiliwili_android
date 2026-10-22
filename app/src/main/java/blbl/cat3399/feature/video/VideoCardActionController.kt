package blbl.cat3399.feature.video

import android.content.Context
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.feature.player.DownloadRequestResolver
import blbl.cat3399.feature.player.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VideoCardDismissBehavior {
    data object LocalNotInterested : VideoCardDismissBehavior

    data object DeleteHistory : VideoCardDismissBehavior

    data object DeleteToView : VideoCardDismissBehavior

    data class DeleteFavFolderItem(
        val mediaId: Long,
    ) : VideoCardDismissBehavior
}

class VideoCardActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dismissBehavior: VideoCardDismissBehavior,
    private val onOpenDetail: (VideoCard, Int) -> Unit,
    private val onOpenUp: (VideoCard) -> Unit,
    private val onCardRemoved: (String) -> Unit = {},
    private val longPressActionProvider: () -> String = { BiliClient.prefs.videoCardLongPressAction },
) : VideoCardActionDelegate {
    private val inFlightActionKeys = HashSet<String>()

    override fun resolveLongPressAction(
        card: VideoCard,
        position: Int,
    ): VideoCardConfiguredLongPressAction = VideoCardConfiguredLongPressAction.fromPref(longPressActionProvider())

    override fun manualActions(
        card: VideoCard,
        position: Int,
    ): List<VideoCardQuickAction> {
        return listOf(
            VideoCardQuickAction.watchLater(context.getString(R.string.video_card_action_watch_later)),
            VideoCardQuickAction.openDetail(context.getString(R.string.video_card_action_open_detail)),
            VideoCardQuickAction.openUp(context.getString(R.string.video_card_action_open_up)),
            VideoCardQuickAction.dismiss(context.getString(dismissActionLabelRes())),
        )
    }

    override fun showMenu(
        card: VideoCard,
        position: Int,
    ): Boolean {
        val actions = buildMenuActions(card)
        if (actions.isEmpty()) return false
        AppPopup.singleChoice(
            context = context,
            title = "视频操作",
            items = actions.map { it.contentDescription.toString() },
            checkedIndex = 0,
            cancelable = true,
            onPicked = { index, _ ->
                actions.getOrNull(index)?.let { action ->
                    onActionSelected(card = card, position = position, action = action)
                }
            },
        )
        return true
    }

    override fun onActionSelected(
        card: VideoCard,
        position: Int,
        action: VideoCardQuickAction,
    ) {
        when (action.id) {
            VideoCardQuickActionId.PLAY -> playVideo(card)
            VideoCardQuickActionId.WATCH_LATER -> addToWatchLater(card)
            VideoCardQuickActionId.OPEN_DETAIL -> openDetail(card, position)
            VideoCardQuickActionId.OPEN_UP -> onOpenUp(card)
            VideoCardQuickActionId.SHARE -> shareVideo(card)
            VideoCardQuickActionId.DISMISS -> dismissCard(card)
            VideoCardQuickActionId.DOWNLOAD -> downloadVideo(card)
        }
    }

    private fun buildMenuActions(card: VideoCard): List<VideoCardQuickAction> {
        val actions =
            mutableListOf(
                VideoCardQuickAction.play("播放视频"),
                VideoCardQuickAction.openDetail(context.getString(R.string.video_card_action_open_detail)),
                VideoCardQuickAction.openUp(context.getString(R.string.video_card_action_open_up)),
                VideoCardQuickAction.watchLater(context.getString(R.string.video_card_action_watch_later)),
                VideoCardQuickAction.share("分享"),
                VideoCardQuickAction.dismiss(context.getString(dismissActionLabelRes())),
            )
        if (canDownload(card)) {
            actions.add(VideoCardQuickAction.download("下载"))
        }
        return actions
    }

    private fun playVideo(card: VideoCard) {
        if (card.hasPlayableVideoIdentity() == false || context.openPlayerForVideoCard(card) == false) {
            AppToast.show(context, context.getString(R.string.video_card_action_missing_video_id))
        }
    }

    private fun shareVideo(card: VideoCard) {
        if (context.shareVideoCard(card) == false) {
            AppToast.show(context, context.getString(R.string.video_card_action_missing_video_id))
        }
    }

    private fun canDownload(card: VideoCard): Boolean {
        return (card.cid ?: 0L) > 0L && card.hasPlayableVideoIdentity()
    }

    private fun downloadVideo(card: VideoCard) {
        val safeCid = card.cid?.takeIf { it > 0L }
        if (safeCid == null || card.hasPlayableVideoIdentity() == false) {
            AppToast.show(context, context.getString(R.string.video_card_action_missing_video_id))
            return
        }
        val actionKey = buildActionKey(card = card, suffix = "download")
        if (markInFlight(actionKey) == false) return

        val popup =
            AppPopup.progress(
                context = context,
                title = "下载视频",
                status = "获取播放地址…",
                negativeText = "取消",
                cancelable = false,
            )
        scope.launch {
            try {
                val request =
                    withContext(Dispatchers.IO) {
                        DownloadRequestResolver.resolve(
                            bvid = card.bvid,
                            cid = safeCid,
                            epId = card.epId?.takeIf { it > 0L },
                            qn = 64,
                            title = card.title,
                        )
                    }
                DownloadService.enqueue(context, request)
                popup?.dismiss()
                AppToast.showLong(context, "已加入下载队列")
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                popup?.dismiss()
                AppToast.show(
                    context,
                    "下载失败：" + (t.message ?: context.getString(R.string.video_card_action_failed)),
                )
            } finally {
                clearInFlight(actionKey)
            }
        }
    }

    private fun openDetail(
        card: VideoCard,
        position: Int,
    ) {
        if (!card.hasVideoDetailIdentity()) {
            AppToast.show(context, context.getString(R.string.video_card_action_open_detail_unsupported))
            return
        }
        onOpenDetail(card, position)
    }

    private fun addToWatchLater(card: VideoCard) {
        val safeBvid = card.bvid.trim()
        val safeAid = card.aid?.takeIf { it > 0L }
        if (safeBvid.isBlank() && safeAid == null) {
            AppToast.show(context, context.getString(R.string.video_card_action_missing_video_id))
            return
        }
        val actionKey = buildActionKey(card = card, suffix = "watch_later")
        if (!markInFlight(actionKey)) return

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    BiliApi.toViewAdd(
                        bvid = safeBvid.takeIf { it.isNotBlank() },
                        aid = safeAid,
                    )
                }
                AppToast.show(context, context.getString(R.string.video_card_action_watch_later_done))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppToast.show(context, errorMessage(t))
            } finally {
                clearInFlight(actionKey)
            }
        }
    }

    private fun dismissCard(card: VideoCard) {
        when (dismissBehavior) {
            VideoCardDismissBehavior.LocalNotInterested -> {
                val actionKey = buildActionKey(card = card, suffix = "not_interested")
                if (!markInFlight(actionKey)) return

                val stableKey = card.stableKey()
                VideoCardVisibilityFilter.hide(stableKey)
                onCardRemoved(stableKey)
                AppToast.show(context, context.getString(R.string.video_card_action_not_interested_done))

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            BiliApi.videoFeedbackDislike(card)
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppLog.w("VideoCardAction", "report not interested failed stableKey=$stableKey", t)
                    } finally {
                        clearInFlight(actionKey)
                    }
                }
            }

            else -> {
                val actionKey = buildActionKey(card = card, suffix = "dismiss")
                if (!markInFlight(actionKey)) return

                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            when (val behavior = dismissBehavior) {
                                VideoCardDismissBehavior.DeleteHistory -> {
                                    BiliApi.historyDelete(kid = buildHistoryKid(card))
                                }

                                VideoCardDismissBehavior.DeleteToView -> {
                                    BiliApi.toViewDelete(aid = resolveAid(card))
                                }

                                is VideoCardDismissBehavior.DeleteFavFolderItem -> {
                                    val aid = resolveAid(card)
                                    BiliApi.favResourceDelete(
                                        aid = aid,
                                        mediaId = behavior.mediaId,
                                    )
                                }

                                VideoCardDismissBehavior.LocalNotInterested -> Unit
                            }
                        }
                        onCardRemoved(card.stableKey())
                        AppToast.show(context, context.getString(R.string.video_card_action_delete_done))
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppToast.show(context, errorMessage(t))
                    } finally {
                        clearInFlight(actionKey)
                    }
                }
            }
        }
    }

    private suspend fun resolveAid(card: VideoCard): Long {
        card.aid?.takeIf { it > 0L }?.let { return it }
        val bvid = card.bvid.trim().takeIf { it.isNotBlank() } ?: throw BiliApiException(apiCode = -400, apiMessage = "missing_video_id")
        return BiliApi.videoDetail(bvid).aid?.takeIf { it > 0L }
            ?: throw BiliApiException(apiCode = -400, apiMessage = "missing_video_id")
    }

    private suspend fun buildHistoryKid(card: VideoCard): String {
        return when (card.business?.trim()) {
            "archive" -> "archive_${resolveAid(card)}"
            "pgc" -> {
                val seasonId = card.seasonId?.takeIf { it > 0L } ?: throw BiliApiException(apiCode = -400, apiMessage = "missing_history_kid")
                "pgc_$seasonId"
            }

            else -> throw BiliApiException(apiCode = -400, apiMessage = "missing_history_kid")
        }
    }

    private fun dismissActionLabelRes(): Int {
        return when (dismissBehavior) {
            VideoCardDismissBehavior.LocalNotInterested -> R.string.video_card_action_not_interested
            else -> R.string.video_card_action_delete
        }
    }

    private fun buildActionKey(
        card: VideoCard,
        suffix: String,
    ): String = "${card.stableKey()}|$suffix"

    private fun markInFlight(actionKey: String): Boolean {
        synchronized(inFlightActionKeys) {
            return inFlightActionKeys.add(actionKey)
        }
    }

    private fun clearInFlight(actionKey: String) {
        synchronized(inFlightActionKeys) {
            inFlightActionKeys.remove(actionKey)
        }
    }

    private fun errorMessage(t: Throwable): String {
        val raw = (t as? BiliApiException)?.apiMessage?.trim().orEmpty().ifBlank { t.message.orEmpty().trim() }
        return when {
            raw.isBlank() -> context.getString(R.string.video_card_action_failed)
            raw == "missing_history_kid" -> context.getString(R.string.video_card_action_missing_video_id)
            raw == "missing_video_id" -> context.getString(R.string.video_card_action_missing_video_id)
            else -> raw
        }
    }
}
