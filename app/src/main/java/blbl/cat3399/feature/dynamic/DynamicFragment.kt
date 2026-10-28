package blbl.cat3399.feature.dynamic

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.Following
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.core.ui.popup.PopupModalSizing
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.GridSpanPolicy
import blbl.cat3399.core.ui.UiScale
import blbl.cat3399.core.ui.enableDpadTabFocus
import blbl.cat3399.core.ui.parkFocusForDataSetReset
import blbl.cat3399.core.ui.requestFocusFirstItemOrSelfAfterRefresh
import blbl.cat3399.core.ui.uiScaler
import blbl.cat3399.core.ui.unparkFocusAfterDataSetReset
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.databinding.FragmentDynamicBinding
import blbl.cat3399.databinding.FragmentDynamicLoginBinding
import blbl.cat3399.feature.following.openUpDetailFromVideoCard
import blbl.cat3399.feature.login.QrLoginActivity
import blbl.cat3399.feature.player.VideoCardPlaylistPage
import blbl.cat3399.feature.video.VideoCardActionController
import blbl.cat3399.feature.video.VideoCardAdapter
import blbl.cat3399.feature.video.VideoCardDismissBehavior
import blbl.cat3399.feature.video.VideoCardVisibilityFilter
import blbl.cat3399.feature.video.buildPagedVideoCardPlaybackHandle
import blbl.cat3399.feature.video.defaultVideoCardPlaylistItem
import blbl.cat3399.feature.video.openVideoDetailFromPlaybackHandle
import blbl.cat3399.feature.video.openVideoFromPlaybackHandle
import com.google.android.material.tabs.TabLayout
import blbl.cat3399.feature.video.removeVideoCardAndRestoreFocus
import blbl.cat3399.ui.BackPressHandler
import blbl.cat3399.ui.RefreshKeyHandler
import blbl.cat3399.ui.SidebarFocusHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 动态页面Fragment，展示关注UP主的最新视频动态
 *
 * 左侧显示关注列表，右侧显示选中UP主的视频动态。
 * 实现了 [RefreshKeyHandler] 接口支持刷新键处理，[BackPressHandler] 接口支持返回键处理。
 */
class DynamicFragment : Fragment(), RefreshKeyHandler, BackPressHandler {
    /** 动态流继续游标，用于分页加载 */
    private data class FeedContinuationCursor(
        /** 选中的UP主ID */
        val selectedMid: Long,
        /** 当前页码 */
        val page: Int,
        /** 偏移量 */
        val offset: String?,
    )

    private var _bindingLogin: FragmentDynamicLoginBinding? = null
    private var _binding: FragmentDynamicBinding? = null

    private lateinit var followAdapter: FollowingAdapter
    private lateinit var videoAdapter: VideoCardAdapter
    private lateinit var articleAdapter: DynamicArticleAdapter

    private val loggedIn: Boolean
        get() = BiliClient.cookies.hasSessData()

    private val loadedStableKeys = HashSet<String>()
    private var isLoadingMore: Boolean = false
    private var endReached: Boolean = false
    private var nextOffset: String? = null
    private var nextPage: Int = 1
    private var requestToken: Int = 0
    private var dynamicGridController: DpadGridController? = null

    private var userMid: Long = 0L
    private var followPage: Int = 1
    private var followIsLoadingMore: Boolean = false
    private var followEndReached: Boolean = false
    private var followRequestToken: Int = 0
    private var currentFollowingListOrder: String = BiliClient.prefs.followingListOrder
    private var currentRecentUpdatePriorityEnabled: Boolean = BiliClient.prefs.dynamicFollowingRecentUpdateDotEnabled
    private var recentUpdatePriorityMids: Set<Long> = emptySet()
    private var recentUpdateOrderMids: List<Long> = emptyList()
    private var hasLoadedFollowingsOnce: Boolean = false
    private var followOrderRefreshToken: Int = 0
    private val consumingRecentUpdateMids = HashSet<Long>()
    private val consumedRecentUpdateMids = HashSet<Long>()
    private val loadedFollowMids = HashSet<Long>()
    private var followingListController: DpadGridController? = null

    private var pendingFocusFirstFeedCardAfterRefresh: Boolean = false

    private var currentContentTab: Int = 0
    private var articleNextOffset: String? = null
    private var articleNextPage: Int = 1
    private var articleEndReached: Boolean = false
    private var articleIsLoading: Boolean = false
    private val articleLoadedIds = HashSet<Long>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (!loggedIn) {
            _bindingLogin = FragmentDynamicLoginBinding.inflate(inflater, container, false)
            return _bindingLogin!!.root
        }
        _binding = FragmentDynamicBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!loggedIn) {
            _bindingLogin?.btnLogin?.setOnClickListener {
                startActivity(Intent(requireContext(), QrLoginActivity::class.java))
            }
            return
        }

        val binding = _binding ?: return

        followAdapter = FollowingAdapter(::onFollowingClicked)
        binding.recyclerFollowing.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFollowing.adapter = followAdapter
        binding.recyclerFollowing.clearOnScrollListeners()
        binding.recyclerFollowing.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    if (followIsLoadingMore || followEndReached) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val totalCount = followAdapter.itemCount
                    if (totalCount <= 0) return
                    if (totalCount - lastVisible - 1 <= 6) loadMoreFollowings()
                }
            },
        )
        followingListController?.release()
        followingListController =
            DpadGridController(
                recyclerView = binding.recyclerFollowing,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            // No header/tab in Dynamic. Keep focus inside the list.
                            return false
                        }

                        override fun onLeftEdge(): Boolean {
                            // Let MainActivity handle entering sidebar when appropriate.
                            return false
                        }

                        override fun onRightEdge() = Unit

                        override fun canLoadMore(): Boolean = !followEndReached

                        override fun loadMore() {
                            loadMoreFollowings()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { _binding != null && isResumed },
                        consumeRightEdge = false,
                    ),
            ).also { it.install() }
        applyUiMode()
        setupContentTabs(binding)

        val actionController =
            VideoCardActionController(
                context = requireContext(),
                scope = viewLifecycleOwner.lifecycleScope,
                dismissBehavior = VideoCardDismissBehavior.LocalNotInterested,
                onOpenDetail = { _, pos -> openDetail(pos) },
                onOpenUp = { card -> openUpDetailFromVideoCard(card) },
                onCardRemoved = { stableKey ->
                    _binding?.recyclerDynamic?.removeVideoCardAndRestoreFocus(
                        adapter = videoAdapter,
                        stableKey = stableKey,
                        isAlive = { _binding != null && isResumed },
                    )
                },
            )
        videoAdapter =
            VideoCardAdapter(
                onClick = { _, pos ->
                    requireContext().openVideoFromPlaybackHandle(
                        playbackHandle = playbackHandle(),
                        position = pos,
                        openDetailBeforePlay = BiliClient.prefs.playerOpenDetailBeforePlay,
                    )
                },
                onLongClick = { card, _ ->
                    openUpDetailFromVideoCard(card)
                    true
                },
                actionDelegate = actionController,
            )
        articleAdapter =
            DynamicArticleAdapter { article ->
                openArticlePopup(article)
            }
        binding.recyclerDynamic.setHasFixedSize(true)
        binding.recyclerDynamic.layoutManager = GridLayoutManager(requireContext(), spanCountForWidth())
        binding.recyclerDynamic.adapter = videoAdapter
        (binding.recyclerDynamic.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        dynamicGridController?.release()
        dynamicGridController =
            DpadGridController(
                recyclerView = binding.recyclerDynamic,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            return focusDynamicTab()
                        }

                        override fun onLeftEdge(): Boolean {
                            return focusSelectedFollowingIfAvailable()
                        }

                        override fun onRightEdge() = Unit

                        override fun canLoadMore(): Boolean =
                            if (currentContentTab == 0) endReached.not() else articleEndReached.not()

                        override fun loadMore() {
                            if (currentContentTab == 0) loadMoreFeed() else loadMoreArticles()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { _binding != null && isResumed },
                        enableCenterLongPressToLongClick = true,
                    ),
            ).also { it.install() }
        binding.recyclerDynamic.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    if (currentContentTab == 0) {
                        if ((isLoadingMore).or(endReached)) return
                    } else {
                        if ((articleIsLoading).or(articleEndReached)) return
                    }
                    if (binding.swipeRefresh.isRefreshing) return

                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = if (currentContentTab == 0) videoAdapter.itemCount else articleAdapter.itemCount
                    if (total <= 0) return

                    if (total - lastVisible - 1 <= 8) {
                        loadMoreFeed()
                    }
                }
            },
        )

        binding.swipeRefresh.setOnRefreshListener {
            pendingFocusFirstFeedCardAfterRefresh = true
            parkFocusForDataSetReset(followingListController, dynamicGridController)
            loadAll(resetFeed = true)
        }
        binding.swipeRefresh.isRefreshing = true
        loadAll(resetFeed = true)
    }

    private var baseFollowings: List<Following> = emptyList()
    private var followItems: List<FollowingAdapter.FollowingUi> = emptyList()
    private var selectedMid: Long = FollowingAdapter.MID_ALL

    private fun onFollowingClicked(following: FollowingAdapter.FollowingUi) {
        selectedMid = following.mid
        followAdapter.submit(followItems, selected = selectedMid)
        resetAndLoadFeed()
        maybeConsumeRecentUpdate(following)
    }

    private fun renderFollowings() {
        followItems = buildFollowingUiList()
        if (selectedMid != FollowingAdapter.MID_ALL && followItems.none { it.mid == selectedMid }) {
            selectedMid = FollowingAdapter.MID_ALL
        }
        followAdapter.submit(followItems, selected = selectedMid)
    }

    private fun buildFollowingUiList(): List<FollowingAdapter.FollowingUi> {
        val items = ArrayList<FollowingAdapter.FollowingUi>(baseFollowings.size + 1)
        items += FollowingAdapter.FollowingUi(FollowingAdapter.MID_ALL, "全部动态", null, isAll = true)
        val orderedFollowings = orderedFollowings()
        for (following in orderedFollowings) {
            val mid = following.mid
            val hasRecentUpdate = recentUpdatePriorityMids.contains(mid)
            items +=
                FollowingAdapter.FollowingUi(
                    mid = mid,
                    name = following.name,
                    avatarUrl = following.avatarUrl,
                    showRecentUpdateDot =
                        currentRecentUpdatePriorityEnabled &&
                            hasRecentUpdate &&
                            !consumedRecentUpdateMids.contains(mid),
                )
        }
        return items
    }

    private fun orderedFollowings(): List<Following> {
        if (baseFollowings.isEmpty()) return baseFollowings
        if (recentUpdateOrderMids.isEmpty()) return baseFollowings
        val orderIndex = recentUpdateOrderMids.withIndex().associate { it.value to it.index }
        val updated = baseFollowings.filter { orderIndex.containsKey(it.mid) }.sortedBy { orderIndex[it.mid] ?: Int.MAX_VALUE }
        val normal = baseFollowings.filter { orderIndex.containsKey(it.mid).not() }
        return updated + normal
    }

    private fun refreshFollowingOrder() {
        if (loggedIn.not()) return
        if (userMid <= 0L) return
        if (baseFollowings.isEmpty()) return
        if (_binding == null) return
        val token = ++followOrderRefreshToken
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mids = BiliApi.dynamicRecentUpdateUpMids()
                if (token != followOrderRefreshToken) return@launch
                if (_binding == null) return@launch
                recentUpdateOrderMids = mids
                recentUpdatePriorityMids = mids.toSet()
                renderFollowings()
                AppLog.d("Dynamic", "following order refreshed updated=" + mids.size)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("Dynamic", "refresh following order failed", t)
            }
        }
    }

    private fun maybeConsumeRecentUpdate(following: FollowingAdapter.FollowingUi) {
        if (!currentRecentUpdatePriorityEnabled) return
        if (following.isAll || !following.showRecentUpdateDot) return
        val mid = following.mid.takeIf { it > 0L } ?: return
        if (!consumingRecentUpdateMids.add(mid)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                BiliApi.consumeDynamicRecentUpdate(hostMid = mid)
                if (_binding == null) return@launch
                consumedRecentUpdateMids.add(mid)
                renderFollowings()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("Dynamic", "consume recent update failed mid=$mid", t)
            } finally {
                consumingRecentUpdateMids.remove(mid)
            }
        }
    }

    private fun loadAll(resetFeed: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val nav = BiliApi.nav()
                val data = nav.optJSONObject("data")
                val mid = data?.optLong("mid") ?: 0L
                val isLogin = data?.optBoolean("isLogin") ?: false
                AppLog.i("Dynamic", "nav isLogin=$isLogin mid=$mid")
                if (!isLogin || mid <= 0) {
                    _binding?.swipeRefresh?.isRefreshing = false
                    context?.let { AppToast.show(it, "登录态失效，请重新登录") }
                    return@launch
                }

                userMid = mid
                resetAndLoadFollowings()
                if (resetFeed) resetAndLoadFeed() else loadMoreFeed()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Dynamic", "load failed", t)
                _binding?.swipeRefresh?.isRefreshing = false
                context?.let { AppToast.show(it, "加载失败，可查看 Logcat(标签 BLBL)") }
            } finally {
                if (!resetFeed) _binding?.swipeRefresh?.isRefreshing = false
            }
        }
    }

    private suspend fun resetAndLoadFollowings() {
        if (_binding == null) return
        if (!this::followAdapter.isInitialized) return
        if (userMid <= 0) return

        loadedFollowMids.clear()
        currentFollowingListOrder = BiliClient.prefs.followingListOrder
        currentRecentUpdatePriorityEnabled = BiliClient.prefs.dynamicFollowingRecentUpdateDotEnabled
        recentUpdatePriorityMids = emptySet()
        recentUpdateOrderMids = emptyList()
        consumedRecentUpdateMids.clear()
        hasLoadedFollowingsOnce = false
        followPage = 1
        followEndReached = false
        followIsLoadingMore = false
        followingListController?.clearPendingFocusAfterLoadMore()
        val token = ++followRequestToken
        if (selectedMid == 0L) selectedMid = FollowingAdapter.MID_ALL
        baseFollowings = emptyList()
        renderFollowings()

        followIsLoadingMore = true
        try {
            val res =
                coroutineScope {
                    val recentUpdateDeferred =
                        async {
                            runCatching { BiliApi.dynamicRecentUpdateUpMids() }
                                .onFailure { AppLog.w("Dynamic", "load recent update up list failed", it) }
                                .getOrElse { emptyList() }
                        }
                    val followingPage =
                        BiliApi.followingsPage(vmid = userMid, pn = followPage, ps = 50, order = currentFollowingListOrder)
                    val recentMids = recentUpdateDeferred.await()
                    recentUpdateOrderMids = recentMids
                    recentUpdatePriorityMids = recentMids.toSet()
                    followingPage
                }
            if (token != followRequestToken) return

            val filtered = res.items.filter { loadedFollowMids.add(it.mid) }
            if (filtered.isEmpty()) {
                followEndReached = true
                context?.let { AppToast.show(it, "暂无关注") }
                return
            }

            baseFollowings = filtered
            hasLoadedFollowingsOnce = true
            renderFollowings()
            followPage += 1
            followEndReached = !res.hasMore
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLog.e("Dynamic", "load followings failed page=$followPage", t)
            context?.let { AppToast.show(it, "关注列表加载失败，可查看 Logcat(标签 BLBL)") }
        } finally {
            if (token == followRequestToken) {
                followIsLoadingMore = false
            }
        }
    }

    private fun loadMoreFollowings() {
        if (followIsLoadingMore || followEndReached) return
        if (userMid <= 0) return
        val token = followRequestToken
        val targetPage = followPage.coerceAtLeast(1)
        followIsLoadingMore = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = BiliApi.followingsPage(vmid = userMid, pn = targetPage, ps = 50, order = currentFollowingListOrder)
                if (token != followRequestToken) return@launch

                val filtered = res.items.filter { loadedFollowMids.add(it.mid) }
                if (filtered.isNotEmpty()) {
                    baseFollowings = baseFollowings + filtered
                    renderFollowings()
                }
                followPage = targetPage + 1
                followEndReached = !res.hasMore
                _binding?.let { b ->
                    b.recyclerFollowing.postIfAlive(isAlive = { _binding === b && isResumed }) {
                        followingListController?.consumePendingFocusAfterLoadMore()
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Dynamic", "load followings failed page=$targetPage", t)
            } finally {
                if (token == followRequestToken) followIsLoadingMore = false
            }
        }
    }

    private fun resetAndLoadFeed() {
        dynamicGridController?.clearPendingFocusAfterLoadMore()
        loadedStableKeys.clear()
        nextOffset = null
        nextPage = 1
        endReached = false
        isLoadingMore = false
        articleLoadedIds.clear()
        articleNextOffset = null
        articleNextPage = 1
        articleEndReached = false
        articleIsLoading = false
        requestToken++
        if (this::videoAdapter.isInitialized) videoAdapter.submit(emptyList())
        if (this::articleAdapter.isInitialized) articleAdapter.submit(emptyList())
        _binding?.swipeRefresh?.isRefreshing = true
        if (currentContentTab == 0) loadMoreFeed() else loadMoreArticles()
    }

    private fun resetAndLoadArticles() {
        articleLoadedIds.clear()
        articleNextOffset = null
        articleNextPage = 1
        articleEndReached = false
        articleIsLoading = false
        requestToken++
        articleAdapter.submit(emptyList())
        _binding?.swipeRefresh?.isRefreshing = true
        loadMoreArticles()
    }

    private fun loadMoreArticles() {
        if (articleIsLoading or articleEndReached) return
        if (selectedMid == 0L) {
            articleEndReached = true
            _binding?.swipeRefresh?.isRefreshing = false
            return
        }
        val token = requestToken
        articleIsLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hostMid = if (selectedMid == FollowingAdapter.MID_ALL) 0L else selectedMid
                val page =
                    BiliApi.dynamicArticles(
                        hostMid = hostMid,
                        offset = articleNextOffset,
                        page = articleNextPage,
                    )
                if (token != requestToken) return@launch
                val fresh = page.items.filter { articleLoadedIds.add(it.id) }
                articleAdapter.append(fresh)
                articleNextOffset = page.nextOffset
                articleNextPage += 1
                articleEndReached = page.nextOffset == null
                _binding?.let { b ->
                    b.recyclerDynamic.postIfAlive(isAlive = { _binding === b && isResumed }) {
                        dynamicGridController?.consumePendingFocusAfterLoadMore()
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Dynamic", "load articles failed mid=$selectedMid", t)
                context?.let { AppToast.show(it, "专栏加载失败，可查看 Logcat(标签 BLBL)") }
            } finally {
                if (token == requestToken) _binding?.swipeRefresh?.isRefreshing = false
                articleIsLoading = false
            }
        }
    }
    private fun loadMoreFeed() {
        if (currentContentTab == 1) {
            loadMoreArticles()
            return
        }
        if (isLoadingMore || endReached) return
        if (selectedMid == 0L) {
            endReached = true
            _binding?.swipeRefresh?.isRefreshing = false
            return
        }
        val token = requestToken
        isLoadingMore = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (selectedMid == FollowingAdapter.MID_ALL) {
                    var currentOffset = nextOffset
                    while (true) {
                        val page = BiliApi.dynamicAllVideo(offset = currentOffset)
                        if (token != requestToken) return@launch
                        val visibleItems = VideoCardVisibilityFilter.filterVisibleFresh(page.items, loadedStableKeys)
                        nextOffset = page.nextOffset
                        endReached = nextOffset == null
                        if (visibleItems.isNotEmpty() || endReached || nextOffset == currentOffset || page.items.isEmpty()) {
                            visibleItems.forEach { loadedStableKeys.add(it.stableKey()) }
                            videoAdapter.append(visibleItems)
                            break
                        }
                        currentOffset = nextOffset
                    }
                } else {
                    var targetPage = nextPage.coerceAtLeast(1)
                    while (true) {
                        val page =
                            BiliApi.spaceArcSearchPage(
                                mid = selectedMid,
                                pn = targetPage,
                                ps = 30,
                            )
                        if (token != requestToken) return@launch

                        val visibleItems = VideoCardVisibilityFilter.filterVisibleFresh(page.items, loadedStableKeys)
                        nextPage = targetPage + 1
                        endReached = !page.hasMore
                        if (visibleItems.isNotEmpty() || endReached || page.items.isEmpty()) {
                            visibleItems.forEach { loadedStableKeys.add(it.stableKey()) }
                            videoAdapter.append(visibleItems)
                            break
                        }
                        targetPage = nextPage
                    }
                }
                _binding?.let { b ->
                    b.recyclerDynamic.postIfAlive(isAlive = { _binding === b && isResumed }) {
                        if (pendingFocusFirstFeedCardAfterRefresh) {
                            pendingFocusFirstFeedCardAfterRefresh = false
                            val recycler = b.recyclerDynamic
                            val isUiAlive = { _binding === b && isResumed }
                            recycler.requestFocusFirstItemOrSelfAfterRefresh(
                                itemCount = videoAdapter.itemCount,
                                smoothScroll = false,
                                isAlive = isUiAlive,
                                onDone = { unparkFocusAfterDataSetReset(followingListController, dynamicGridController) },
                            )
                            return@postIfAlive
                        }
                        dynamicGridController?.consumePendingFocusAfterLoadMore()
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("Dynamic", "load feed failed mid=$selectedMid", t)
                context?.let { AppToast.show(it, "加载失败，可查看 Logcat(标签 BLBL)") }
            } finally {
                if (token == requestToken) _binding?.swipeRefresh?.isRefreshing = false
                isLoadingMore = false
            }
        }
    }

    private fun openArticlePopup(article: BiliApi.DynamicArticleCard) {
        var webView: WebView? = null
        var progress: ProgressBar? = null
        var container: FrameLayout? = null
        val height = (resources.displayMetrics.heightPixels * 0.72f).toInt().coerceAtLeast(280)
        val webUrl =
            if (article.id > 0L) {
                "https://www.bilibili.com/opus/${article.id}"
            } else {
                article.jumpUrl
            }
        AppPopup.custom(
            context = requireContext(),
            title = article.title,
            cancelable = true,
            actions = listOf(PopupAction(role = PopupActionRole.POSITIVE, text = "关闭")),
            preferredActionRole = PopupActionRole.POSITIVE,
            autoFocus = false,
            modalSizing =
                PopupModalSizing(
                    widthRatio = 0.94f,
                    maxWidthDp = 900f,
                    maxHeightRatio = 0.90f,
                ),
            onModalAttached = { _ ->
                container?.post {
                    val host = container ?: return@post
                    if (host.isAttachedToWindow.not() || webView != null) return@post
                    webView = createArticleWebView(host.context, webUrl, host, progress)
                }
            },
            onBackPressed = {
                val web = webView
                if (web != null && web.canGoBack()) {
                    web.goBack()
                    true
                } else {
                    false
                }
            },
            onDismiss = {
                val web = webView
                webView = null
                progress = null
                web?.stopLoading()
                web?.post { web.destroy() }
            },
        ) { dialogContext ->
            val host = FrameLayout(dialogContext)
            host.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            val loading =
                ProgressBar(dialogContext).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
                    isIndeterminate = true
                }
            progress = loading
            host.addView(loading)
            container = host
            host
        }
    }

    private fun createArticleWebView(
        context: android.content.Context,
        webUrl: String,
        container: FrameLayout,
        progress: ProgressBar?,
    ): WebView {
        val web =
            WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            progress?.visibility = View.VISIBLE
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            progress?.visibility = View.GONE
                            view?.let { web ->
                                hideBiliAppPrompts(web)
                                web.postDelayed({ hideBiliAppPrompts(web) }, 1_000L)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            handleArticleWebUrl(view, request.url.toString())

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                            handleArticleWebUrl(view, url)
                    }
                webChromeClient = WebChromeClient()
            }
        container.addView(web, 0)
        web.loadUrl(webUrl)
        return web
    }

    private fun hideBiliAppPrompts(view: WebView) {
        val script =
            """
            (function() {
              var id = 'blbl-hide-app-prompts';
              var style = document.getElementById(id);
              if (style === null) {
                style = document.createElement('style');
                style.id = id;
                style.textContent = '.m-nav-openapp,.openapp-dialog,.open-app-img,[class*="openapp"],[class*="open-app"]{display:none;}';
                document.head.appendChild(style);
              }
              var texts = ['App内打开', '在App中打开', '浏览方式（推荐使用）', '浏览方式', '下载 App'];
              function hideText(text) {
                var nodes = document.querySelectorAll('body *');
                for (var i = 0; i < nodes.length; i++) {
                  var node = nodes[i];
                  if (node.children.length === 0 && (node.textContent || '').trim() === text) {
                    node.style.setProperty('display', 'none', 'important');
                    var parent = node.parentElement;
                    if (parent !== null) {
                      parent.style.setProperty('display', 'none', 'important');
                    }
                  }
                }
              }
              texts.forEach(hideText);
            })();
            """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun handleArticleWebUrl(view: WebView, url: String): Boolean {
        if (url.startsWith("bilibili://opus/detail/")) {
            val id = url.substringAfter("bilibili://opus/detail/").substringBefore('?').trim()
            if (id.isNotBlank()) {
                view.loadUrl("https://www.bilibili.com/opus/$id")
                return true
            }
        }
        if (url.startsWith("bilibili://")) {
            return true
        }
        return false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun spanCountForWidth(): Int {
        val prefs = BiliClient.prefs
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        return GridSpanPolicy.dynamicSpanCountForWidthDp(
            widthDp = widthDp,
            dynamicOverrideSpanCount = prefs.dynamicGridSpanCount,
            globalOverrideSpanCount = prefs.gridSpanCount,
        )
    }

    private fun focusSelectedFollowingIfAvailable(): Boolean {
        val b = _binding ?: return false
        val recyclerFollowing = b.recyclerFollowing
        val isUiAlive = { _binding === b && isResumed }
        recyclerFollowing.postIfAlive(isAlive = isUiAlive) {
            val recycler = recyclerFollowing
            val selectedChild =
                (0 until recycler.childCount)
                    .map { recycler.getChildAt(it) }
                    .firstOrNull { it?.isSelected == true }
            if (selectedChild != null) {
                selectedChild.requestFocus()
                return@postIfAlive
            }

            val vh = recycler.findViewHolderForAdapterPosition(0)
            if (vh != null) {
                vh.itemView.requestFocus()
                return@postIfAlive
            }

            if ((recycler.adapter?.itemCount ?: 0) <= 0) {
                recycler.requestFocus()
                return@postIfAlive
            }

            recycler.scrollToPosition(0)
            recycler.postIfAlive(isAlive = isUiAlive) {
                recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: recycler.requestFocus()
            }
        }
        return true
    }

    override fun onDestroyView() {
        _bindingLogin = null
        dynamicGridController?.release()
        dynamicGridController = null
        followingListController?.release()
        followingListController = null
        followRequestToken++
        followOrderRefreshToken++
        hasLoadedFollowingsOnce = false
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        (_binding?.recyclerDynamic?.layoutManager as? GridLayoutManager)?.let { lm ->
            val desired = spanCountForWidth()
            if (lm.spanCount != desired) lm.spanCount = desired
        }
        if (loggedIn && hasLoadedFollowingsOnce) refreshFollowingOrder()
    }

    override fun handleBackPressed(): Boolean {
        // Dynamic page has no tabs: Back in content should go to sidebar (focus-based navigation).
        val root = _binding?.root ?: _bindingLogin?.root ?: return false
        val focused = activity?.currentFocus ?: return false
        if (!FocusTreeUtils.isDescendantOf(focused, root)) return false
        return (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
    }

    override fun handleRefreshKey(): Boolean {
        if (!isResumed) return false
        if (!loggedIn) {
            AppToast.show(requireContext(), "请先登录")
            _bindingLogin?.btnLogin?.requestFocus()
            return true
        }

        val b = _binding ?: return false
        if (b.swipeRefresh.isRefreshing) return true
        pendingFocusFirstFeedCardAfterRefresh = true
        parkFocusForDataSetReset(followingListController, dynamicGridController)
        b.swipeRefresh.isRefreshing = true
        loadAll(resetFeed = true)
        return true
    }

    private fun setupContentTabs(binding: FragmentDynamicBinding) {
        val tabLayout = binding.dynamicTabLayout
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText("视频"))
        tabLayout.addTab(tabLayout.newTab().setText("专栏"))
        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    switchContentTab(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) {
                    pendingFocusFirstFeedCardAfterRefresh = true
                    loadAll(resetFeed = true)
                }
            },
        )
        tabLayout.postIfAlive(isAlive = { _binding === binding && isResumed }) {
            tabLayout.enableDpadTabFocus(selectOnFocus = BiliClient.prefs.tabSwitchFollowsFocus)
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@postIfAlive
            for (i in 0 until tabStrip.childCount) {
                tabStrip.getChildAt(i).setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return@setOnKeyListener focusFirstDynamicCard()
                    }
                    false
                }
            }
        }
    }

    private fun switchContentTab(index: Int) {
        val safeIndex = index.coerceIn(0, 1)
        if (currentContentTab == safeIndex) return
        currentContentTab = safeIndex
        val b = _binding ?: return
        parkFocusForDataSetReset(followingListController, dynamicGridController)
        if (safeIndex == 0) {
            b.recyclerDynamic.layoutManager = GridLayoutManager(requireContext(), spanCountForWidth())
            b.recyclerDynamic.adapter = videoAdapter
            if (videoAdapter.itemCount <= 0) resetAndLoadFeed()
        } else {
            b.recyclerDynamic.layoutManager = GridLayoutManager(requireContext(), spanCountForWidth())
            b.recyclerDynamic.adapter = articleAdapter
            if (articleAdapter.itemCount <= 0) resetAndLoadArticles()
        }
        b.recyclerDynamic.postIfAlive(isAlive = { _binding === b && isResumed }) {
            unparkFocusAfterDataSetReset(followingListController, dynamicGridController)
            focusFirstDynamicCard()
        }
    }

    private fun focusDynamicTab(): Boolean {
        val b = _binding ?: return false
        val tabLayout = b.dynamicTabLayout
        val position = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
        tabLayout.postIfAlive(isAlive = { _binding === b && isResumed }) {
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup
            tabStrip?.getChildAt(position)?.requestFocus() ?: tabLayout.requestFocus()
        }
        return true
    }

    private fun focusFirstDynamicCard(): Boolean {
        val b = _binding ?: return false
        val recycler = b.recyclerDynamic
        recycler.postIfAlive(isAlive = { _binding === b && isResumed }) {
            recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                ?: recycler.requestFocus()
        }
        return true
    }

    private fun applyUiMode() {
        val binding = _binding ?: return
        val uiScale = UiScale.factor(requireContext())
        val scaler = requireContext().uiScaler(uiScale)
        fun scaledPx(id: Int): Int = scaler.scaledDimenPx(id)

        val width = scaledPx(R.dimen.wiliwili_dynamic_panel_width)
        val margin = scaledPx(R.dimen.wiliwili_dynamic_panel_margin)
        val padding = scaledPx(R.dimen.wiliwili_dynamic_panel_padding)
        val gridPadding = scaledPx(R.dimen.wiliwili_dynamic_grid_padding)

        val cardLp = binding.cardFollowing.layoutParams
        var changed = false
        if (cardLp.width != width.coerceAtLeast(1)) {
            cardLp.width = width.coerceAtLeast(1)
            changed = true
        }
        val mlp = cardLp as? ViewGroup.MarginLayoutParams
        if (mlp != null && (mlp.leftMargin != margin || mlp.topMargin != margin || mlp.bottomMargin != margin)) {
            mlp.setMargins(margin, margin, mlp.rightMargin, margin)
            changed = true
        }
        if (changed) binding.cardFollowing.layoutParams = cardLp

        val feedLp = binding.cardFeed.layoutParams as? ViewGroup.MarginLayoutParams
        if (feedLp != null && (feedLp.topMargin != margin || feedLp.rightMargin != margin || feedLp.bottomMargin != margin)) {
            feedLp.setMargins(feedLp.leftMargin, margin, margin, margin)
            binding.cardFeed.layoutParams = feedLp
        }

        if (binding.recyclerFollowing.paddingLeft != padding || binding.recyclerFollowing.paddingTop != padding || binding.recyclerFollowing.paddingRight != padding || binding.recyclerFollowing.paddingBottom != padding) {
            binding.recyclerFollowing.setPadding(padding, padding, padding, padding)
        }
        if (binding.recyclerDynamic.paddingLeft != gridPadding || binding.recyclerDynamic.paddingTop != gridPadding || binding.recyclerDynamic.paddingRight != gridPadding || binding.recyclerDynamic.paddingBottom != gridPadding) {
            binding.recyclerDynamic.setPadding(gridPadding, gridPadding, gridPadding, gridPadding)
        }
    }

    companion object {
        fun newInstance() = DynamicFragment()
    }

    private fun openDetail(position: Int) {
        requireContext().openVideoDetailFromPlaybackHandle(playbackHandle(), position)
    }

    private fun playbackHandle() =
        buildPagedVideoCardPlaybackHandle(
            source = "Dynamic",
            cardsProvider = videoAdapter::snapshot,
            nextCursorProvider = {
                FeedContinuationCursor(selectedMid = selectedMid, page = nextPage, offset = nextOffset)
            },
            hasMoreProvider = { selectedMid != 0L && !endReached },
        ) { cursor ->
            if (cursor.selectedMid == FollowingAdapter.MID_ALL) {
                val page = BiliApi.dynamicAllVideo(offset = cursor.offset)
                val nextOffset = page.nextOffset
                VideoCardPlaylistPage(
                    cards = page.items,
                    nextCursor =
                        FeedContinuationCursor(
                            selectedMid = cursor.selectedMid,
                            page = 1,
                            offset = nextOffset,
                        ),
                    hasMore = nextOffset != null,
                    canAdvance = nextOffset != null && nextOffset != cursor.offset && page.items.isNotEmpty(),
                )
            } else {
                val pageNum = cursor.page.coerceAtLeast(1)
                val page = BiliApi.spaceArcSearchPage(mid = cursor.selectedMid, pn = pageNum, ps = 30)
                VideoCardPlaylistPage(
                    cards = page.items,
                    nextCursor =
                        FeedContinuationCursor(
                            selectedMid = cursor.selectedMid,
                            page = pageNum + 1,
                            offset = null,
                        ),
                    hasMore = page.hasMore,
                    canAdvance = page.hasMore && page.items.isNotEmpty(),
                )
            }
        }
}
