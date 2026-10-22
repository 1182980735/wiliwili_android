package blbl.cat3399.feature.video

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.api.video.VideoAudioKind
import blbl.cat3399.core.api.video.VideoDetail
import blbl.cat3399.core.api.video.VideoMediaRequestProfile
import blbl.cat3399.core.api.video.VideoPlayKind
import blbl.cat3399.core.api.video.VideoPlayRequest
import blbl.cat3399.core.api.video.VideoPlayStream
import blbl.cat3399.core.api.video.VideoTrackInfo
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.VideoTag
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.ActivityStackLimiter
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.GridSpanPolicy
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.core.ui.ThemeColor
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.core.ui.smoothScrollToPositionStart
import blbl.cat3399.core.util.parseBangumiRedirectUrl
import blbl.cat3399.core.util.Format
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupHandle
import blbl.cat3399.core.ui.popup.PopupModalSizing
import blbl.cat3399.databinding.ActivityVideoDetailBinding
import blbl.cat3399.databinding.IncludeVideoCommentImageViewerContentBinding
import blbl.cat3399.databinding.IncludeVideoCommentsPanelContentBinding
import blbl.cat3399.databinding.ViewVideoCommentsPopupBinding
import blbl.cat3399.feature.following.UpDetailActivity
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.feature.player.ArchiveTripleActionState
import blbl.cat3399.feature.player.AudioBalanceLevel
import blbl.cat3399.feature.player.DashAudioKind
import blbl.cat3399.feature.player.DashSegmentBase
import blbl.cat3399.feature.player.DashTrackInfo
import blbl.cat3399.feature.player.Playable
import blbl.cat3399.feature.player.PlayerActivity
import blbl.cat3399.feature.player.PlayerPlaylistContinuation
import blbl.cat3399.feature.player.PlayerPlaylistItem
import blbl.cat3399.feature.player.PlayerPlaylistStore
import blbl.cat3399.feature.player.VideoCardPlaylistPage
import blbl.cat3399.feature.player.buildFreshVideoCardPlaylistContinuation
import blbl.cat3399.feature.player.executeArchiveTripleAction
import blbl.cat3399.feature.player.parseMultiPagePlaylistFromDetailWithUiCards
import blbl.cat3399.feature.player.parseVideoCardsToPlaylistParsed
import blbl.cat3399.feature.player.parseUgcSeasonPlaylistFromDetailWithUiCards
import blbl.cat3399.feature.player.engine.BlblPlayerEngine
import blbl.cat3399.feature.player.engine.ExoPlayerEngine
import blbl.cat3399.feature.player.engine.PlaybackSource
import blbl.cat3399.feature.player.userMessage
import blbl.cat3399.feature.settings.SettingsActivity
import blbl.cat3399.feature.player.danmaku.DanmakuConfig
import blbl.cat3399.feature.player.danmaku.DanmakuFontWeight
import blbl.cat3399.feature.player.danmaku.DanmakuLaneDensity
import blbl.cat3399.feature.tag.TagDetailActivity
import blbl.cat3399.ui.MainActivity
import blbl.cat3399.feature.video.comment.VideoCommentImageViewerController
import blbl.cat3399.feature.video.comment.VideoCommentImageViewerViews
import blbl.cat3399.feature.video.comment.VideoCommentsPanelController
import blbl.cat3399.feature.video.comment.VideoCommentsPanelViews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityVideoDetailBinding

    private lateinit var headerAdapter: VideoDetailHeaderAdapter
    private lateinit var recommendAdapter: CompactVideoRowAdapter
    private lateinit var extraAdapter: CompactVideoRowAdapter
    private lateinit var concatAdapter: ConcatAdapter
    private var inlinePlayerController: VideoInlinePlayerController? = null

    private var loadJob: Job? = null
    private var requestToken: Int = 0

    private var bvid: String = ""
    private var cid: Long? = null
    private var aid: Long? = null

    private var ownerMid: Long? = null
    private var ownerName: String? = null
    private var ownerAvatar: String? = null
    private var coverUrl: String? = null
    private var title: String? = null
    private var desc: String? = null
    private var metaText: String? = null
    private var tabName: String? = null
    private var tabId: Int? = null
    private var tags: List<VideoTag> = emptyList()

    private var playlistToken: String? = null
    private var playlistIndex: Int? = null

    private var currentParts: List<PlayerPlaylistItem> = emptyList()
    private var currentPartsUiCards: List<blbl.cat3399.core.model.VideoCard> = emptyList()
    private var partsOrderReversed: Boolean = false
    private var currentUgcSeasonTitle: String? = null
    private var currentUgcSeasonItems: List<PlayerPlaylistItem> = emptyList()
    private var currentUgcSeasonUiCards: List<blbl.cat3399.core.model.VideoCard> = emptyList()
    private var currentUgcSeasonIndex: Int? = null
    private var currentUgcSeasonId: Long? = null
    private var currentUgcSeasonOwnerMid: Long? = null
    private var seasonOrderReversed: Boolean = false

    private var actionLiked: Boolean = false
    private var actionCoinCount: Int = 0
    private var actionFavored: Boolean = false
    private var detailLikeTotal: Long? = null
    private var detailCoinTotal: Long? = null
    private var detailFavTotal: Long? = null
    private var detailReplyTotal: Long? = null
    private var detailShareTotal: Long? = null

    private var likeActionJob: Job? = null
    private var coinActionJob: Job? = null
    private var favDialogJob: Job? = null
    private var favApplyJob: Job? = null
    private var tripleActionJob: Job? = null
    private var socialStateFetchJob: Job? = null
    private var socialStateFetchToken: Int = 0
    private var commentsPopupHandle: PopupHandle? = null
    private var commentsPopupController: VideoCommentsPanelController? = null
    private var commentsPopupImageViewerController: VideoCommentImageViewerController? = null
    private var commentsController: VideoCommentsPanelController? = null
    private var rightPanelTabIndex: Int = 0
    private var inlineRtHeld: Boolean = false
    private var uploadsLoaded: Boolean = false
    private var uploadsJob: Job? = null
    private var uploadsItems: List<blbl.cat3399.core.model.VideoCard> = emptyList()
    private var relatedRefreshJob: Job? = null
    private var commentTotalCount: Int = 0
    private var uploadsTotalCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStackLimiter.register(group = ACTIVITY_STACK_GROUP, activity = this, maxDepth = ACTIVITY_STACK_MAX_DEPTH)

        bvid = intent.getStringExtra(EXTRA_BVID).orEmpty().trim()
        cid = intent.getLongExtra(EXTRA_CID, -1L).takeIf { it > 0L }
        aid = intent.getLongExtra(EXTRA_AID, -1L).takeIf { it > 0L }
        playlistToken = intent.getStringExtra(EXTRA_PLAYLIST_TOKEN)?.trim()?.takeIf { it.isNotBlank() }
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, -1).takeIf { it >= 0 }

        title = intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf { it.isNotBlank() }
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL)?.trim()?.takeIf { it.isNotBlank() }
        ownerName = intent.getStringExtra(EXTRA_OWNER_NAME)?.trim()?.takeIf { it.isNotBlank() }
        ownerAvatar = intent.getStringExtra(EXTRA_OWNER_AVATAR)?.trim()?.takeIf { it.isNotBlank() }
        ownerMid = intent.getLongExtra(EXTRA_OWNER_MID, -1L).takeIf { it > 0L }

        if (bvid.isBlank() && aid == null) {
            AppToast.show(this, "缺少 bvid/aid")
            finish()
            return
        }

        if (savedInstanceState != null) {
            initUi()
            load()
            return
        }

        showLoadingUi()
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)

        lifecycleScope.launch {
            try {
                val detail = fetchVideoDetail()
                val bangumiRedirect = parseBangumiRedirectUrl(detail.redirectUrl.orEmpty())
                if (bangumiRedirect != null) {
                    startActivity(
                        Intent(this@VideoDetailActivity, BangumiDetailActivity::class.java)
                            .putExtra(BangumiDetailActivity.EXTRA_IS_DRAMA, false)
                            .apply {
                                bangumiRedirect.epId?.let { epId ->
                                    putExtra(BangumiDetailActivity.EXTRA_EP_ID, epId)
                                    putExtra(BangumiDetailActivity.EXTRA_CONTINUE_EP_ID, epId)
                                }
                                bangumiRedirect.seasonId?.let { seasonId ->
                                    putExtra(BangumiDetailActivity.EXTRA_SEASON_ID, seasonId)
                                }
                            },
                    )
                    finish()
                    return@launch
                }

                initUi()
                load(prefetchedDetail = detail)
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                AppToast.show(this@VideoDetailActivity, t.message ?: "加载失败")
                finish()
            }
        }
    }

    override fun onPause() {
        inlinePlayerController?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        inlinePlayerController?.onResume()
        if (!this::binding.isInitialized) return
        if (this::headerAdapter.isInitialized) headerAdapter.invalidateSizing()
        if (this::recommendAdapter.isInitialized) recommendAdapter.invalidateSizing()
    }

    override fun onDestroy() {
        inlinePlayerController?.release()
        inlinePlayerController = null
        commentsPopupHandle?.dismiss()
        commentsPopupController?.release()
        commentsPopupHandle = null
        commentsPopupController = null
        commentsPopupImageViewerController = null
        commentsController?.release()
        commentsController = null
        uploadsJob?.cancel()
        uploadsJob = null
        relatedRefreshJob?.cancel()
        relatedRefreshJob = null
        ActivityStackLimiter.unregister(group = ACTIVITY_STACK_GROUP, activity = this)
        super.onDestroy()
    }

    private fun showLoadingUi() {
        val root =
            FrameLayout(this).apply {
                ThemeColor.applyBackground(
                    view = this,
                    attr = R.attr.blblPageBackdrop,
                    fallbackRes = R.color.blbl_bg,
                )
                addView(
                    ProgressBar(this@VideoDetailActivity),
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = Gravity.CENTER
                    },
                )
            }
        setContentView(root)
    }

    private fun initUi() {
        binding = ActivityVideoDetailBinding.inflate(layoutInflater.cloneInUserScale(this))
        setContentView(binding.root)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)

        binding.btnBack.setOnClickListener { finish() }
        binding.footerPrevTab.text = "画质"
        binding.footerNextTab.text = "快退"
        binding.footerRefresh.text = "快进"
        binding.footerBack.text = "B 返回"
        binding.footerConfirm.text = "A 确认"
        binding.footerPrevTab.setOnClickListener { openQualityPicker() }
        binding.footerNextTab.setOnClickListener { seekInlineBy(-10L) }
        binding.footerRefresh.setOnClickListener { seekInlineBy(10L) }
        binding.footerSearch.setOnClickListener { openSearchFromDetail() }
        binding.footerBack.setOnClickListener { handleDetailBackAction() }
        binding.footerConfirm.setOnClickListener { confirmFocusedDetailItem() }
        binding.footerPrevTab.visibility = View.VISIBLE
        binding.footerNextTab.visibility = View.VISIBLE
        binding.footerRefresh.visibility = View.VISIBLE
        binding.footerSearch.visibility = View.GONE
        headerAdapter =
            VideoDetailHeaderAdapter(
                onPlayClick = { toggleInlinePlayback() },
                onUpClick = { openUpDetail() },
                onTabClick = { tab -> openRegionTab(tab) },
                onTagClick = { tag -> onVideoTagClick(tag) },
                onLikeClick = { onLikeButtonClicked() },
                onLikeLongPress = { onLikeButtonLongPressed() },
                onCoinClick = { onCoinButtonClicked() },
                onFavClick = { onFavButtonClicked() },
                onCommentsClick = { openCommentsPopup() },
                onSecondaryClick = { /* video detail has no secondary action yet */ },
                onShareClick = { shareVideo() },
                onUpCardFocused = { smoothScrollHeaderToTop() },
                onPartsOrderClick = {
                    partsOrderReversed = !partsOrderReversed
                    applyHeader()
                },
                onSeasonOrderClick = {
                    seasonOrderReversed = !seasonOrderReversed
                    applyHeader()
                },
                onPartCardClick = { card, _ -> playPartByUiCard(card) },
                onSeasonCardClick = { card, _ -> playSeasonByUiCard(card) },
            )

        val recommendActionController =
            VideoCardActionController(
                context = this,
                scope = lifecycleScope,
                dismissBehavior = VideoCardDismissBehavior.LocalNotInterested,
                onOpenDetail = { _, pos -> openRecommendDetail(pos) },
                onOpenUp = { card -> openUpDetailForCard(card) },
                onCardRemoved = { stableKey ->
                    val removed = recommendAdapter.removeByStableKey(stableKey)
                    if (removed >= 0) binding.recyclerRecommendations.requestFocus()
                },
            )
        val extraActionController =
            VideoCardActionController(
                context = this,
                scope = lifecycleScope,
                dismissBehavior = VideoCardDismissBehavior.LocalNotInterested,
                onOpenDetail = { _, pos ->
                    openVideoDetailFromCards(
                        cards = extraAdapter.snapshot(),
                        position = pos,
                        source = "VideoDetail:$bvid:extra",
                    )
                },
                onOpenUp = { card -> openUpDetailForCard(card) },
                onCardRemoved = { stableKey ->
                    val removed = extraAdapter.removeByStableKey(stableKey)
                    if (removed >= 0) binding.recyclerDetailExtra.requestFocus()
                },
            )
        recommendAdapter =
            CompactVideoRowAdapter(
                onClick = { card, pos ->
                    val nextBvid = card.bvid.trim()
                    if (nextBvid.isBlank()) return@CompactVideoRowAdapter
                    openRecommendDetail(pos)
                },
                actionDelegate = recommendActionController,
            )

        extraAdapter =
            CompactVideoRowAdapter(
                onClick = { _, pos ->
                    openVideoDetailFromCards(
                        cards = extraAdapter.snapshot(),
                        position = pos,
                        source = "VideoDetail:$bvid:extra",
                    )
                },
                actionDelegate = extraActionController,
            )

        binding.recycler.adapter = headerAdapter
        binding.recycler.layoutManager = LinearLayoutManager(this)
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.recyclerRecommendations.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecommendations.adapter = recommendAdapter

        binding.recyclerDetailExtra.layoutManager = LinearLayoutManager(this)
        binding.recyclerDetailExtra.adapter = extraAdapter
        inlinePlayerController =
            VideoInlinePlayerController(
                activity = this,
                scope = lifecycleScope,
                headerAdapter = headerAdapter,
                onOpenFullscreen = { playCurrentFromHeader() },
                onOpenQuality = { openQualityPicker() },
                onOpenPlaylist = { openInlinePlaylistPicker() },
                onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            )
        setupRightPanel()
        binding.swipeRefresh.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            val availableHeight = bottom - binding.swipeRefresh.top
            if (availableHeight > 0) {
                headerAdapter.setCoverHeightPx((availableHeight * 0.62f).toInt())
            }
        }

        binding.swipeRefresh.setOnRefreshListener { load() }

        headerAdapter.update(
            title = title,
            metaText = metaText,
            desc = desc,
            coverUrl = coverUrl,
            usePosterCover = false,
            upName = ownerName,
            upAvatar = ownerAvatar,
            tabName = tabName,
            tags = tags,
            primaryButtonText = "播放",
            secondaryButtonText = null,
            showActions = true,
            actionLiked = actionLiked,
            actionCoinCount = actionCoinCount,
            actionFavored = actionFavored,
            actionLikeTotal = detailLikeTotal,
            actionCoinTotal = detailCoinTotal,
            actionFavTotal = detailFavTotal,
            actionReplyTotal = detailReplyTotal,
            actionShareTotal = detailShareTotal,
            showCommentsAction = aid?.takeIf { it > 0L } != null,
            partsHeaderText = buildPartsHeaderText(cardsCount = currentPartsUiCards.size),
            partsCards = partsCardsForDisplay(),
            partsSelectedKey = resolvePartsSelectedKey(),
            partsOrderReversed = partsOrderReversed,
            seasonHeaderText = buildSeasonHeaderText(),
            seasonCards = seasonCardsForDisplay(),
            seasonSelectedKey = resolveSeasonSelectedKey(),
            seasonOrderReversed = seasonOrderReversed,
            recommendHeaderText = null,
        )

        binding.recycler.post { headerAdapter.requestFocusPlay() }
    }

    private fun setupRightPanel() {
        binding.detailTabComment.setOnClickListener { selectRightPanelTab(0) }
        binding.detailTabParts.setOnClickListener { selectRightPanelTab(1) }
        binding.detailTabRecommend.setOnClickListener { selectRightPanelTab(2) }
        binding.detailTabUpload.setOnClickListener { selectRightPanelTab(3) }

        val commentViews = IncludeVideoCommentsPanelContentBinding.bind(binding.commentsPanelContent)
        commentsController =
            VideoCommentsPanelController(
                context = this,
                scope = lifecycleScope,
                views =
                    VideoCommentsPanelViews(
                        sortRow = commentViews.rowCommentSort,
                        sortHot = commentViews.chipCommentSortHot,
                        sortNew = commentViews.chipCommentSortNew,
                        comments = commentViews.recyclerComments,
                        thread = commentViews.recyclerCommentThread,
                        hint = commentViews.tvCommentsHint,
                    ),
                oidProvider = { aid?.takeIf { it > 0L } },
                upMidProvider = { ownerMid ?: 0L },
                imageViewer = null,
                isActive = { isRightCommentsActive() },
                onTotalCountChanged = { count ->
                    commentTotalCount = count
                    updateRightTabTitles()
                },
            )
        selectRightPanelTab(0)
        updateRightTabTitles()
    }

    private fun isRightCommentsActive(): Boolean {
        if (isFinishing) return false
        if (isDestroyed) return false
        return rightPanelTabIndex == 0
    }

    private fun showRightPanelTab(index: Int) {
        rightPanelTabIndex = index
        updateDetailSplitForTab(index)
        binding.commentsPanelContent.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.recyclerRecommendations.visibility = if (index == 2) View.VISIBLE else View.GONE
        var showExtra = index == 1
        if (index == 3) showExtra = true
        binding.recyclerDetailExtra.visibility = if (showExtra) View.VISIBLE else View.GONE
        binding.tvDetailPlaceholder.visibility = View.GONE
        when (index) {
            0 -> {
                binding.tvDetailPlaceholder.text = ""
                commentsController?.showRoot()
                commentsController?.ensureLoaded()
            }

            1 -> {
                val cards = collectionCards()
                extraAdapter.submit(cards)
                updateRightTabTitles()
                if (cards.isEmpty()) {
                    binding.tvDetailPlaceholder.text = "暂无分集或分P内容"
                    binding.tvDetailPlaceholder.visibility = View.VISIBLE
                }
            }

            2 -> Unit

            else -> {
                if (uploadsLoaded) {
                    extraAdapter.submit(uploadsItems)
                    if (uploadsItems.isEmpty()) {
                        binding.tvDetailPlaceholder.text = "暂无投稿"
                        binding.tvDetailPlaceholder.visibility = View.VISIBLE
                    }
                } else {
                    binding.tvDetailPlaceholder.text = "投稿加载中…"
                    binding.tvDetailPlaceholder.visibility = View.VISIBLE
                    loadOwnerUploads()
                }
            }
        }
    }

    private fun selectRightPanelTab(index: Int) {
        if (this::binding.isInitialized.not()) return
        val safeIndex = index.coerceIn(0, 3)
        val texts = listOf(
            binding.detailTabCommentText,
            binding.detailTabPartsText,
            binding.detailTabRecommendText,
            binding.detailTabUploadText,
        )
        val indicators = listOf(
            binding.detailTabCommentIndicator,
            binding.detailTabPartsIndicator,
            binding.detailTabRecommendIndicator,
            binding.detailTabUploadIndicator,
        )
        val accent = ThemeColor.resolve(this, R.attr.blblAccent, R.color.blbl_purple)
        val secondary = ThemeColor.resolve(this, R.attr.blblOnPageBackdropSecondary, R.color.blbl_text_secondary_wiliwili_light)
        texts.forEachIndexed { i, text ->
            text.setTextColor(if (i == safeIndex) accent else secondary)
        }
        indicators.forEachIndexed { i, indicator ->
            indicator.visibility = if (i == safeIndex) View.VISIBLE else View.GONE
        }
        showRightPanelTab(safeIndex)
    }

    private fun selectRightTabRelative(direction: Int) {
        val next = (rightPanelTabIndex + direction + 4) % 4
        selectRightPanelTab(next)
    }

    private fun updateDetailSplitForTab(index: Int) {
        val params =
            binding.detailSplitGuideline.layoutParams
                as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        params.guidePercent = 0.66f
        binding.detailSplitGuideline.layoutParams = params
        binding.root.requestLayout()
        binding.swipeRefresh.requestLayout()
        binding.rightPanel.requestLayout()
    }

    private fun updateRightTabTitles() {
        if (this::binding.isInitialized.not()) return
        val episodeCount =
            if (currentUgcSeasonUiCards.isNotEmpty()) {
                currentUgcSeasonUiCards.size
            } else {
                currentPartsUiCards.size
            }
        val recommendCount = if (this::recommendAdapter.isInitialized) recommendAdapter.itemCount else 0
        binding.detailTabCommentText.text = tabLabel("评论", commentTotalCount)
        binding.detailTabPartsText.text = tabLabel("分集", episodeCount)
        binding.detailTabRecommendText.text = tabLabel("推荐", recommendCount)
        binding.detailTabUploadText.text = tabLabel("投稿", uploadsTotalCount)
    }

    private fun tabLabel(base: String, count: Int): String {
        if (count <= 0) return base
        return "$base(${compactCount(count)})"
    }

    private fun compactCount(count: Int): String =
        when {
            count >= 100_000_000 -> "${count / 100_000_000}亿"
            count >= 10_000 -> "${count / 10_000}万"
            else -> count.toString()
        }

    private fun collectionCards(): List<blbl.cat3399.core.model.VideoCard> {
        if (currentUgcSeasonUiCards.isNotEmpty()) return currentUgcSeasonUiCards
        return currentPartsUiCards
    }

    private fun loadOwnerUploads() {
        if (uploadsLoaded) return
        if (uploadsJob?.isActive == true) return
        val mid = ownerMid?.takeIf { it > 0L }
        if (mid == null) {
            binding.tvDetailPlaceholder.text = "未获取到 UP 主信息"
            binding.tvDetailPlaceholder.visibility = View.VISIBLE
            return
        }
        uploadsJob =
            lifecycleScope.launch {
                try {
                    val page = withContext(Dispatchers.IO) {
                        BiliApi.spaceArcSearchPage(mid = mid, pn = 1, ps = 30, order = "pubdate")
                    }
                    if (isFinishing) return@launch
                    if (isDestroyed) return@launch
                    uploadsItems = page.items
                    uploadsLoaded = true
                    uploadsTotalCount = page.total
                    updateRightTabTitles()
                    if (rightPanelTabIndex == 3) {
                        extraAdapter.submit(uploadsItems)
                        if (uploadsItems.isEmpty()) {
                            binding.tvDetailPlaceholder.text = "暂无投稿"
                            binding.tvDetailPlaceholder.visibility = View.VISIBLE
                        } else {
                            binding.tvDetailPlaceholder.visibility = View.GONE
                        }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.w("VideoDetail", "load owner uploads failed mid=$mid", t)
                    if (rightPanelTabIndex == 3) {
                        binding.tvDetailPlaceholder.text = "投稿加载失败"
                        binding.tvDetailPlaceholder.visibility = View.VISIBLE
                    }
                }
            }
    }

    private fun refreshFocusedDetailWindow() {
        val focused = currentFocus
        val inRightPanel = focused != null && FocusTreeUtils.isDescendantOf(focused, binding.rightPanel)
        val inFooter = focused != null && FocusTreeUtils.isDescendantOf(focused, binding.footerBar)
        if (inRightPanel.not() && inFooter.not()) {
            load()
            return
        }
        when (rightPanelTabIndex) {
            0 -> commentsController?.refresh()
            1 -> {
                extraAdapter.submit(collectionCards())
                binding.tvDetailPlaceholder.visibility = View.GONE
            }
            2 -> refreshRelatedVideos()
            else -> {
                uploadsLoaded = false
                loadOwnerUploads()
            }
        }
    }

    private fun refreshRelatedVideos() {
        val safeBvid = bvid.trim()
        if (safeBvid.isBlank() && aid == null) return
        relatedRefreshJob?.cancel()
        relatedRefreshJob = lifecycleScope.launch {
            try {
                val related = withContext(Dispatchers.IO) {
                    BiliApi.archiveRelated(bvid = safeBvid, aid = aid)
                }
                if (isFinishing) return@launch
                if (isDestroyed) return@launch
                recommendAdapter.submit(related)
                updateRightTabTitles()
                AppLog.d("VideoDetail", "related refreshed count=${related.size}")
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("VideoDetail", "refresh related failed bvid=$safeBvid", t)
            }
        }
    }

    private fun openSearchFromDetail() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SEARCH, true),
        )
    }

    private fun handleDetailBackAction(): Boolean {
        if (commentsController?.handleBack() == true) return true
        if (rightPanelTabIndex != 0) {
            selectRightPanelTab(0)
            return true
        }
        val focused = currentFocus
        val inRightPanel = focused != null && FocusTreeUtils.isDescendantOf(focused, binding.rightPanel)
        if (inRightPanel) {
            headerAdapter.requestFocusPlay()
            return true
        }
        finish()
        return true
    }

    private fun confirmFocusedDetailItem(): Boolean {
        val focused = currentFocus ?: return false
        if (focused === binding.footerConfirm) return true
        if (focused.performClick()) return true
        return dispatchDpadCenterToFocused()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val editing = currentFocus is EditText
        if (editing.not()) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
                inlineRtHeld = event.action == KeyEvent.ACTION_DOWN
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (inlineRtHeld && (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
                    val delta = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) 0.05f else -0.05f
                    inlinePlayerController?.adjustVolume(delta)
                    return true
                }
                if (event.repeatCount == 0) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            if (shouldSeekWithShoulder()) seekInlineByWiliwili(-1) else selectRightTabRelative(-1)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            if (shouldSeekWithShoulder()) seekInlineByWiliwili(1) else selectRightTabRelative(1)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_L2 -> {
                            selectRightTabRelative(-1)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_R2 -> {
                            selectRightTabRelative(1)
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_START -> {
                            openQualityPicker()
                            return true
                        }
                        KeyEvent.KEYCODE_X,
                        KeyEvent.KEYCODE_BUTTON_X,
                        -> {
                            val focused = currentFocus
                            val inRightPanel = focused != null && FocusTreeUtils.isDescendantOf(focused, binding.rightPanel)
                            val handled =
                                if (inRightPanel && rightPanelTabIndex == 0 && commentsController?.toggleSortFromRemote() == true) {
                                    true
                                } else {
                                    inlinePlayerController?.toggleDanmaku() == true
                                }
                            return handled
                        }
                        KeyEvent.KEYCODE_Y,
                        KeyEvent.KEYCODE_BUTTON_Y,
                        -> {
                            inlinePlayerController?.toggleOsd()
                            return true
                        }
                        KeyEvent.KEYCODE_BUTTON_SELECT -> {
                            inlinePlayerController?.showVideoStats()
                            return true
                        }
                        KeyEvent.KEYCODE_A,
                        KeyEvent.KEYCODE_BUTTON_A,
                        -> {
                            confirmFocusedDetailItem()
                            return true
                        }
                        KeyEvent.KEYCODE_B,
                        KeyEvent.KEYCODE_BUTTON_B,
                        -> {
                            handleDetailBackAction()
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun shouldSeekWithShoulder(): Boolean {
        val focused = currentFocus ?: return false
        return FocusTreeUtils.isDescendantOf(focused, binding.recycler)
    }

    private fun seekInlineByWiliwili(direction: Int) {
        val currentSec = (inlinePlayerController?.currentPositionMs() ?: 0L) / 1000L
        val rangeSec =
            when {
                currentSec <= 30L -> 5L
                currentSec <= 60L -> 15L
                currentSec <= 300L -> 30L
                currentSec <= 1_200L -> 60L
                else -> (currentSec / 20L).coerceAtLeast(1L)
            }
        seekInlineBy(direction * rangeSec)
    }

    private fun smoothScrollHeaderToTop() {
        if (!this::binding.isInitialized) return
        if (!binding.recycler.canScrollVertically(-1)) return
        binding.recycler.smoothScrollToPositionStart(0)
    }

    private suspend fun fetchVideoDetail(): VideoDetail =
        withContext(Dispatchers.IO) {
            if (bvid.isNotBlank()) {
                BiliApi.videoDetail(bvid)
            } else {
                BiliApi.videoDetail(aid ?: 0L)
            }
        }

    private fun load(prefetchedDetail: VideoDetail? = null) {
        val codeToken = ++requestToken
        loadJob?.cancel()
        loadJob = null
        tripleActionJob?.cancel()
        tripleActionJob = null

        binding.swipeRefresh.isRefreshing = true

        loadJob =
            lifecycleScope.launch {
                try {
                    val detail = prefetchedDetail ?: fetchVideoDetail()
                    if (codeToken != requestToken) return@launch

                    val bangumiRedirect = parseBangumiRedirectUrl(detail.redirectUrl.orEmpty())
                    if (bangumiRedirect != null) {
                        startActivity(
                            Intent(this@VideoDetailActivity, BangumiDetailActivity::class.java)
                                .putExtra(BangumiDetailActivity.EXTRA_IS_DRAMA, false)
                                .apply {
                                    bangumiRedirect.epId?.let { epId ->
                                        putExtra(BangumiDetailActivity.EXTRA_EP_ID, epId)
                                        putExtra(BangumiDetailActivity.EXTRA_CONTINUE_EP_ID, epId)
                                    }
                                    bangumiRedirect.seasonId?.let { seasonId ->
                                        putExtra(BangumiDetailActivity.EXTRA_SEASON_ID, seasonId)
                                    }
                                },
                        )
                        finish()
                        return@launch
                    }

                    val resolvedBvid = detail.bvid.trim().takeIf { it.isNotBlank() } ?: bvid
                    val resolvedAid = detail.aid?.takeIf { it > 0L } ?: aid
                    val resolvedCid = cid ?: detail.cid?.takeIf { it > 0L }

                    bvid = resolvedBvid
                    aid = resolvedAid
                    cid = resolvedCid

                    title = detail.title?.trim()?.takeIf { it.isNotBlank() } ?: title
                    desc = detail.description?.trim().orEmpty()
                    coverUrl = detail.coverUrl?.trim()?.takeIf { it.isNotBlank() } ?: coverUrl
                    tabId = detail.tabId ?: tabId
                    tabName = detail.tabName?.trim()?.takeIf { it.isNotBlank() }

                    actionLiked = false
                    actionCoinCount = 0
                    actionFavored = false
                    detailLikeTotal = detail.stat.like
                    detailCoinTotal = detail.stat.coin
                    detailFavTotal = detail.stat.favorite
                    detailReplyTotal = detail.stat.reply
                    detailShareTotal = detail.stat.share ?: 0L

                    run {
                        val pubDate = detail.pubDateSec
                        val viewCount = detail.stat.view
                        val likeCount = detail.stat.like
                        val coinCount = detail.stat.coin
                        val favCount = detail.stat.favorite
                        metaText =
                            buildList {
                                pubDate?.let { Format.pubDateText(it) }.takeIf { !it.isNullOrBlank() }?.let(::add)
                                viewCount?.let { "${Format.count(it)}观看" }?.let(::add)
                                likeCount?.let { "${Format.count(it)}赞" }?.let(::add)
                                coinCount?.let { "${Format.count(it)}投币" }?.let(::add)
                                favCount?.let { "${Format.count(it)}收藏" }?.let(::add)
                            }.joinToString(" · ")
                                .trim()
                                .takeIf { it.isNotBlank() }
                    }

                    val owner = detail.owner
                    ownerMid = owner?.mid?.takeIf { it > 0L } ?: ownerMid
                    ownerName = owner?.name?.trim()?.takeIf { it.isNotBlank() } ?: ownerName
                    ownerAvatar = owner?.avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: ownerAvatar

                    run {
                        val parsed = parseMultiPagePlaylistFromDetailWithUiCards(detail, bvid = resolvedBvid, aid = resolvedAid)
                        currentParts = parsed.items
                        currentPartsUiCards = parsed.uiCards
                    }

                    val ugcSeason = detail.ugcSeason
                    currentUgcSeasonTitle = ugcSeason?.title?.trim()?.takeIf { it.isNotBlank() }
                    currentUgcSeasonItems = emptyList()
                    currentUgcSeasonUiCards = emptyList()
                    currentUgcSeasonIndex = null
                    currentUgcSeasonId = ugcSeason?.id?.takeIf { it > 0L }
                    currentUgcSeasonOwnerMid = ugcSeason?.ownerMid?.takeIf { it > 0L } ?: ownerMid
                    if (ugcSeason != null) {
                        val parsedFromView = parseUgcSeasonPlaylistFromDetailWithUiCards(ugcSeason)
                        if (parsedFromView.items.isNotEmpty()) {
                            currentUgcSeasonItems = parsedFromView.items
                            currentUgcSeasonUiCards = parsedFromView.uiCards
                        } else {
                            val seasonId = ugcSeason.id?.takeIf { it > 0L }
                            val mid = ugcSeason.ownerMid?.takeIf { it > 0L } ?: ownerMid
                            if (seasonId != null && mid != null) {
                                val archivesPage =
                                    withContext(Dispatchers.IO) {
                                        runCatching { BiliApi.ugcSeasonArchives(mid = mid, seasonId = seasonId, pageSize = 200) }.getOrNull()
                                    }
                                if (archivesPage != null) {
                                    val parsedFromApi = parseVideoCardsToPlaylistParsed(archivesPage.items, ::defaultVideoCardPlaylistItem)
                                    currentUgcSeasonItems = parsedFromApi.items
                                    currentUgcSeasonUiCards = parsedFromApi.uiCards
                                }
                            }
                        }
                    }
                    currentUgcSeasonIndex =
                        pickPlaylistIndexForCurrentMedia(
                            list = currentUgcSeasonItems,
                            bvid = resolvedBvid,
                            aid = resolvedAid,
                            cid = resolvedCid,
                        ).takeIf { it >= 0 }

                    val (fetchedTags, related) =
                        coroutineScope {
                            val tagsDeferred =
                                async(Dispatchers.IO) {
                                    runCatching { BiliApi.viewTags(bvid = resolvedBvid, aid = resolvedAid, cid = resolvedCid) }
                                        .getOrDefault(emptyList())
                                }
                            val relatedDeferred =
                                async(Dispatchers.IO) {
                                    runCatching { BiliApi.archiveRelated(bvid = resolvedBvid, aid = resolvedAid) }
                                        .getOrDefault(emptyList())
                                }
                            tagsDeferred.await() to relatedDeferred.await()
                        }
                    if (codeToken != requestToken) return@launch

                    tags = fetchedTags
                    applyHeader()
                    inlinePlayerController?.prepare(
                        bvid = resolvedBvid,
                        aid = resolvedAid,
                        cid = resolvedCid,
                        autoPlay = true,
                    )
                    recommendAdapter.submit(related)
                    updateRightTabTitles()
                    if (rightPanelTabIndex == 0) commentsController?.ensureLoaded()
                    loadOwnerUploads()
                    refreshActionButtonStatesFromServer(bvid = resolvedBvid, aid = resolvedAid)
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    AppLog.e("VideoDetail", "load failed bvid=$bvid aid=$aid", t)
                    AppToast.show(this@VideoDetailActivity, t.message ?: "加载失败")
                } finally {
                    if (codeToken == requestToken) binding.swipeRefresh.isRefreshing = false
                }
            }
    }

    private fun pickPlaylistIndexForCurrentMedia(list: List<PlayerPlaylistItem>, bvid: String, aid: Long?, cid: Long?): Int {
        val safeBvid = bvid.trim()
        if (cid != null && cid > 0) {
            val byCid = list.indexOfFirst { it.cid == cid }
            if (byCid >= 0) return byCid
        }
        if (aid != null && aid > 0) {
            val byAid = list.indexOfFirst { it.aid == aid }
            if (byAid >= 0) return byAid
        }
        if (safeBvid.isNotBlank()) {
            val byBvid = list.indexOfFirst { it.bvid == safeBvid }
            if (byBvid >= 0) return byBvid
        }
        return -1
    }

    private fun openQualityPicker() {
        val capability = InlinePlaybackQualityPolicy.detect()
        if (capability.hasHardwareAvcDecoder.not()) {
            AppPopup.message(
                context = this,
                title = "内嵌画质",
                message = "未检测到硬件 AVC 解码器，内嵌播放已回退到 480P AVC。",
                positiveText = "知道了",
            )
            return
        }
        val options = InlinePlaybackQualityPolicy.qualityOptions
        val current = BiliClient.prefs.playerPreferredQn
        AppPopup.singleChoice(
            context = this,
            title = "选择画质",
            items = options.map { it.second },
            checkedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0),
            onRestoreFocus = { binding.recycler.post { headerAdapter.requestFocusPlay() }; true },
        ) { index, label ->
            val qn = options.getOrNull(index)?.first ?: return@singleChoice
            BiliClient.prefs.playerPreferredQn = qn
            inlinePlayerController?.reloadForQuality()
            AppToast.show(this, "默认画质：$label")
        }
    }

    private fun openInlinePlaylistPicker() {
        val useSeason = currentUgcSeasonUiCards.isNotEmpty()
        val cards = if (useSeason) currentUgcSeasonUiCards else currentPartsUiCards
        if (cards.isEmpty()) {
            AppToast.show(this, "当前视频没有分P或合集")
            return
        }
        val currentIndex = cards.indexOfFirst { card ->
            (cid != null && card.cid == cid) || (bvid.isNotBlank() && card.bvid.trim() == bvid)
        }.coerceAtLeast(0)
        val labels = cards.mapIndexed { index, card ->
            val duration = if (card.durationSec > 0) " · ${Format.clock(card.durationSec.toLong())}" else ""
            "${index + 1}. ${card.title.trim().ifBlank { "未命名" }}$duration"
        }
        AppPopup.singleChoice(
            context = this,
            title = if (useSeason) "合集播放列表" else "分P播放列表",
            items = labels,
            checkedIndex = currentIndex,
        ) { index, _ ->
            val card = cards.getOrNull(index) ?: return@singleChoice
            switchInlinePlaylistCard(card = card, useSeason = useSeason)
        }
    }

    private fun switchInlinePlaylistCard(card: blbl.cat3399.core.model.VideoCard, useSeason: Boolean) {
        val safeBvid = card.bvid.trim()
        val safeCid = card.cid?.takeIf { it > 0L }
        if (safeBvid.isBlank() && card.aid == null) {
            AppToast.show(this, "该条目缺少播放信息")
            return
        }
        if (safeCid == null) {
            AppToast.show(this, "该条目缺少 cid")
            return
        }
        val items = if (useSeason) currentUgcSeasonItems else currentParts
        val cardIndex = items.indexOfFirst { item ->
            item.cid == safeCid || (safeBvid.isNotBlank() && item.bvid.trim() == safeBvid)
        }
        val rawIndex = cardIndex.coerceAtLeast(0)
        val picked = items.getOrNull(cardIndex)
        bvid = picked?.bvid?.trim()?.takeIf { it.isNotBlank() } ?: safeBvid
        cid = picked?.cid?.takeIf { it > 0L } ?: safeCid
        aid = picked?.aid?.takeIf { it > 0L } ?: card.aid?.takeIf { it > 0L }
        title = card.title.trim().takeIf { it.isNotBlank() } ?: title
        coverUrl = card.coverUrl.trim().takeIf { it.isNotBlank() } ?: coverUrl
        if (useSeason) currentUgcSeasonIndex = cardIndex.takeIf { it >= 0 }
        if (cardIndex >= 0 && items.isNotEmpty()) {
            playlistToken =
                PlayerPlaylistStore.put(
                    items = items,
                    index = rawIndex,
                    source = if (useSeason) "VideoDetail:inline:ugc_season:$bvid" else "VideoDetail:inline:multi_page:$bvid",
                    uiCards = if (useSeason) currentUgcSeasonUiCards else currentPartsUiCards,
                    continuation =
                        if (useSeason) {
                            buildUgcSeasonPlaylistContinuation(currentUgcSeasonUiCards)
                        } else {
                            null
                        },
                )
            playlistIndex = rawIndex
        } else {
            playlistToken = null
            playlistIndex = null
        }
        applyHeader()
        inlinePlayerController?.prepare(
            bvid = bvid,
            aid = aid,
            cid = cid,
            autoPlay = true,
        )
        AppToast.show(this, "内嵌播放：${card.title.trim().ifBlank { "已切换" }}")
    }

    private fun startPlayerAtOffset(deltaSec: Long) {
        val safeBvid = bvid.trim()
        if (safeBvid.isBlank() && aid == null) {
            AppToast.show(this, "缺少视频信息")
            return
        }
        val startMs = (deltaSec * 1000L).coerceAtLeast(0L)
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_BVID, safeBvid)
                .putExtra(PlayerActivity.EXTRA_CID, cid ?: -1L)
                .apply { aid?.let { putExtra(PlayerActivity.EXTRA_AID, it) } }
                .apply { if (startMs > 0L) putExtra(PlayerActivity.EXTRA_START_POSITION_MS, startMs) }
                .apply { playlistToken?.let { putExtra(PlayerActivity.EXTRA_PLAYLIST_TOKEN, it) } }
                .apply { playlistIndex?.let { putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, it) } },
        )
    }

    private fun toggleInlinePlayback() {
        inlinePlayerController?.toggle()
    }

    private fun seekInlineBy(deltaSec: Long) {
        val controller = inlinePlayerController
        if (controller == null) {
            startPlayerAtOffset(deltaSec)
        } else {
            controller.seekBy(deltaSec * 1000L)
        }
    }

    private fun shareVideo() {
        shareVideoLink(title = title, bvid = bvid, aid = aid)
    }

    private fun playCurrentFromHeader() {
        val safeBvid = bvid.trim()
        if (safeBvid.isBlank() && aid == null) {
            AppToast.show(this, "缺少 bvid")
            return
        }

        val startMs = inlinePlayerController?.currentPositionMs()?.takeIf { it > 0L }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_BVID, safeBvid)
                .putExtra(PlayerActivity.EXTRA_CID, cid ?: -1L)
                .apply { aid?.let { putExtra(PlayerActivity.EXTRA_AID, it) } }
                .apply { if (startMs != null) putExtra(PlayerActivity.EXTRA_START_POSITION_MS, startMs) }
                .apply { playlistToken?.let { putExtra(PlayerActivity.EXTRA_PLAYLIST_TOKEN, it) } }
                .apply { playlistIndex?.let { putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, it) } },
        )
    }

    private fun playPart(index: Int) {
        val list = currentParts
        if (list.isEmpty() || index !in list.indices) return
        val picked = list[index]
        val safeBvid = picked.bvid.trim()
        if (safeBvid.isBlank()) return

        val token =
            PlayerPlaylistStore.put(
                items = list,
                index = index,
                source = "VideoDetail:multi_page:$safeBvid",
                uiCards = currentPartsUiCards,
            )
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_BVID, safeBvid)
                .putExtra(PlayerActivity.EXTRA_CID, picked.cid ?: -1L)
                .apply { picked.aid?.let { putExtra(PlayerActivity.EXTRA_AID, it) } }
                .putExtra(PlayerActivity.EXTRA_PLAYLIST_TOKEN, token)
                .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index),
        )
    }

    private fun playSeasonItem(index: Int) {
        val list = currentUgcSeasonItems
        if (list.isEmpty() || index !in list.indices) return
        val picked = list[index]
        val safeBvid = picked.bvid.trim()
        if (safeBvid.isBlank()) return

        val token =
            PlayerPlaylistStore.put(
                items = list,
                index = index,
                source = "VideoDetail:ugc_season:$safeBvid",
                uiCards = currentUgcSeasonUiCards,
                continuation = buildUgcSeasonPlaylistContinuation(currentUgcSeasonUiCards),
            )
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_BVID, safeBvid)
                .putExtra(PlayerActivity.EXTRA_CID, picked.cid ?: -1L)
                .apply { picked.aid?.let { putExtra(PlayerActivity.EXTRA_AID, it) } }
                .putExtra(PlayerActivity.EXTRA_PLAYLIST_TOKEN, token)
                .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index),
        )
    }

    private fun openUpDetail() {
        val mid = ownerMid?.takeIf { it > 0L }
        if (mid == null) {
            AppToast.show(this, "未获取到 UP 主信息")
            return
        }
        startActivity(
            Intent(this, UpDetailActivity::class.java)
                .putExtra(UpDetailActivity.EXTRA_MID, mid)
                .apply {
                    ownerName?.takeIf { it.isNotBlank() }?.let { putExtra(UpDetailActivity.EXTRA_NAME, it) }
                    ownerAvatar?.takeIf { it.isNotBlank() }?.let { putExtra(UpDetailActivity.EXTRA_AVATAR, it) }
                },
        )
    }

    private fun openRecommendDetail(position: Int) {
        openVideoDetailFromCards(
            cards = recommendAdapter.snapshot(),
            position = position,
            source = "VideoDetail:$bvid:recommend",
        )
    }

    private fun openUpDetailForCard(card: blbl.cat3399.core.model.VideoCard) {
        val targetMid = card.ownerMid?.takeIf { it > 0L } ?: return
        startActivity(
            Intent(this, UpDetailActivity::class.java)
                .putExtra(UpDetailActivity.EXTRA_MID, targetMid)
                .apply {
                    card.ownerName.takeIf { it.isNotBlank() }?.let { putExtra(UpDetailActivity.EXTRA_NAME, it) }
                    card.ownerFace?.takeIf { it.isNotBlank() }?.let { putExtra(UpDetailActivity.EXTRA_AVATAR, it) }
                },
        )
    }

    private fun buildUgcSeasonPlaylistContinuation(
        cards: List<blbl.cat3399.core.model.VideoCard>,
    ): PlayerPlaylistContinuation? {
        val seasonId = currentUgcSeasonId?.takeIf { it > 0L } ?: return null
        val mid = currentUgcSeasonOwnerMid?.takeIf { it > 0L } ?: return null
        return buildFreshVideoCardPlaylistContinuation(
            seedCards = cards,
            nextCursor = 1,
            hasMore = cards.isNotEmpty(),
            playlistItemFactory = ::defaultVideoCardPlaylistItem,
        ) { pageNum ->
            val safePageNum = pageNum.coerceAtLeast(1)
            val archivesPage = BiliApi.ugcSeasonArchives(mid = mid, seasonId = seasonId, pageNum = safePageNum, pageSize = 200)
            val parsed = parseVideoCardsToPlaylistParsed(archivesPage.items, ::defaultVideoCardPlaylistItem)
            val totalCount = archivesPage.totalCount
            val hasMore = totalCount?.let { safePageNum * 200 < it } ?: (parsed.uiCards.size >= 200)
            VideoCardPlaylistPage(
                cards = parsed.uiCards,
                nextCursor = safePageNum + 1,
                hasMore = hasMore,
                canAdvance = hasMore && parsed.uiCards.isNotEmpty(),
            )
        }
    }

    private fun onVideoTagClick(tag: VideoTag) {
        val safeTagName = tag.tagName.trim().takeIf { it.isNotBlank() }
        if (safeTagName == null) return

        startActivity(
            Intent(this, TagDetailActivity::class.java)
                .putExtra(TagDetailActivity.EXTRA_TAG_NAME, safeTagName),
        )
    }

    private fun spanCountForWidth(): Int {
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return GridSpanPolicy.fixedSpanCountForWidthDp(
            widthDp = widthDp,
            overrideSpanCount = BiliClient.prefs.gridSpanCount,
        )
    }

    companion object {
        const val EXTRA_BVID: String = "bvid"
        const val EXTRA_CID: String = "cid"
        const val EXTRA_AID: String = "aid"
        const val EXTRA_TITLE: String = "title"
        const val EXTRA_COVER_URL: String = "cover_url"
        const val EXTRA_OWNER_NAME: String = "owner_name"
        const val EXTRA_OWNER_AVATAR: String = "owner_avatar"
        const val EXTRA_OWNER_MID: String = "owner_mid"
        const val EXTRA_PLAYLIST_TOKEN: String = "playlist_token"
        const val EXTRA_PLAYLIST_INDEX: String = "playlist_index"

        private const val ACTIVITY_STACK_GROUP: String = "video_detail_flow"
        private const val ACTIVITY_STACK_MAX_DEPTH: Int = 3
    }

    private fun applyHeader() {
        if (!this::headerAdapter.isInitialized) return
        headerAdapter.update(
            title = title,
            metaText = metaText,
            desc = desc,
            coverUrl = coverUrl,
            usePosterCover = false,
            upName = ownerName,
            upAvatar = ownerAvatar,
            tabName = tabName,
            tags = tags,
            primaryButtonText = "播放",
            secondaryButtonText = null,
            showActions = true,
            actionLiked = actionLiked,
            actionCoinCount = actionCoinCount,
            actionFavored = actionFavored,
            actionLikeTotal = detailLikeTotal,
            actionCoinTotal = detailCoinTotal,
            actionFavTotal = detailFavTotal,
            actionReplyTotal = detailReplyTotal,
            actionShareTotal = detailShareTotal,
            showCommentsAction = aid?.takeIf { it > 0L } != null,
            partsHeaderText = buildPartsHeaderText(cardsCount = currentPartsUiCards.size),
            partsCards = partsCardsForDisplay(),
            partsSelectedKey = resolvePartsSelectedKey(),
            partsOrderReversed = partsOrderReversed,
            seasonHeaderText = buildSeasonHeaderText(),
            seasonCards = seasonCardsForDisplay(),
            seasonSelectedKey = resolveSeasonSelectedKey(),
            seasonOrderReversed = seasonOrderReversed,
            recommendHeaderText = null,
        )
    }

    private fun partsCardsForDisplay(): List<blbl.cat3399.core.model.VideoCard> =
        if (partsOrderReversed) currentPartsUiCards.asReversed() else currentPartsUiCards

    private fun seasonCardsForDisplay(): List<blbl.cat3399.core.model.VideoCard> =
        if (seasonOrderReversed) currentUgcSeasonUiCards.asReversed() else currentUgcSeasonUiCards

    private fun buildPartsHeaderText(cardsCount: Int): String? {
        if (cardsCount <= 1) return null
        return "分P（$cardsCount）"
    }

    private fun buildSeasonHeaderText(): String? {
        val count = currentUgcSeasonUiCards.size
        if (count <= 1) return null
        val safeTitle = currentUgcSeasonTitle?.trim().takeIf { !it.isNullOrBlank() }
        return safeTitle?.let { "合集：$it" } ?: "合集（$count）"
    }

    private fun resolvePartsSelectedKey(): String? {
        val pickedCid = cid?.takeIf { it > 0L } ?: return null
        val card = currentPartsUiCards.firstOrNull { it.cid == pickedCid } ?: return null
        return cardStableKey(card)
    }

    private fun resolveSeasonSelectedKey(): String? {
        val idx = currentUgcSeasonIndex?.takeIf { it >= 0 } ?: return null
        val card = currentUgcSeasonUiCards.getOrNull(idx) ?: return null
        return cardStableKey(card)
    }

    private fun playPartByUiCard(card: blbl.cat3399.core.model.VideoCard) {
        val pickedCid = card.cid?.takeIf { it > 0L } ?: return
        val idx = currentPartsUiCards.indexOfFirst { it.cid == pickedCid }.takeIf { it >= 0 } ?: return
        playPart(idx)
    }

    private fun playSeasonByUiCard(card: blbl.cat3399.core.model.VideoCard) {
        val safeBvid = card.bvid.trim().takeIf { it.isNotBlank() }
        val safeCid = card.cid?.takeIf { it > 0L }
        val idx =
            currentUgcSeasonUiCards.indexOfFirst {
                (safeCid != null && it.cid == safeCid) ||
                    (safeBvid != null && it.bvid.trim() == safeBvid)
            }.takeIf { it >= 0 } ?: return
        playSeasonItem(idx)
    }

    private fun cardStableKey(card: blbl.cat3399.core.model.VideoCard): String =
        buildString {
            append(card.bvid)
            append('|')
            append(card.cid ?: -1L)
            append('|')
            append(card.aid ?: -1L)
            append('|')
            append(card.epId ?: -1L)
            append('|')
            append(card.title)
        }

    private fun openRegionTab(tab: String) {
        val safeRid = tabId?.takeIf { it > 0 }
        val safeTab = tab.trim().takeIf { it.isNotBlank() }
        if (safeRid == null || safeTab == null) {
            AppToast.show(this, "未获取到分区信息")
            return
        }
        startActivity(
            Intent(this, RegionDetailActivity::class.java)
                .putExtra(RegionDetailActivity.EXTRA_RID, safeRid)
                .putExtra(RegionDetailActivity.EXTRA_TITLE, safeTab),
        )
    }

    private fun openCommentsPopup() {
        if (commentsPopupHandle?.isShowing == true) {
            commentsPopupController?.focusRoot()
            return
        }

        val requestAid = aid?.takeIf { it > 0L }
        if (requestAid == null) {
            AppToast.show(this, getString(R.string.player_comment_no_aid))
            return
        }

        var popupBinding: ViewVideoCommentsPopupBinding?
        var imageViewer: VideoCommentImageViewerController? = null
        var controller: VideoCommentsPanelController? = null
        var handle: PopupHandle? = null
        var popupAttached = false

        handle =
            AppPopup.custom(
                context = this,
                title = getString(R.string.player_btn_comments),
                cancelable = true,
                actions = emptyList(),
                preferredActionRole = null,
                autoFocus = false,
                modalSizing =
                    PopupModalSizing(
                        widthRatio = 0.86f,
                        maxWidthDp = 1120f,
                        maxHeightRatio = 0.90f,
                    ),
                onModalAttached = {
                    popupAttached = true
                    controller?.showRoot()
                    controller?.ensureLoaded()
                    controller?.focusRoot()
                },
                onDismiss = {
                    popupAttached = false
                    controller?.release()
                    if (commentsPopupController === controller) commentsPopupController = null
                    if (commentsPopupImageViewerController === imageViewer) commentsPopupImageViewerController = null
                    if (commentsPopupHandle === handle) commentsPopupHandle = null
                    popupBinding = null
                },
                onBackPressed = {
                    controller?.handleBack() == true
                },
                content = { dialogContext ->
                    val b = ViewVideoCommentsPopupBinding.inflate(LayoutInflater.from(dialogContext))
                    val commentViews = IncludeVideoCommentsPanelContentBinding.bind(b.commentsPopupContent)
                    val imageViews = IncludeVideoCommentImageViewerContentBinding.bind(b.commentImageViewer)
                    // The popup card has a tighter width than the player side panel. Reserve
                    // extra horizontal focus-safe space for the shared 1.02x comment-card scale.
                    val focusSafeH = b.root.resources.getDimensionPixelSize(R.dimen.player_comments_recycler_padding_h)
                    listOf(commentViews.recyclerComments, commentViews.recyclerCommentThread).forEach { recycler ->
                        recycler.setPadding(
                            focusSafeH * 2,
                            recycler.paddingTop,
                            focusSafeH * 2,
                            recycler.paddingBottom + focusSafeH * 8,
                        )
                        recycler.addOnChildAttachStateChangeListener(
                            object : androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener {
                                override fun onChildViewAttachedToWindow(child: View) {
                                    child.setOnFocusChangeListener { focusedCard, hasFocus ->
                                        if (!hasFocus) return@setOnFocusChangeListener
                                        recycler.post {
                                            val visible = android.graphics.Rect()
                                            if (!recycler.getGlobalVisibleRect(visible)) return@post
                                            val location = IntArray(2)
                                            focusedCard.getLocationOnScreen(location)
                                            val cardTop = location[1]
                                            val cardBottom = cardTop + focusedCard.height
                                            val safeInset = focusSafeH
                                            val delta = when {
                                                cardBottom + safeInset > visible.bottom -> cardBottom + safeInset - visible.bottom
                                                cardTop - safeInset < visible.top -> cardTop - safeInset - visible.top
                                                else -> 0
                                            }
                                            if (delta != 0) recycler.scrollBy(0, delta)
                                        }
                                    }
                                }

                                override fun onChildViewDetachedFromWindow(child: View) {
                                    child.setOnFocusChangeListener(null)
                                }
                            },
                        )
                    }
                    popupBinding = b

                    imageViewer =
                        VideoCommentImageViewerController(
                            views =
                                VideoCommentImageViewerViews(
                                    container = b.commentImageViewer,
                                    image = imageViews.ivCommentImage,
                                    previous = imageViews.ivCommentImagePrev,
                                    next = imageViews.ivCommentImageNext,
                            ),
                            currentFocusProvider = { window?.decorView?.findFocus() },
                            fallbackFocusProvider = {
                                val currentBinding = popupBinding
                                if (currentBinding == null) {
                                    null
                                } else if (commentViews.recyclerCommentThread.visibility == View.VISIBLE) {
                                    commentViews.recyclerCommentThread
                                } else {
                                    commentViews.recyclerComments
                                }
                            },
                        )
                    b.commentImageViewer.setOnKeyListener { _, _, event ->
                        imageViewer?.dispatchKeyEvent(event) == true
                    }

                    controller =
                        VideoCommentsPanelController(
                            context = this@VideoDetailActivity,
                            scope = lifecycleScope,
                            views =
                                VideoCommentsPanelViews(
                                    sortRow = commentViews.rowCommentSort,
                                    sortHot = commentViews.chipCommentSortHot,
                                    sortNew = commentViews.chipCommentSortNew,
                                    comments = commentViews.recyclerComments,
                                    thread = commentViews.recyclerCommentThread,
                                    hint = commentViews.tvCommentsHint,
                                ),
                            oidProvider = { requestAid },
                            upMidProvider = { ownerMid ?: 0L },
                            imageViewer = imageViewer,
                            isActive = {
                                popupAttached &&
                                    !isFinishing &&
                                    !isDestroyed &&
                                    (handle == null || handle?.isShowing == true)
                            },
                        )

                    b.root
                },
            )

        commentsPopupHandle = handle
        commentsPopupController = controller
        commentsPopupImageViewerController = imageViewer

        if (handle == null) {
            controller?.release()
            commentsPopupController = null
            commentsPopupImageViewerController = null
        }
    }

    private fun refreshActionButtonStatesFromServer(
        bvid: String,
        aid: Long?,
    ) {
        if (!BiliClient.cookies.hasSessData()) return
        val requestBvid = bvid.trim().takeIf { it.isNotBlank() } ?: return
        val requestAid = aid?.takeIf { it > 0L }

        socialStateFetchJob?.cancel()
        val token = ++socialStateFetchToken
        val baselineLiked = actionLiked
        val baselineCoinCount = actionCoinCount
        val baselineFavored = actionFavored

        socialStateFetchJob =
            lifecycleScope.launch {
                try {
                    val (liked, coins, favoured) =
                        withContext(Dispatchers.IO) {
                            coroutineScope {
                                val likedJob =
                                    async {
                                        runCatching { BiliApi.archiveHasLike(bvid = requestBvid, aid = requestAid) }.getOrNull()
                                    }
                                val coinsJob =
                                    async {
                                        runCatching { BiliApi.archiveCoins(bvid = requestBvid, aid = requestAid) }.getOrNull()
                                    }
                                val favouredJob =
                                    async {
                                        runCatching { BiliApi.archiveFavoured(bvid = requestBvid, aid = requestAid) }.getOrNull()
                                    }
                                Triple(likedJob.await(), coinsJob.await(), favouredJob.await())
                            }
                        }

                    if (token != socialStateFetchToken) return@launch
                    if (this@VideoDetailActivity.bvid != requestBvid) return@launch
                    if (requestAid != null && this@VideoDetailActivity.aid != requestAid) return@launch

                    var changed = false
                    liked?.let { value ->
                        if (tripleActionJob?.isActive != true && likeActionJob?.isActive != true && actionLiked == baselineLiked) {
                            actionLiked = value
                            changed = true
                        }
                    }
                    coins?.let { value ->
                        if (tripleActionJob?.isActive != true && coinActionJob?.isActive != true && actionCoinCount == baselineCoinCount) {
                            actionCoinCount = value.coerceIn(0, 2)
                            changed = true
                        }
                    }
                    favoured?.let { value ->
                        if (
                            tripleActionJob?.isActive != true &&
                            favDialogJob?.isActive != true &&
                            favApplyJob?.isActive != true &&
                            actionFavored == baselineFavored
                        ) {
                            actionFavored = value
                            changed = true
                        }
                    }
                    if (changed) applyHeader()
                } finally {
                    if (token == socialStateFetchToken) socialStateFetchJob = null
                }
            }
    }

    private fun onLikeButtonLongPressed() {
        if (tripleActionJob?.isActive == true) return
        if (
            likeActionJob?.isActive == true ||
            coinActionJob?.isActive == true ||
            favDialogJob?.isActive == true ||
            favApplyJob?.isActive == true
        ) {
            AppToast.show(this, "操作进行中，请稍后")
            return
        }
        if (!BiliClient.cookies.hasSessData()) {
            AppToast.show(this, "请先登录后再一键三连")
            return
        }

        val requestBvid = bvid.trim().takeIf { it.isNotBlank() } ?: return
        val requestAid = aid?.takeIf { it > 0L }
        val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        val initialState =
            ArchiveTripleActionState(
                liked = actionLiked,
                coinCount = actionCoinCount,
                favored = actionFavored,
            )

        if (initialState.isSatisfied) {
            AppToast.show(this, "已完成三连")
            return
        }

        tripleActionJob =
            lifecycleScope.launch {
                try {
                    val result =
                        executeArchiveTripleAction(
                            bvid = requestBvid,
                            aid = requestAid,
                            selfMid = selfMid,
                            initialState = initialState,
                            isStillValid = {
                                this@VideoDetailActivity.bvid == requestBvid &&
                                    (requestAid == null || this@VideoDetailActivity.aid == requestAid)
                            },
                        )
                    actionLiked = result.state.liked
                    actionCoinCount = result.state.coinCount
                    actionFavored = result.state.favored
                    applyHeader()
                    AppToast.show(this@VideoDetailActivity, result.toastMessage())
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    AppToast.show(this@VideoDetailActivity, t.userMessage(defaultMessage = "操作失败"))
                } finally {
                    tripleActionJob = null
                    applyHeader()
                }
            }
    }

    private fun onLikeButtonClicked() {
        if (tripleActionJob?.isActive == true) return
        if (likeActionJob?.isActive == true) return
        if (!BiliClient.cookies.hasSessData()) {
            AppToast.show(this, "请先登录后再点赞")
            return
        }
        val requestBvid = bvid.trim().takeIf { it.isNotBlank() } ?: return
        val targetLike = !actionLiked

        likeActionJob =
            lifecycleScope.launch {
                try {
                    applyHeader()
                    BiliApi.archiveLike(bvid = requestBvid, aid = aid, like = targetLike)
                    if (bvid != requestBvid) return@launch
                    actionLiked = targetLike
                    AppToast.show(this@VideoDetailActivity, if (targetLike) "点赞成功" else "已取消赞")
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    val e = t as? BiliApiException
                    if (targetLike && e?.apiCode == 65006) {
                        if (bvid != requestBvid) return@launch
                        actionLiked = true
                        AppToast.show(this@VideoDetailActivity, "已点赞")
                    } else {
                        val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                        AppToast.show(this@VideoDetailActivity, msg)
                    }
                } finally {
                    likeActionJob = null
                    applyHeader()
                }
            }
    }

    private fun onCoinButtonClicked() {
        if (tripleActionJob?.isActive == true) return
        if (coinActionJob?.isActive == true) return
        if (actionCoinCount >= 2) return
        if (!BiliClient.cookies.hasSessData()) {
            AppToast.show(this, "请先登录后再投币")
            return
        }
        val requestBvid = bvid.trim().takeIf { it.isNotBlank() } ?: return

        coinActionJob =
            lifecycleScope.launch {
                try {
                    applyHeader()
                    BiliApi.coinAdd(bvid = requestBvid, aid = aid, multiply = 1, selectLike = false)
                    if (bvid != requestBvid) return@launch
                    actionCoinCount = (actionCoinCount + 1).coerceAtMost(2)
                    AppToast.show(this@VideoDetailActivity, "投币成功")
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    val e = t as? BiliApiException
                    if (e?.apiCode == 34005) {
                        if (bvid != requestBvid) return@launch
                        actionCoinCount = 2
                        AppToast.show(this@VideoDetailActivity, "已达到投币上限")
                    } else {
                        val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                        AppToast.show(this@VideoDetailActivity, msg)
                    }
                } finally {
                    coinActionJob = null
                    applyHeader()
                }
            }
    }

    private fun onFavButtonClicked() {
        if (tripleActionJob?.isActive == true) return
        if (favDialogJob?.isActive == true || favApplyJob?.isActive == true) return
        val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        if (selfMid == null) {
            AppToast.show(this, "请先登录后再收藏")
            return
        }
        val rid = aid?.takeIf { it > 0L }
        if (rid == null) {
            AppToast.show(this, "未获取到 aid，暂不支持收藏")
            return
        }
        val requestBvid = bvid
        val requestAid = rid

        favDialogJob =
            lifecycleScope.launch {
                try {
                    applyHeader()
                    val folders =
                        withContext(Dispatchers.IO) {
                            BiliApi.favFoldersWithState(upMid = selfMid, rid = requestAid)
                        }
                    if (bvid != requestBvid) return@launch
                    if (folders.isEmpty()) {
                        AppToast.show(this@VideoDetailActivity, "未获取到收藏夹")
                        return@launch
                    }

                    val initial = folders.filter { it.favState }.map { it.mediaId }.toSet()
                    actionFavored = initial.isNotEmpty()
                    applyHeader()

                    val labels =
                        folders.map { folder ->
                            if (folder.favState) "${folder.title}（已收藏）" else folder.title
                        }
                    AppPopup.singleChoice(
                        context = this@VideoDetailActivity,
                        title = "选择收藏夹",
                        items = labels,
                        checkedIndex = 0,
                        onDismiss = { binding.recycler.post { headerAdapter.requestFocusFav() } },
                    ) { index, _ ->
                        val picked = folders.getOrNull(index)
                        if (picked == null) {
                            binding.recycler.post { headerAdapter.requestFocusFav() }
                            return@singleChoice
                        }

                        val nextSelected = initial.toMutableSet()
                        if (nextSelected.contains(picked.mediaId)) nextSelected.remove(picked.mediaId) else nextSelected.add(picked.mediaId)
                        val add = (nextSelected - initial).toList()
                        val del = (initial - nextSelected).toList()
                        if (add.isNotEmpty() || del.isNotEmpty()) {
                            applyFavSelection(
                                requestBvid = requestBvid,
                                rid = requestAid,
                                add = add,
                                del = del,
                                selected = nextSelected.toSet(),
                            )
                        }
                        binding.recycler.post { headerAdapter.requestFocusFav() }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    val e = t as? BiliApiException
                    val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "加载收藏夹失败")
                    AppToast.show(this@VideoDetailActivity, msg)
                } finally {
                    favDialogJob = null
                    applyHeader()
                }
            }
    }

    private fun applyFavSelection(
        requestBvid: String,
        rid: Long,
        add: List<Long>,
        del: List<Long>,
        selected: Set<Long>,
    ) {
        if (favApplyJob?.isActive == true) return
        favApplyJob =
            lifecycleScope.launch {
                try {
                    applyHeader()
                    BiliApi.favResourceDeal(rid = rid, addMediaIds = add, delMediaIds = del)
                    if (bvid != requestBvid) return@launch
                    actionFavored = selected.isNotEmpty()
                    applyHeader()
                    AppToast.show(this@VideoDetailActivity, "收藏已更新")
                } catch (t: Throwable) {
                    if (t is CancellationException) return@launch
                    val e = t as? BiliApiException
                    val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                    AppToast.show(this@VideoDetailActivity, msg)
                } finally {
                    favApplyJob = null
                    applyHeader()
                }
            }
    }
}
