package blbl.cat3399.feature.player

import android.graphics.Typeface
import android.widget.TextView
import androidx.media3.common.Player
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.feature.player.engine.ExoPlayerEngine
import blbl.cat3399.feature.player.engine.PlayerEngineKind
import java.util.Locale

internal fun PlayerActivity.showVideoDataPanel() {
    var statsText: TextView? = null
    val refresh =
        object : Runnable {
            override fun run() {
                val textView = statsText ?: return
                textView.text = buildVideoDataText()
                textView.postDelayed(this, 500L)
            }
        }
    AppPopup.custom(
        context = this,
        title = "视频数据",
        cancelable = true,
        actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
        preferredActionRole = PopupActionRole.POSITIVE,
        onModalAttached = { statsText?.post(refresh) },
        onDismiss = { statsText?.removeCallbacks(refresh) },
    ) { dialogContext ->
        TextView(dialogContext).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            val pad = (20f * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            text = buildVideoDataText()
            statsText = this
        }
    }
}

internal fun PlayerActivity.buildVideoDataText(): String {
    val engine = player ?: return "播放器尚未就绪"
    val exo = (engine as? ExoPlayerEngine)?.exoPlayer
    val videoFormat = exo?.videoFormat
    val audioFormat = exo?.audioFormat
    val width = currentVideoContentWidth ?: debug.videoInputWidth ?: videoFormat?.width ?: 0
    val height = currentVideoContentHeight ?: debug.videoInputHeight ?: videoFormat?.height ?: 0
    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "-"
    val codec =
        videoFormat?.codecs?.trim()?.takeIf { it.isNotBlank() }
            ?: videoFormat?.sampleMimeType?.trim()?.takeIf { it.isNotBlank() }
            ?: session.preferCodec
    val bitrateBps =
        videoFormat?.averageBitrate?.takeIf { it > 0 }
            ?: videoFormat?.bitrate?.takeIf { it > 0 }
            ?: videoFormat?.peakBitrate?.takeIf { it > 0 }
    val fps = debug.renderFps ?: debug.videoInputFps ?: videoFormat?.frameRate
    val state =
        when (engine.playbackState) {
            Player.STATE_IDLE -> "空闲"
            Player.STATE_BUFFERING -> "缓冲中"
            Player.STATE_READY -> if (engine.isPlaying) "播放中" else "就绪/暂停"
            Player.STATE_ENDED -> "播放结束"
            else -> engine.playbackState.toString()
        }
    val videoHost = debug.videoTransferHost?.trim()?.takeIf { it.isNotBlank() }
    val audioHost = debug.audioTransferHost?.trim()?.takeIf { it.isNotBlank() }
    val cdnHost = debug.cdnHost?.trim()?.takeIf { it.isNotBlank() }
    val line =
        when {
            videoHost != null && audioHost != null && videoHost != audioHost -> "video=$videoHost | audio=$audioHost"
            videoHost != null -> videoHost
            audioHost != null -> audioHost
            else -> cdnHost ?: "-"
        }
    val decoder = debug.videoDecoderName?.trim()?.takeIf { it.isNotBlank() } ?: "由播放器内核选择"
    val renderer =
        when (engine.kind) {
            PlayerEngineKind.ExoPlayer -> "ExoPlayer / SurfaceView"
            PlayerEngineKind.IjkPlayer -> "IjkPlayer / SurfaceView"
        }
    val audioId = session.actualAudioId.takeIf { it > 0 } ?: session.preferAudioId
    val audioTrack =
        buildString {
            append(audioLabel(audioId))
            audioFormat?.codecs?.trim()?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
            audioFormat?.sampleMimeType?.trim()?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
        }
    val source = pageListSource?.trim()?.takeIf { it.isNotBlank() } ?: partsListSource?.trim() ?: "-"
    return buildString {
        append("视频：").append(currentMainTitle?.trim().orEmpty().ifBlank { "-" }).append('\n')
        append("BV/CID：").append(currentBvid.ifBlank { "-" }).append(" / ").append(currentCid).append('\n')
        append("分辨率：").append(resolution).append('\n')
        append("编码：").append(session.preferCodec).append(" / ").append(codec).append('\n')
        append("码率：").append(formatBitrate(bitrateBps)).append('\n')
        append("帧率：").append(formatFps(fps)).append('\n')
        append("画质：").append(qnLabel(session.actualQn.takeIf { it > 0 } ?: selectedResolutionQn())).append('\n')
        append("状态：").append(state).append(" / speed=").append(String.format(Locale.US, "%.2fx", engine.playbackSpeed)).append('\n')
        append("当前/总时长：").append(formatHms(engine.currentPosition)).append(" / ").append(formatHms(engine.duration)).append('\n')
        append("缓冲：").append(formatHms(engine.bufferedPosition)).append(" / ").append(engine.bufferedPosition.coerceAtLeast(0L) * 100L / engine.duration.coerceAtLeast(1L)).append("%\n")
        append("渲染器：").append(renderer).append('\n')
        append("解码器：").append(decoder).append('\n')
        append("音轨：").append(audioTrack).append('\n')
        append("线路/CDN：").append(line).append('\n')
        append("列表来源：").append(source).append('\n')
        append("丢帧：").append(debug.droppedFramesTotal).append("  重缓冲：").append(debug.rebufferCount)
    }
}

private fun formatBitrate(bps: Int?): String {
    val value = bps?.takeIf { it > 0 } ?: return "-"
    return if (value >= 1_000_000) String.format(Locale.US, "%.2f Mbps", value / 1_000_000.0)
    else String.format(Locale.US, "%.0f kbps", value / 1_000.0)
}

private fun formatFps(fps: Float?): String {
    val value = fps?.takeIf { it > 0f } ?: return "-"
    val rounded = kotlin.math.round(value)
    return if (kotlin.math.abs(value - rounded) < 0.05f) rounded.toInt().toString()
    else String.format(Locale.US, "%.1f", value)
}
