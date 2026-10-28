package blbl.cat3399.feature.video

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.os.SystemClock
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.video.VideoPlayKind
import blbl.cat3399.core.api.video.VideoPlayRequest
import blbl.cat3399.core.api.video.VideoPlayStream
import blbl.cat3399.core.api.video.VideoTrack
import blbl.cat3399.core.model.Danmaku
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.core.ui.popup.PopupHandle
import blbl.cat3399.core.util.Format
import blbl.cat3399.feature.player.DashAudioKind
import blbl.cat3399.feature.player.DashSegmentBase
import blbl.cat3399.feature.player.DashTrackInfo
import blbl.cat3399.feature.player.Playable
import blbl.cat3399.feature.player.danmaku.DanmakuConfig
import blbl.cat3399.feature.player.engine.BlblPlayerEngine
import blbl.cat3399.feature.player.engine.ExoPlayerEngine
import blbl.cat3399.feature.player.engine.IjkPlayerEngine
import blbl.cat3399.feature.player.engine.PlaybackSource
import blbl.cat3399.feature.player.danmaku.DanmakuFontWeight
import blbl.cat3399.feature.player.danmaku.DanmakuLaneDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

internal class VideoInlinePlayerController(
    private val activity: BaseActivity,
    private val scope: CoroutineScope,
    private val headerAdapter: VideoDetailHeaderAdapter,
    private val onOpenFullscreen: () -> Unit,
    private val onOpenQuality: () -> Unit,
    private val onOpenPlaylist: () -> Unit,
    private val onOpenSettings: () -> Unit,
) {
    private var engine: BlblPlayerEngine? = null
    private var exoPlayer: ExoPlayer? = null
    private var currentPlayable: Playable? = null
    private var usingIjkFallback: Boolean = false
    private var playbackJob: Job? = null
    private var danmakuJob: Job? = null
    private var preparedCid: Long? = null
    private var durationMs: Long = 0L
    private var dragging = false
    private var attachedViews: InlinePlayerViews? = null
    private var attachedEngine: BlblPlayerEngine? = null
    private var ijkVideoWidth: Int = 0
    private var ijkVideoHeight: Int = 0
    private var lastBvid: String = ""
    private var lastAid: Long? = null
    private var lastCid: Long? = null
    private var lastAutoPlay: Boolean = true
    private var candidateUrls: List<String> = emptyList()
    private var releasedForBackground: Boolean = false
    private var backgroundResumePositionMs: Long = 0L
    private var backgroundResumeAutoPlay: Boolean = false
    private var firstFrameRendered: Boolean = false
    private var watchdogJob: Job? = null
    private var lastStartPositionMs: Long = 0L
    private var lastStream: VideoPlayStream? = null
    private var lastVideoTrack: VideoTrack? = null
    private var lastPlaybackQn: Int = InlinePlaybackQualityPolicy.FALLBACK_QN
    private var lastQualityLabel: String = InlinePlaybackQualityPolicy.FALLBACK_LABEL
    private var lastResolutionText: String = "未知"
    private var lastCodecText: String = "AVC"
    private var lastFrameRateText: String = "未知"
    private var lastHasHardwareAvc: Boolean = false
    private var actualDecoderName: String? = null
    private var lastPlaybackPositionMs: Long = 0L
    private var lastPlaybackAdvanceAtMs: Long = 0L
    private var stallRetryCount: Int = 0
    private var stallRetryJob: Job? = null
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
        qnOverride: Int? = null,
    ) {
        val safeCid = cid?.takeIf { it > 0L } ?: return
        lastBvid = bvid.trim()
        lastAid = aid?.takeIf { it > 0L }
        lastCid = safeCid
        lastAutoPlay = autoPlay
        firstFrameRendered = false
        lastPlaybackPositionMs = startPositionMs
        lastPlaybackAdvanceAtMs = SystemClock.elapsedRealtime()
        stallRetryCount = 0
        stallRetryJob?.cancel()
        stallRetryJob = null
        if (forceReload.not() && preparedCid == safeCid && engine != null && usingIjkFallback.not()) {
            if (autoPlay) engine?.play()
            return
        }
        val useIjkPrimary = BiliClient.prefs.playerEngineKind == AppPrefs.PLAYER_ENGINE_IJK
        usingIjkFallback = useIjkPrimary
        if (useIjkPrimary) ensureIjkEngine() else ensureExoEngine()
        attachViews()
        playbackJob?.cancel()
        preparedCid = null
        showLoading(true)
        val capability = InlinePlaybackQualityPolicy.detect()
        lastHasHardwareAvc = capability.hasHardwareAvcDecoder
        lastPlaybackQn = qnOverride ?: InlinePlaybackQualityPolicy.selectInlineQn(BiliClient.prefs.playerPreferredQn, capability)
        actualDecoderName = null
        lastQualityLabel = InlinePlaybackQualityPolicy.qualityLabel(lastPlaybackQn)
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
                                    qn = lastPlaybackQn,
                                    fnval = 4048,
                                    tryLook = false,
                                    preferCodec = "AVC",
                                ),
                            )
                        }
                    lastStream = stream
                    val dashVideo =
                        stream.dash?.videos?.firstOrNull { it.urls.isNotEmpty() && it.qn == lastPlaybackQn }
                            ?: stream.dash?.videos?.firstOrNull { it.urls.isNotEmpty() }
                    val dashAudio =
                        stream.dash?.audios?.firstOrNull { it.urls.isNotEmpty() && it.id == BiliClient.prefs.playerPreferredAudioId }
                            ?: stream.dash?.audios?.firstOrNull { it.urls.isNotEmpty() }
                    val progressive = stream.progressive.firstOrNull { it.urls.isNotEmpty() }
                    if (dashVideo == null && progressive == null) error("没有可用播放地址")
                    lastVideoTrack = dashVideo
                    lastQualityLabel =
                        stream.supportFormats.firstOrNull { it.quality == lastPlaybackQn }?.label?.trim()?.takeIf { it.isNotBlank() }
                            ?: InlinePlaybackQualityPolicy.qualityLabel(lastPlaybackQn)
                    val trackInfo = lastVideoTrack?.info
                    lastResolutionText =
                        if ((trackInfo?.width ?: 0) > 0 && (trackInfo?.height ?: 0) > 0) {
                            "${trackInfo?.width}x${trackInfo?.height}"
                        } else {
                            "未知"
                        }
                    lastCodecText = trackInfo?.codecs?.trim()?.takeIf { it.isNotBlank() }
                        ?: trackInfo?.mimeType?.trim()?.takeIf { it.isNotBlank() }
                        ?: "AVC"
                    lastFrameRateText = trackInfo?.frameRate?.trim()?.takeIf { it.isNotBlank() } ?: "未知"
                    durationMs = stream.durationMs ?: stream.dash?.durationMs ?: progressive?.lengthMs ?: 0L
                    candidateUrls =
                        if (dashVideo != null) {
                            dashVideo.urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        } else {
                            progressive?.urls.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        }
                    firstFrameRendered = false
                    lastStartPositionMs = startPositionMs
                    lastPlaybackPositionMs = startPositionMs
                    lastPlaybackAdvanceAtMs = SystemClock.elapsedRealtime()
                    stallRetryCount = 0
                    stallRetryJob?.cancel()
                    stallRetryJob = null
                    val initialUrl = candidateUrls.firstOrNull() ?: error("没有可用播放地址")
                    AppLog.i(
                        "InlinePlayer",
                        "prepare qn=$lastPlaybackQn candidates=${candidateUrls.size} audio=${dashAudio?.id ?: 0} dash=${dashVideo != null}/${dashAudio != null}",
                    )
                    val views = attachViews() ?: return@launch
                    val playable = buildInlinePlayable(dashVideo, dashAudio, progressive)
                        ?: error("无法构建内嵌播放源")
                    currentPlayable = playable
                    val activeEngine = engine ?: return@launch
                    activeEngine.setSource(
                        PlaybackSource.Vod(
                            playable = playable,
                            subtitle = null,
                            durationMs = durationMs,
                            initialPositionMs = startPositionMs,
                            seamlessQualitySwitchEnabled = false,
                        ),
                    )
                    activeEngine.prepare()
                    activeEngine.playWhenReady = autoPlay
                    if (autoPlay) activeEngine.play()
                    preparedCid = safeCid
                    views.playerView.visibility = if (exoPlayer != null) View.VISIBLE else View.GONE
                    views.ijkSurface.visibility = if (engine is IjkPlayerEngine) View.VISIBLE else View.GONE
                    views.coverImage.visibility = View.VISIBLE
                    views.coverImage.bringToFront()
                    views.loading.bringToFront()
                    views.quickActions.bringToFront()
                    views.controls.bringToFront()
                    views.coverPlay.bringToFront()
                    views.quickActions.visibility = View.VISIBLE
                    views.controls.visibility = View.VISIBLE
                    updateOsdLabels(views)
                    views.coverPlay.visibility = View.GONE
                    views.danmakuView.visibility =
                        if (BiliClient.prefs.danmakuEnabled) View.VISIBLE else View.GONE
                    loadDanmaku(safeCid, durationMs)
                    scheduleFirstFrameRetry(autoPlay)
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

    fun toggleOsd(): Boolean {
        val views = attachedViews ?: return false
        val visible = views.controls.visibility == View.VISIBLE || views.quickActions.visibility == View.VISIBLE
        val target = if (visible) View.GONE else View.VISIBLE
        views.controls.visibility = target
        views.quickActions.visibility = target
        return true
    }

    fun adjustVolume(delta: Float): Boolean {
        val player = engine ?: return false
        player.volume = (player.volume + delta).coerceIn(0f, 1f)
        val views = attachedViews
        if (views != null) updateOsdLabels(views)
        AppToast.show(activity, "音量 ${(player.volume * 100f).roundToInt()}%")
        return true
    }

    fun currentPositionMs(): Long = engine?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun showVideoStats() {
        showVideoStatsPanel()
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
        val wasPlaying = engine?.isPlaying ?: lastAutoPlay
        prepare(lastBvid, lastAid, cid, autoPlay = wasPlaying, startPositionMs = position, forceReload = true)
    }

    fun pause() {
        engine?.pause()
        activity.window?.decorView?.removeCallbacks(progressRunnable)
    }

    fun onResume() {
        if (engine != null) {
            attachViews()
            scheduleProgress()
        }
    }

    fun releaseForBackground() {
        if (preparedCid == null && engine == null) return
        backgroundResumePositionMs = currentPositionMs()
        backgroundResumeAutoPlay = engine?.isPlaying == true
        releasedForBackground = true
        release()
    }

    fun resumeAfterBackground() {
        if (releasedForBackground.not()) return
        releasedForBackground = false
        val cid = lastCid ?: return
        if (cid <= 0L || lastBvid.isBlank()) return
        prepare(
            bvid = lastBvid,
            aid = lastAid,
            cid = cid,
            autoPlay = backgroundResumeAutoPlay,
            startPositionMs = backgroundResumePositionMs,
            forceReload = true,
        )
    }

    fun release() {
        playbackJob?.cancel()
        danmakuJob?.cancel()
        watchdogJob?.cancel()
        stallRetryJob?.cancel()
        stallRetryJob = null
        watchdogJob = null
        activity.window?.decorView?.removeCallbacks(progressRunnable)
        attachedViews?.playerView?.player = null
        engine?.release()
        engine = null
        preparedCid = null
        attachedViews = null
    }
    private fun ensureExoEngine(): ExoPlayerEngine {
        val existing = engine
        if (existing is ExoPlayerEngine) {
            exoPlayer = existing.exoPlayer
            return existing
        }
        releaseActiveEngine()
        val created = ExoPlayerEngine(activity)
        exoPlayer = created.exoPlayer
        created.exoPlayer.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    actualDecoderName = decoderName
                }
            },
        )
        installEngineListener(created)
        engine = created
        return created
    }

    private fun ensureIjkEngine(): IjkPlayerEngine {
        val existing = engine
        if (existing is IjkPlayerEngine) return existing
        releaseActiveEngine()
        val created = IjkPlayerEngine(activity, forceSoftwareDecode = true)
        installEngineListener(created)
        engine = created
        exoPlayer = null
        return created
    }

    private fun installEngineListener(target: BlblPlayerEngine) {
        target.addListener(
            object : BlblPlayerEngine.Listener {
                override fun onPlayerError(error: Throwable) {
                    AppLog.e("InlinePlayer", "playerError engine=${target.kind}", error)
                    if (target === engine && target is ExoPlayerEngine && usingIjkFallback.not()) {
                        switchToIjkFallback(target)
                    }
                }

                override fun onRenderedFirstFrame() {
                    if (target !== engine) return
                    firstFrameRendered = true
                    watchdogJob?.cancel()
                    val views = attachedViews ?: return
                    views.coverImage.visibility = View.GONE
                    views.loading.visibility = View.GONE
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateProgress()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateProgress()
                }

                override fun onPositionDiscontinuity(newPositionMs: Long) {
                    attachedViews?.danmakuView?.notifySeek(newPositionMs)
                }

                override fun onVideoSizeChanged(width: Int, height: Int) {
                    if (target !== engine || width <= 0 || height <= 0) return
                    ijkVideoWidth = width
                    ijkVideoHeight = height
                    attachedViews?.let { applyIjkAspect(it) }
                }
            },
        )
    }

    private fun switchToIjkFallback(from: ExoPlayerEngine) {
        if (usingIjkFallback) return
        val playable = currentPlayable as? Playable.Dash ?: return
        val cid = lastCid?.takeIf { it > 0L } ?: return
        val position = from.currentPosition.coerceAtLeast(0L)
        usingIjkFallback = true
        AppLog.w("InlinePlayer", "switch to IjkPlayer fallback position=$position")
        AppToast.show(activity, "硬解异常，切换 IjkPlayer 软解")
        releaseActiveEngine()
        val ijk = ensureIjkEngine()
        attachViews()
        ijk.setSource(
            PlaybackSource.Vod(
                playable = playable,
                subtitle = null,
                durationMs = durationMs,
                initialPositionMs = position,
                seamlessQualitySwitchEnabled = false,
            ),
        )
        ijk.prepare()
        ijk.playWhenReady = lastAutoPlay
        if (lastAutoPlay) ijk.play()
        preparedCid = cid
        lastPlaybackPositionMs = position
        lastPlaybackAdvanceAtMs = SystemClock.elapsedRealtime()
        stallRetryCount = 0
        scheduleProgress()
    }

    private fun releaseActiveEngine() {
        attachedViews?.playerView?.player = null
        (engine as? IjkPlayerEngine)?.setVideoSurface(null)
        engine?.release()
        engine = null
        exoPlayer = null
        attachedEngine = null
    }

    private fun restartCurrentPlayable(position: Long, autoPlay: Boolean) {
        val playable = currentPlayable ?: return
        val activeEngine = engine ?: return
        activeEngine.setSource(
            PlaybackSource.Vod(
                playable = playable,
                subtitle = null,
                durationMs = durationMs,
                initialPositionMs = position.coerceAtLeast(0L),
                seamlessQualitySwitchEnabled = false,
            ),
        )
        activeEngine.prepare()
        activeEngine.playWhenReady = autoPlay
        if (autoPlay) activeEngine.play()
        lastPlaybackPositionMs = position
        lastPlaybackAdvanceAtMs = SystemClock.elapsedRealtime()
    }

    private fun scheduleFirstFrameRetry(autoPlay: Boolean) {
        watchdogJob?.cancel()
        watchdogJob =
            scope.launch {
                delay(8_000L)
                if (firstFrameRendered) return@launch
                val activeEngine = engine ?: return@launch
                if (activeEngine is ExoPlayerEngine && usingIjkFallback.not()) {
                    switchToIjkFallback(activeEngine)
                } else {
                    restartCurrentPlayable(activeEngine.currentPosition, autoPlay)
                    scheduleFirstFrameRetry(autoPlay)
                }
            }
    }

    private fun attachViews(): InlinePlayerViews? {
        val views = headerAdapter.inlinePlayerViews() ?: return null
        if (attachedViews?.playerView === views.playerView && attachedEngine === engine) return views
        attachedViews = views
        attachedEngine = engine
        views.playerView.player = exoPlayer
        views.playerView.visibility = if (exoPlayer != null) View.VISIBLE else View.GONE
        views.ijkSurface.visibility = if (engine is IjkPlayerEngine) View.VISIBLE else View.GONE
        bindIjkSurface(views)
        views.btnPlay.setOnClickListener { toggle() }
        views.btnQuality.setOnClickListener { onOpenQuality() }
        views.btnFullscreen.setOnClickListener { onOpenFullscreen() }
        views.btnVolume.setOnClickListener { showVolumePanel() }
        views.btnSpeed.setOnClickListener { showSpeedPicker() }
        views.btnDanmaku.setOnClickListener { toggleDanmaku() }
        views.btnSettings.setOnClickListener { onOpenSettings() }
        views.btnPlaylist.setOnClickListener { onOpenPlaylist() }
        views.btnStats.setOnClickListener { showVideoStatsPanel() }
        views.quickActions.bringToFront()
        updateOsdLabels(views)
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
                    engine?.seekTo(target)
                    views.danmakuView.notifySeek(target)
                }
            },
        )
        views.danmakuView.setPositionProvider { engine?.currentPosition ?: 0L }
        views.danmakuView.setIsPlayingProvider { engine?.isPlaying == true }
        views.danmakuView.setPlaybackSpeedProvider { engine?.playbackSpeed ?: 1f }
        views.danmakuView.setConfigProvider { danmakuConfig() }
        return views
    }

    private fun bindIjkSurface(views: InlinePlayerViews) {
        val surfaceView = views.ijkSurface
        surfaceView.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    (engine as? IjkPlayerEngine)?.setVideoSurface(Surface(surface))
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    applyIjkAspect(views)
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    (engine as? IjkPlayerEngine)?.setVideoSurface(null)
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        if (surfaceView.isAvailable) {
            (engine as? IjkPlayerEngine)?.setVideoSurface(Surface(surfaceView.surfaceTexture))
            applyIjkAspect(views)
        }
    }

    private fun applyIjkAspect(views: InlinePlayerViews) {
        val viewWidth = views.ijkSurface.width
        val viewHeight = views.ijkSurface.height
        if (viewWidth <= 0 || viewHeight <= 0 || ijkVideoWidth <= 0 || ijkVideoHeight <= 0) return
        val scale = minOf(viewWidth.toFloat() / ijkVideoWidth, viewHeight.toFloat() / ijkVideoHeight)
        val dx = (viewWidth - ijkVideoWidth * scale) / 2f
        val dy = (viewHeight - ijkVideoHeight * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        views.ijkSurface.setTransform(matrix)
    }

    private fun scheduleProgress() {
        val decor = activity.window?.decorView ?: return
        decor.removeCallbacks(progressRunnable)
        decor.post(progressRunnable)
    }

    private fun updateProgress() {
        val player = engine ?: return
        val views = attachViews() ?: return
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
        val now = SystemClock.elapsedRealtime()
        val position = player.currentPosition.coerceAtLeast(0L)
        if (position > lastPlaybackPositionMs + 250L) {
            lastPlaybackPositionMs = position
            lastPlaybackAdvanceAtMs = now
            stallRetryCount = 0
        } else if (lastPlaybackAdvanceAtMs <= 0L) {
            lastPlaybackAdvanceAtMs = now
        }
        val stallThresholdMs = 15_000L
        val stalled =
            firstFrameRendered &&
                preparedCid != null &&
                playbackJob?.isActive != true &&
                dragging.not() &&
                player.playWhenReady &&
                player.playbackState == Player.STATE_BUFFERING &&
                now - lastPlaybackAdvanceAtMs >= stallThresholdMs
        if (stalled) scheduleStallRetry()
        views.coverPlay.visibility = if (player.isPlaying) View.GONE else View.VISIBLE
        views.loading.visibility =
            if (firstFrameRendered.not()) View.VISIBLE else if (player.playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
        updateOsdLabels(views)
    }

    private fun scheduleStallRetry() {
        if (stallRetryJob?.isActive == true) return
        if (stallRetryCount >= 4) return
        val activeEngine = engine ?: return
        val position = activeEngine.currentPosition.coerceAtLeast(0L)
        stallRetryCount += 1
        stallRetryJob =
            scope.launch {
                delay(800L)
                if (engine !== activeEngine) return@launch
                AppLog.w("InlinePlayer", "playback stalled position=$position retry=$stallRetryCount engine=${activeEngine.kind}")
                if (activeEngine is ExoPlayerEngine && stallRetryCount >= 2 && usingIjkFallback.not()) {
                    switchToIjkFallback(activeEngine)
                    return@launch
                }
                restartCurrentPlayable(position, lastAutoPlay)
                scheduleFirstFrameRetry(lastAutoPlay)
            }
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

    private fun updateOsdLabels(views: InlinePlayerViews) {
        val player = engine
        views.btnVolume.text = "音量 ${((player?.volume ?: 1f) * 100f).roundToInt()}%"
        views.btnSpeed.text = formatSpeed(player?.playbackSpeed ?: 1f)
        views.btnDanmaku.text = if (BiliClient.prefs.danmakuEnabled) "弹幕开" else "弹幕关"
        views.btnQuality.text =
            "画质 ${InlinePlaybackQualityPolicy.shortQualityLabel(lastQualityLabel)}"
    }

    private fun formatSpeed(speed: Float): String =
        if (speed % 1f == 0f) "${speed.toInt()}x" else String.format(Locale.US, "%.2fx", speed)

    fun toggleDanmaku(): Boolean {
        val enabled = BiliClient.prefs.danmakuEnabled.not()
        BiliClient.prefs.danmakuEnabled = enabled
        val views = headerAdapter.inlinePlayerViews() ?: return false
        if (enabled) {
            views.danmakuView.visibility = View.VISIBLE
            lastCid?.let { loadDanmaku(it, durationMs) }
        } else {
            danmakuJob?.cancel()
            danmakuJob = null
            views.danmakuView.setDanmakus(emptyList())
            views.danmakuView.visibility = View.GONE
        }
        updateOsdLabels(views)
        AppToast.show(activity, if (enabled) "弹幕已开启" else "弹幕已关闭")
        return true
    }

    private fun showVolumePanel() {
        val player = engine ?: return
        val initial = (player.volume.coerceIn(0f, 1f) * 100f).roundToInt()
        AppPopup.custom(
            context = activity,
            title = "音量",
            cancelable = true,
            actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
            preferredActionRole = PopupActionRole.POSITIVE,
        ) { dialogContext ->
            val box = LinearLayout(dialogContext).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = (20f * resources.displayMetrics.density).roundToInt()
                setPadding(pad, pad / 2, pad, pad / 2)
            }
            val label = TextView(dialogContext).apply {
                text = "音量 $initial%"
                textSize = 16f
                gravity = Gravity.CENTER
            }
            val seekBar = SeekBar(dialogContext).apply {
                max = 100
                progress = initial
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            player.volume = progress.coerceIn(0, 100) / 100f
                            label.text = "音量 ${progress.coerceIn(0, 100)}%"
                            attachedViews?.let { updateOsdLabels(it) }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                    },
                )
            }
            box.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            box.addView(seekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            box
        }
    }

    private fun showSpeedPicker() {
        val player = engine ?: return
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val current = player.playbackSpeed
        val checked = speeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.takeIf { it >= 0 } ?: 2
        AppPopup.singleChoice(
            context = activity,
            title = "播放倍速",
            items = speeds.map { formatSpeed(it) },
            checkedIndex = checked,
        ) { index, _ ->
            val speed = speeds.getOrNull(index) ?: return@singleChoice
            player.setPlaybackSpeed(speed)
            updateProgress()
            AppToast.show(activity, "播放速度：${formatSpeed(speed)}")
        }
    }

    private fun showVideoStatsPanel() {
        var statsText: TextView? = null
        val refresh = object : Runnable {
            override fun run() {
                val textView = statsText ?: return
                textView.text = buildVideoStatsText()
                textView.postDelayed(this, 500L)
            }
        }
        AppPopup.custom(
            context = activity,
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
                val pad = (20f * resources.displayMetrics.density).roundToInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                text = buildVideoStatsText()
                statsText = this
            }
        }
    }

    private fun buildVideoStatsText(): String {
        val player = engine
        val width = lastVideoTrack?.info?.width ?: 0
        val height = lastVideoTrack?.info?.height ?: 0
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else lastResolutionText
        val codec = lastCodecText.ifBlank { "未知" }
        val state = when (player?.playbackState) {
            Player.STATE_BUFFERING -> "缓冲中"
            Player.STATE_READY -> if (player.isPlaying) "播放中" else "就绪/暂停"
            Player.STATE_ENDED -> "播放结束"
            Player.STATE_IDLE -> "空闲"
            else -> "未知"
        }
        val total = player?.duration?.takeIf { it > 0L } ?: durationMs
        val current = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val buffered = player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
        val bufferedPercent =
            player?.bufferedPosition?.takeIf { it > 0L }?.let { bp ->
                val total = player.duration
                if (total > 0L) ((bp * 100L) / total).toInt().coerceIn(0, 100) else 0
            } ?: 0
        val host = candidateUrls.firstOrNull()
            ?.let { runCatching { java.net.URI(it).host }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: "未知"
        val hardware = if (lastHasHardwareAvc) "是" else "否"
        val speed = player?.playbackSpeed ?: 1f
        val volume = ((player?.volume ?: 1f) * 100f).roundToInt()
        return buildString {
            appendLine("分辨率：$resolution")
            appendLine("编码：$codec")
            appendLine("帧率：${lastFrameRateText}")
            appendLine("状态：$state")
            appendLine("当前/总时长：${Format.clock(current / 1000L)} / ${Format.clock(total / 1000L)}")
            appendLine("缓冲位置：${Format.clock(buffered / 1000L)}")
            appendLine("缓冲进度：$bufferedPercent%")
            appendLine("画质：${InlinePlaybackQualityPolicy.shortQualityLabel(lastQualityLabel)} (qn=$lastPlaybackQn)")
            appendLine("硬件 AVC：$hardware")
            appendLine("倍速：${formatSpeed(speed)}")
            appendLine("音量：$volume%")
            append("CDN：$host")
        }
    }

    private fun showLoading(show: Boolean) {
        headerAdapter.inlinePlayerViews()?.loading?.visibility =
            if (show) View.VISIBLE else View.GONE
    }
    private fun buildInlinePlayable(
        video: VideoTrack?,
        audio: blbl.cat3399.core.api.video.AudioTrack?,
        progressive: blbl.cat3399.core.api.video.VideoProgressiveStream?,
    ): Playable? {
        if (video != null) {
            val videoInfo = dashTrackInfoOf(video.info) ?: return null
            val videoUrls = video.urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (videoUrls.isEmpty()) return null
            val representation =
                Playable.DashVideoRepresentation(
                    videoUrl = videoUrls.first(),
                    videoUrlCandidates = videoUrls,
                    videoMediaRequestProfile = video.mediaRequestProfile,
                    videoTrackInfo = videoInfo,
                    qn = video.qn,
                    codecid = video.codecid,
                    isDolbyVision = video.isDolbyVision,
                )
            if (audio != null) {
                val audioInfo = dashTrackInfoOf(audio.info) ?: return null
                val audioUrls = audio.urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                if (audioUrls.isEmpty()) return null
                return Playable.Dash(
                    videoUrl = videoUrls.first(),
                    audioUrl = audioUrls.first(),
                    videoUrlCandidates = videoUrls,
                    audioUrlCandidates = audioUrls,
                    videoMediaRequestProfile = video.mediaRequestProfile,
                    audioMediaRequestProfile = audio.mediaRequestProfile,
                    videoTrackInfo = videoInfo,
                    audioTrackInfo = audioInfo,
                    qn = video.qn,
                    codecid = video.codecid,
                    audioId = audio.id,
                    audioKind =
                        when (audio.kind) {
                            blbl.cat3399.core.api.video.VideoAudioKind.DOLBY -> DashAudioKind.DOLBY
                            blbl.cat3399.core.api.video.VideoAudioKind.FLAC -> DashAudioKind.FLAC
                            else -> DashAudioKind.NORMAL
                        },
                    isDolbyVision = video.isDolbyVision,
                    videoRepresentations = listOf(representation),
                )
            }
            return Playable.VideoOnly(
                videoUrl = videoUrls.first(),
                videoUrlCandidates = videoUrls,
                videoMediaRequestProfile = video.mediaRequestProfile,
                qn = video.qn,
                codecid = video.codecid,
                isDolbyVision = video.isDolbyVision,
            )
        }
        if (progressive != null) {
            val urls = progressive.urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (urls.isNotEmpty()) {
                return Playable.Progressive(
                    url = urls.first(),
                    urlCandidates = urls,
                    mediaRequestProfile = progressive.mediaRequestProfile,
                )
            }
        }
        return null
    }

    private fun dashTrackInfoOf(info: blbl.cat3399.core.api.video.VideoTrackInfo): DashTrackInfo? {
        val base = info.segmentBase ?: return null
        return DashTrackInfo(
            mimeType = info.mimeType,
            codecs = info.codecs,
            bandwidth = info.bandwidth,
            width = info.width,
            height = info.height,
            frameRate = info.frameRate,
            segmentBase = DashSegmentBase(initialization = base.initialization, indexRange = base.indexRange),
        )
    }


}
