package blbl.cat3399.feature.player

import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.feature.video.comment.showSendDanmakuDialog

internal fun PlayerActivity.showPlayerDanmakuComposer() {
    val cid = currentCid.takeIf { it > 0L }
    if (cid == null) {
        AppToast.show(this, "当前视频缺少 cid")
        return
    }
    showSendDanmakuDialog(
        activity = this,
        cid = cid,
        aid = currentAid,
        bvid = currentBvid,
        progressMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
    )
}
