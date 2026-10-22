package blbl.cat3399.feature.video

import android.view.View
import android.widget.SeekBar
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.video.VideoPlayKind
import blbl.cat3399.core.api.video.VideoPlayRequest
import blbl.cat3399.core.model.Danmaku
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.util.Format
import blbl.cat3399.feature.player.danmaku.DanmakuConfig
import blbl.cat3399.feature.player.danmaku.DanmakuFontWeight
import blbl.cat3399.feature.player.danmaku.DanmakuLaneDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class VideoInlinePlayerController(
    private val activity: BaseActivity,
    private val scope: CoroutineScope,
    private val headerAdapter: VideoDetailHeaderAdapter,
    private val onOpenFullscreen: () -> Unit,
    private val onOpenQuality: () -> Unit,
) {
    private var engine: ExoPlayer? = null
    private var playbackJob: Job? = null
    private var danmakuJob: Job? = null
    private var preparedCid: Long? = null
    private var durationMs: Long = 0L
    private var dragging = false
    private var attachedViews: InlinePlayerViews? = null
    private var lastBvid: String = ""
    private var lastAid: Long? = null
    private var lastCid: Long? = null
    private var lastAutoPlay: Boolean = true
    private var candidateUrls: List<String> = emptyList()
    private var candidateIndex: Int = 0
    private var firstFrameRendered: Boolean = false
    private var watchdogJob: Job? = null
    private var lastStartPositionMs: Long = 0L
    private val progressRunnable =
        object : Runnable {
            override fun run() {
                updateProgress()
                activity.window?.decorView?.postDelayed(this, 500L)
            }
        }

    fun prepare(
        bvid: String,
        aid: Long?,
        cid: Long?,
        autoPlay: Boolean,
        startPositionMs: Long = 0L,
        forceReload: Boolean = false,
    ) {
        val safeCid = cid?.takeIf { it > 0L } ?: return
        lastBvid = bvid.trim()
        lastAid = aid?.takeIf { it > 0L }
        lastCid = safeCid
        lastAutoPlay = autoPlay
        if (forceReload.not() && preparedCid == safeCid && engine is ExoPlayer) {
            if (autoPlay) engine?.play()
            return
        }
        val player = ensureEngine()
        attachViews(player)
        playbackJob?.cancel()
        preparedCid = null
        showLoading(true)
        playbackJob =
            scope.launch {
                try {
                    val stream =
                        withContext(Dispatchers.IO) {
                            BiliApi.playUrl(
                                VideoPlayRequest(
                                    kind = VideoPlayKind.UGC,
                                    bvid = lastBvid.takeIf { it.isNotBlank() },
                                    aid = lastAid,
                                    cid = safeCid,
                                    qn = BiliClient.prefs.playerPreferredQn.coerceAtMost(32),
                                    fnval = 1,
                                    tryLook = false,
                                    preferCodec = "AVC",
                                ),
                            )
                        }
                    val progressive = stream.progressive.firstOrNull { it.urls.isNotEmpty() }
                        ?: error("没有可用播放地址")
                    durationMs = stream.durationMs ?: progressive.lengthMs ?: 0L
                    candidateUrls = progressive.urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    candidateIndex = 0
                    firstFrameRendered = false
                    lastStartPositionMs = startPositionMs
                    val initialUrl = candidateUrls.firstOrNull() ?: error("没有可用播放地址")
                    val views = attachViews(player) ?: return@launch
                    val mediaSource = createMediaSource(initialUrl)
                    if (startPositionMs > 0L) {
                        player.setMediaSource(mediaSource, startPositionMs)
                    } else {
                        player.setMediaSource(mediaSource)
                    }
                    player.prepare()
                    player.playWhenReady = autoPlay
                    if (autoPlay) player.play()
                    preparedCid = safeCid
                    views.playerView.visibility = View.VISIBLE
                    views.coverImage.visibility = View.VISIBLE
                    views.coverImage.bringToFront()
                    views.loading.bringToFront()
                    views.controls.bringToFront()
                    views.coverPlay.bringToFront()
                    views.controls.visibility = View.VISIBLE
                    views.coverPlay.visibility = View.GONE
                    views.danmakuView.visibility =
                        if (BiliClient.prefs.danmakuEnabled) View.VISIBLE else View.GONE
                    loadDanmaku(safeCid, durationMs)
                    scheduleFirstFrameRetry(player, autoPlay)
                    scheduleProgress()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppToast.show(activity, t.message ?: "内嵌播放器加载失败")
                } finally {
                    showLoading(false)
                }
            }
    }
    fun toggle() {
        val player = engine
        if (player == null) {
            prepare(lastBvid, lastAid, lastCid, autoPlay = true)
            return
        }
        if (preparedCid == null) {
            prepare(lastBvid, lastAid, lastCid, autoPlay = true)
            return
        }
        if (player.isPlaying) player.pause() else player.play()
        updateProgress()
    }

    fun seekBy(deltaMs: Long) {
        val player = engine
        if (player == null || preparedCid == null) {
            val target = (deltaMs * 1000L).coerceAtLeast(0L)
            prepare(lastBvid, lastAid, lastCid, autoPlay = true, startPositionMs = target)
            return
        }
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(target)
        attachedViews?.danmakuView?.notifySeek(target)
        updateProgress()
    }

    fun reloadForQuality() {
        val cid = lastCid ?: return
        val position = engine?.currentPosition?.coerceAtLeast(0L) ?: 0L
        prepare(lastBvid, lastAid, cid, autoPlay = true, startPositionMs = position, forceReload = true)
    }

    fun pause() {
        engine?.pause()
        activity.window?.decorView?.removeCallbacks(progressRunnable)
    }

    fun onResume() {
        if (engine is ExoPlayer) {
            engine?.let { attachViews(it) }
            scheduleProgress()
        }
    }

    fun release() {
        playbackJob?.cancel()
        danmakuJob?.cancel()
        watchdogJob?.cancel()
        watchdogJob = null
        activity.window?.decorView?.removeCallbacks(progressRunnable)
        attachedViews?.playerView?.player = null
        engine?.release()
        engine = null
        preparedCid = null
        attachedViews = null
    }
    private fun ensureEngine(): ExoPlayer {
        val existing = engine
        if (existing is ExoPlayer) return existing
        val created = ExoPlayer.Builder(activity).build()
        created.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateProgress()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateProgress()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    attachedViews?.danmakuView?.notifySeek(newPosition.positionMs)
                }

                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                    watchdogJob?.cancel()
                    val views = attachedViews ?: return
                    views.coverImage.visibility = View.GONE
                    views.loading.visibility = View.GONE
                }
            },
        )
        engine = created
        return created
    }

    private fun scheduleFirstFrameRetry(player: ExoPlayer, autoPlay: Boolean) {
        watchdogJob?.cancel()
        watchdogJob =
            scope.launch {
                delay(8_000L)
                if (firstFrameRendered) return@launch
                val urls = candidateUrls
                if (urls.isEmpty()) return@launch
                candidateIndex = (candidateIndex + 1) % urls.size
                val position = player.currentPosition.coerceAtLeast(0L)
                player.setMediaSource(createMediaSource(urls[candidateIndex]), position)
                player.prepare()
                player.playWhenReady = autoPlay
                if (autoPlay) player.play()
                scheduleFirstFrameRetry(player, autoPlay)
            }
    }

    private fun attachViews(player: ExoPlayer): InlinePlayerViews? {
        val views = headerAdapter.inlinePlayerViews() ?: return null
        if (attachedViews === views) return views
        attachedViews = views
        views.playerView.player = player
        views.btnPlay.setOnClickListener { toggle() }
        views.btnQuality.setOnClickListener { onOpenQuality() }
        views.btnFullscreen.setOnClickListener { onOpenFullscreen() }
        views.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    dragging = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    dragging = false
                    val total = durationMs.takeIf { it > 0L } ?: return
                    val target = total * (seekBar?.progress ?: 0) / 1000L
                    player.seekTo(target)
                    views.danmakuView.notifySeek(target)
                }
            },
        )
        views.danmakuView.setPositionProvider { player.currentPosition }
        views.danmakuView.setIsPlayingProvider { player.isPlaying }
        views.danmakuView.setPlaybackSpeedProvider { player.playbackParameters.speed }
        views.danmakuView.setConfigProvider { danmakuConfig() }
        return views
    }
    private fun scheduleProgress() {
        val decor = activity.window?.decorView ?: return
        decor.removeCallbacks(progressRunnable)
        decor.post(progressRunnable)
    }

    private fun updateProgress() {
        val player = engine ?: return
        val views = attachViews(player) ?: return
        val total = player.duration.takeIf { it > 0L } ?: durationMs
        if (total > 0L) {
            if (dragging.not()) {
                views.seekBar.progress =
                    ((player.currentPosition.coerceAtLeast(0L) * 1000L) / total).toInt()
            }
            views.time.text =
                "${Format.clock(player.currentPosition.coerceAtLeast(0L) / 1000L)} / ${Format.clock(total / 1000L)}"
        }
        views.btnPlay.setImageResource(
            if (player.isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play,
        )
        views.coverPlay.visibility = if (player.isPlaying) View.GONE else View.VISIBLE
        views.loading.visibility =
            if (firstFrameRendered.not()) View.VISIBLE else if (player.playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
    }
    private fun loadDanmaku(cid: Long, totalDurationMs: Long) {
        danmakuJob?.cancel()
        val views = headerAdapter.inlinePlayerViews() ?: return
        if (BiliClient.prefs.danmakuEnabled.not()) {
            views.danmakuView.setDanmakus(emptyList())
            views.danmakuView.visibility = View.GONE
            return
        }
        views.danmakuView.visibility = View.VISIBLE
        danmakuJob =
            scope.launch {
                val segmentCount =
                    ((totalDurationMs.coerceAtLeast(1L) / 360_000L) + 1L).toInt().coerceIn(1, 2)
                val all = ArrayList<Danmaku>()
                for (segment in 1..segmentCount) {
                    runCatching { BiliApi.dmSeg(cid, segment) }.getOrNull()?.let { all.addAll(it) }
                }
                withContext(Dispatchers.Main) {
                    val current = headerAdapter.inlinePlayerViews() ?: return@withContext
                    current.danmakuView.setDanmakus(all.sortedBy { it.timeMs })
                    current.danmakuView.notifySeek(engine?.currentPosition ?: 0L)
                }
            }
    }
    private fun danmakuConfig(): DanmakuConfig {
        val prefs = BiliClient.prefs
        return DanmakuConfig(
            enabled = prefs.danmakuEnabled,
            opacity = prefs.danmakuOpacity,
            textSizeSp = prefs.danmakuTextSizeSp,
            fontWeight = DanmakuFontWeight.fromPrefValue(prefs.danmakuFontWeight),
            strokeWidthPx = prefs.danmakuStrokeWidthPx,
            speedLevel = prefs.danmakuSpeed,
            area = prefs.danmakuArea,
            laneDensity = DanmakuLaneDensity.fromPrefValue(prefs.danmakuLaneDensity),
            showHighLikeIcon = prefs.danmakuShowHighLikeIcon,
        )
    }

    private fun showLoading(show: Boolean) {
        headerAdapter.inlinePlayerViews()?.loading?.visibility =
            if (show) View.VISIBLE else View.GONE
    }
    private fun createMediaSource(url: String): MediaSource {
        val dataSourceFactory =
            OkHttpDataSource.Factory(BiliClient.cdnOkHttp)
                .setDefaultRequestProperties(
                    mapOf(
                        "Referer" to "https://www.bilibili.com/",
                        "Origin" to "https://www.bilibili.com",
                    ),
                )
        return DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
    }

}

