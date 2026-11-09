package blbl.cat3399.feature.following

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.api.video.VideoCollectionKind
import blbl.cat3399.core.api.video.VideoCollectionSection
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.model.FavFolder
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.tv.RemoteKeys
import blbl.cat3399.core.ui.ActivityStackLimiter
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.GridSpanPolicy
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.core.ui.ThemeColor
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.feature.video.VideoDetailActivity
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.ui.enableDpadTabFocus
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ActivityUpDetailBinding
import blbl.cat3399.feature.login.QrLoginActivity
import blbl.cat3399.feature.player.PlayerPlaylistContinuation
import blbl.cat3399.feature.player.VideoCardPlaylistPage
import blbl.cat3399.feature.player.buildFreshVideoCardPlaylistContinuation
import blbl.cat3399.feature.video.VideoCardActionController
import blbl.cat3399.feature.video.VideoCardAdapter
import blbl.cat3399.feature.video.VideoCardDismissBehavior
import blbl.cat3399.feature.video.VideoCardVisibilityFilter
import blbl.cat3399.feature.video.buildPagedVideoCardPlaybackHandle
import blbl.cat3399.feature.video.buildVideoCardPlaybackHandle
import blbl.cat3399.feature.video.defaultVideoCardPlaylistItem
import blbl.cat3399.feature.video.openVideoDetailFromPlaybackHandle
import blbl.cat3399.feature.video.openVideoFromPlaybackHandle
import blbl.cat3399.feature.video.removeVideoCardAndRestoreFocus
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UpDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityUpDetailBinding

    private lateinit var archiveAdapter: VideoCardAdapter
    private lateinit var sectionAdapter: UpDetailSectionAdapter

    private lateinit var archiveLayoutManager: GridLayoutManager
    private lateinit var sectionsLayoutManager: LinearLayoutManager

    private val mid: Long by lazy { intent.getLongExtra(EXTRA_MID, 0L) }

    private enum class UpTab { ARCHIVE, COLLECTION_SERIES, DYNAMIC, FAVORITES }

    private var currentTab: UpTab = UpTab.ARCHIVE

    private var isFollowed: Boolean = false
    private var followActionInFlight: Boolean = false
    private var loadedInitialInfo: Boolean = false
    private var headerRequestToken: Int = 0
    private var appBarVerticalOffset: Int = 0
    private var pendingFocusHeaderAfterExpand: Boolean = false
    private var tabSwitchRequestToken: Int = 0

    private val loadedArchiveStableKeys = HashSet<String>()
    private var archiveIsLoadingMore: Boolean = false
    private var archiveEndReached: Boolean = false
    private var archiveNextPage: Int = 1
    private var archiveRequestToken: Int = 0
    private val dynamicCards = ArrayList<VideoCard>()
    private val dynamicLoadedKeys = HashSet<String>()
    private var dynamicNextOffset: String? = null
    private var dynamicEndReached: Boolean = false
    private var dynamicIsLoading: Boolean = false
    private var dynamicRequestToken: Int = 0
    private val favoriteFolders = ArrayList<FavFolder>()
    private val favoriteLoadedKeys = HashSet<String>()
    private var favoriteSelectedMediaId: Long = 0L
    private var favoriteNextPage: Int = 1
    private var favoriteEndReached: Boolean = false
    private var favoriteIsLoading: Boolean = false
    private var favoriteRequestToken: Int = 0
    private var favoriteFolderTabsBound: Boolean = false

    private val seasonsSeriesSections = ArrayList<UpDetailSection>()
    private val loadedSectionStableIds = HashSet<String>()
    private var seasonsSeriesIsLoadingMore: Boolean = false
    private var seasonsSeriesEndReached: Boolean = false
    private var seasonsSeriesNextPage: Int = 1
    private var seasonsSeriesRequestToken: Int = 0

    private data class SectionPagingState(
        var nextPage: Int = 1,
        var isLoading: Boolean = false,
        var endReached: Boolean = false,
        var totalCount: Int? = null,
        val loadedBvids: HashSet<String> = HashSet(),
    )

    private val sectionPagingStates = HashMap<String, SectionPagingState>()
    private val sectionPendingAdvanceIndex = HashMap<String, Int>()

    private var pendingFocusFirstArchiveItemAfterLoad: Boolean = false
    private var pendingFocusFirstSectionItemAfterLoad: Boolean = false
    private var forceFocusFirstContentAfterRefresh: Boolean = false
    private var archiveGridController: DpadGridController? = null
    private var focusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener? = null
    private var didRequestInitialTab0Focus: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityStackLimiter.register(group = ACTIVITY_STACK_GROUP, activity = this, maxDepth = ACTIVITY_STACK_MAX_DEPTH)
        binding = ActivityUpDetailBinding.inflate(layoutInflater.cloneInUserScale(this))
        setContentView(binding.root)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        binding.swipeRefresh.isNestedScrollingEnabled = true

        if (mid <= 0L) {
            AppToast.show(this, "无效的 UP 主 mid")
            finish()
            return
        }

        archiveLayoutManager = GridLayoutManager(this, spanCountForWidth())
        sectionsLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        setupHeaderButtons()
        prefillHeaderFromIntent()
        setupAppBar()
        setupAdapters()
        setupTabs()

        binding.recycler.setHasFixedSize(true)
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        applyRecyclerForCurrentTab()
        installFocusListener()

        binding.swipeRefresh.setOnRefreshListener { refreshCurrentTab() }
        refreshCurrentTab(initial = true)
    }

    override fun onDestroy() {
        uninstallFocusListener()
        archiveGridController?.release()
        archiveGridController = null
        ActivityStackLimiter.unregister(group = ACTIVITY_STACK_GROUP, activity = this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)

        archiveAdapter.invalidateSizing()
        sectionAdapter.invalidateSizing()

        if (isGridTab(currentTab)) {
            archiveLayoutManager.spanCount = spanCountForWidth()
            if (!binding.swipeRefresh.isRefreshing && archiveAdapter.itemCount == 0) {
                ensureTabLoadedIfNeeded()
            }
        } else {
            if (!binding.swipeRefresh.isRefreshing && sectionAdapter.itemCount == 0) {
                ensureSeasonsSeriesLoadedIfNeeded()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        // Avoid showing focus highlight when user entered via touch; rely on first DPAD nav key to
        // establish focus in that case (see dispatchKeyEvent()).
        if (hasFocus && !binding.root.isInTouchMode) {
            scheduleInitialTab0Focus()
            ensureInitialFocus()
        }
        if (hasFocus) maybeConsumePendingFocusFirstContentAfterLoad()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && RemoteKeys.isRefreshKey(event.keyCode)) {
            if (binding.swipeRefresh.isRefreshing) return true
            refreshCurrentTab()
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val focused = currentFocus
            if (focused != null && FocusTreeUtils.isDescendantOf(focused, binding.recycler)) {
                val next = focused.focusSearch(android.view.View.FOCUS_UP)
                if (next == null || !FocusTreeUtils.isDescendantOf(next, binding.recycler)) {
                    // Only consume when we really moved focus; otherwise let the framework attempt
                    // default focus-search (better than trapping DPAD_UP inside the grid).
                    return focusTabsFromContentEdge()
                }
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN && currentFocus == null && isNavKey(event.keyCode)) {
            ensureInitialFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupHeaderButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnBack.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return@setOnKeyListener false
            focusSelectedTab()
            true
        }

        binding.btnFollow.setOnClickListener { onFollowClicked() }
        binding.btnFollowingStat.setOnClickListener { openRelationList(showFans = false) }
        binding.btnFansStat.setOnClickListener { openRelationList(showFans = true) }
        binding.btnFollow.isFocusableInTouchMode = true
        binding.btnFollow.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return@setOnKeyListener false
            focusSelectedTab()
            true
        }
    }

    private fun setupAppBar() {
        binding.appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                appBarVerticalOffset = verticalOffset
                if (pendingFocusHeaderAfterExpand && verticalOffset == 0) {
                    pendingFocusHeaderAfterExpand = false
                    val focused = currentFocus
                    if (focused == null || FocusTreeUtils.isDescendantOf(focused, binding.tabLayout)) {
                        focusHeaderPreferred()
                    }
                }
            },
        )
    }

    private fun installFocusListener() {
        if (focusListener != null) return
        focusListener =
            android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
                if (newFocus == null) return@OnGlobalFocusChangeListener
                if (FocusTreeUtils.isDescendantOf(newFocus, binding.recycler)) {
                    collapseHeaderForContent()
                }
            }.also { binding.root.viewTreeObserver.addOnGlobalFocusChangeListener(it) }
    }

    private fun uninstallFocusListener() {
        focusListener?.let { binding.root.viewTreeObserver.removeOnGlobalFocusChangeListener(it) }
        focusListener = null
    }

    private fun focusHeaderPreferred() {
        if (binding.btnFollow.isVisible) {
            binding.btnFollow.requestFocus()
        } else {
            binding.btnBack.requestFocus()
        }
    }

    private fun requestExpandHeaderAndFocus() {
        if (appBarVerticalOffset == 0) {
            focusHeaderPreferred()
            return
        }
        pendingFocusHeaderAfterExpand = true
        binding.appBar.setExpanded(true, true)
    }

    private fun collapseHeaderForContent() {
        val total = binding.appBar.totalScrollRange
        if (total <= 0) return
        val fullyCollapsedOffset = -total
        if (appBarVerticalOffset > fullyCollapsedOffset) binding.appBar.setExpanded(false, true)
    }

    private fun prefillHeaderFromIntent() {
        binding.tvName.text = intent.getStringExtra(EXTRA_NAME).orEmpty()
        binding.tvSign.text = intent.getStringExtra(EXTRA_SIGN).orEmpty()
        binding.tvSign.isVisible = binding.tvSign.text.isNotBlank()
        binding.tvFollowingCount.text = "-"
        binding.tvFansCount.text = "-"
        binding.tvLikesCount.text = "-"
        binding.tvPlayCount.text = "-"
        val avatar = intent.getStringExtra(EXTRA_AVATAR)
        blbl.cat3399.core.image.ImageLoader.loadInto(binding.ivAvatar, blbl.cat3399.core.image.ImageUrl.avatar(avatar))
    }

    private fun setupAdapters() {
        val archiveActionController =
            VideoCardActionController(
                context = this,
                scope = lifecycleScope,
                dismissBehavior = VideoCardDismissBehavior.LocalNotInterested,
                onOpenDetail = { _, pos ->
                    openVideoDetailFromPlaybackHandle(
                        playbackHandle = archivePlaybackHandle(),
                        position = pos,
                    )
                },
                onOpenUp = { card -> openUpDetailForCard(card) },
                onCardRemoved = { stableKey ->
                    binding.recycler.removeVideoCardAndRestoreFocus(
                        adapter = archiveAdapter,
                        stableKey = stableKey,
                        isAlive = { !isFinishing && !isDestroyed && isGridTab(currentTab) },
                    )
                },
            )
        archiveAdapter =
            VideoCardAdapter(
                onClick = { _, pos ->
                    openVideoFromPlaybackHandle(
                        playbackHandle = archivePlaybackHandle(),
                        position = pos,
                        openDetailBeforePlay = BiliClient.prefs.playerOpenDetailBeforePlay,
                    )
                },
                actionDelegate = archiveActionController,
            )

        sectionAdapter =
            UpDetailSectionAdapter(
                onVideoClick = { section, _, index ->
                    openVideoFromPlaybackHandle(
                        playbackHandle = buildSectionPlaybackHandle(section),
                        position = index,
                        openDetailBeforePlay = BiliClient.prefs.playerOpenDetailBeforePlay,
                    )
                },
                onRequestLoadMore = { section, requestedNextIndex ->
                    onSectionLoadMoreRequested(section, requestedNextIndex)
                },
                onRequestMoveToTabs = { focusTabsFromContentEdge() },
            )
    }

    private fun setupTabs() {
        binding.tabLayout.removeAllTabs()
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.up_tab_archive))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.up_tab_collection_series))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("动态"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("收藏"))

        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val next =
                        when (tab.position) {
                            1 -> UpTab.COLLECTION_SERIES
                            2 -> UpTab.DYNAMIC
                            3 -> UpTab.FAVORITES
                            else -> UpTab.ARCHIVE
                        }
                    switchTab(next)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            },
        )

        binding.favoriteFolderTabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    onFavoriteFolderSelected(tab.position)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            },
        )
        binding.favoriteFolderTabs.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) {
            binding.favoriteFolderTabs.enableDpadTabFocus(
                selectOnFocusProvider = { BiliClient.prefs.tabSwitchFollowsFocus },
            )
            val strip = binding.favoriteFolderTabs.getChildAt(0) as? ViewGroup ?: return@postIfAlive
            for (i in 0 until strip.childCount) {
                strip.getChildAt(i).setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return@setOnKeyListener requestFocusFirstContentFromTab()
                    }
                    false
                }
            }
        }

        val tabLayout = binding.tabLayout
        tabLayout.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) {
            tabLayout.enableDpadTabFocus(selectOnFocusProvider = { BiliClient.prefs.tabSwitchFollowsFocus })
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@postIfAlive
            for (i in 0 until tabStrip.childCount) {
                val tabView = tabStrip.getChildAt(i)
                tabView.isFocusableInTouchMode = true
                tabView.setOnKeyListener { _, keyCode, event ->
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            requestExpandHeaderAndFocus()
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            collapseHeaderForContent()
                            requestFocusFirstContentFromTab()
                            true
                        }

                        else -> false
                    }
                }
            }
        }
    }

    private fun scheduleInitialTab0Focus() {
        // Mirror VideoDetailActivity: post a single initial focus request after the first layout,
        // so focus lands on the primary entry control (Tab0) instead of the header button.
        val tabLayout = binding.tabLayout
        tabLayout.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) {
            // Don't create focus highlight when entering via touch.
            if (binding.root.isInTouchMode) return@postIfAlive
            if (didRequestInitialTab0Focus) return@postIfAlive
            didRequestInitialTab0Focus = true

            tabLayout.getTabAt(0)?.select()
            requestFocusSelectedTabView()
        }
    }

    private fun switchTab(next: UpTab) {
        if (currentTab == next) return
        currentTab = next
        // Tab switching can be triggered by focus changes while RecyclerView is recycling children
        // (e.g. during generic motion scroll). Switching adapter/layout manager synchronously at that
        // moment can crash with "Cannot call removeView(At) within removeView(At)".
        val token = ++tabSwitchRequestToken
        binding.recycler.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) {
            if (token != tabSwitchRequestToken) return@postIfAlive
            applyRecyclerForCurrentTab()
            ensureTabLoadedIfNeeded()
        }
    }

    private fun isGridTab(tab: UpTab): Boolean =
        tab == UpTab.ARCHIVE || tab == UpTab.DYNAMIC || tab == UpTab.FAVORITES

    private fun gridTabLoading(): Boolean =
        when (currentTab) {
            UpTab.ARCHIVE -> archiveIsLoadingMore
            UpTab.DYNAMIC -> dynamicIsLoading
            UpTab.FAVORITES -> favoriteIsLoading
            else -> false
        }

    private fun gridTabEndReached(): Boolean =
        when (currentTab) {
            UpTab.ARCHIVE -> archiveEndReached
            UpTab.DYNAMIC -> dynamicEndReached
            UpTab.FAVORITES -> favoriteEndReached
            else -> true
        }

    private fun loadMoreGridTab() {
        when (currentTab) {
            UpTab.ARCHIVE -> loadMoreArchive()
            UpTab.DYNAMIC -> loadMoreDynamic()
            UpTab.FAVORITES -> loadMoreFavoriteFolder()
            else -> Unit
        }
    }

    private fun applyRecyclerForCurrentTab() {
        binding.recycler.clearOnScrollListeners()
        binding.favoriteFolderTabs.visibility = if (currentTab == UpTab.FAVORITES) View.VISIBLE else View.GONE
        if (isGridTab(currentTab)) {
            archiveAdapter.submit(emptyList())
            binding.recycler.adapter = archiveAdapter
            binding.recycler.layoutManager = archiveLayoutManager
            archiveLayoutManager.spanCount = spanCountForWidth()

            binding.recycler.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        if (dy > 0) collapseHeaderForContent()
                        if (dy <= 0) return
                        if (gridTabLoading() || gridTabEndReached()) return
                        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val lastVisible = lm.findLastVisibleItemPosition()
                        val total = archiveAdapter.itemCount
                        if (total <= 0) return
                        if (total - lastVisible - 1 <= 8) loadMoreGridTab()
                    }
                },
            )

            installArchiveGridController()
        } else {
            binding.recycler.adapter = sectionAdapter
            binding.recycler.layoutManager = sectionsLayoutManager
            sectionAdapter.replaceAll(seasonsSeriesSections)
            installSeasonsSeriesScrollListener()
            releaseArchiveGridController()
        }
    }

    private fun installSeasonsSeriesScrollListener() {
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) collapseHeaderForContent()
                    if (dy <= 0) return
                    if (seasonsSeriesIsLoadingMore || seasonsSeriesEndReached) return
                    val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = recyclerView.adapter?.itemCount ?: 0
                    if (total <= 0) return
                    if (total - lastVisible - 1 <= 2) loadMoreSeasonsSeries()
                }
            },
        )
    }

    private fun ensureTabLoadedIfNeeded() {
        when (currentTab) {
            UpTab.ARCHIVE -> {
                if (!binding.swipeRefresh.isRefreshing && archiveAdapter.itemCount == 0) {
                    resetAndLoadArchive()
                }
            }

            UpTab.COLLECTION_SERIES -> ensureSeasonsSeriesLoadedIfNeeded()

            UpTab.DYNAMIC -> {
                if (!binding.swipeRefresh.isRefreshing && archiveAdapter.itemCount == 0 && !dynamicIsLoading) {
                    resetAndLoadDynamic()
                }
            }

            UpTab.FAVORITES -> {
                if (!binding.swipeRefresh.isRefreshing && !favoriteIsLoading) {
                    resetAndLoadFavorites()
                }
            }
        }
    }

    private fun ensureSeasonsSeriesLoadedIfNeeded() {
        if (seasonsSeriesIsLoadingMore) return
        if (seasonsSeriesSections.isNotEmpty()) return
        resetAndLoadSeasonsSeries()
    }

    private fun refreshCurrentTab(initial: Boolean = false) {
        if (!initial) {
            forceFocusFirstContentAfterRefresh = true
            binding.swipeRefresh.isRefreshing = true
        }
        // Avoid framework fallback focus during adapter resets.
        if (forceFocusFirstContentAfterRefresh) {
            when (currentTab) {
                UpTab.ARCHIVE,
                UpTab.DYNAMIC,
                UpTab.FAVORITES,
                -> archiveGridController?.parkFocusForDataSetReset()
                UpTab.COLLECTION_SERIES -> parkFocusInRecyclerForDataSetReset()
            }
        }
        loadHeader()
        when (currentTab) {
            UpTab.ARCHIVE -> resetAndLoadArchive()
            UpTab.COLLECTION_SERIES -> resetAndLoadSeasonsSeries()
            UpTab.DYNAMIC -> resetAndLoadDynamic()
            UpTab.FAVORITES -> resetAndLoadFavorites()
        }
    }

    private fun ensureInitialFocus() {
        if (currentFocus != null) return
        if (focusSelectedTab()) return
        if (requestFocusFirstContentIfPossible()) return
        if (appBarVerticalOffset == 0 && binding.btnFollow.isVisible) {
            binding.btnFollow.requestFocus()
            return
        }
        binding.btnBack.requestFocus()
    }

    private fun requestFocusSelectedTabView(): Boolean {
        val tabLayout = binding.tabLayout
        val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return false
        if (tabStrip.childCount <= 0) return false
        val selected = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0

        fun tryFocusTabView(view: android.view.View?): Boolean {
            if (view == null) return false
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            return view.requestFocus()
        }

        if (tryFocusTabView(tabStrip.getChildAt(selected))) return true
        for (i in 0 until tabStrip.childCount) {
            if (tryFocusTabView(tabStrip.getChildAt(i))) return true
        }
        return false
    }

    private fun focusSelectedTab(): Boolean {
        if (requestFocusSelectedTabView()) return true
        return binding.btnBack.requestFocus()
    }

    private fun focusTabsFromContentEdge(): Boolean {
        return requestFocusSelectedTabView()
    }

    private fun requestFocusFirstContentFromTab(): Boolean {
        when (currentTab) {
            UpTab.ARCHIVE,
            UpTab.DYNAMIC,
            UpTab.FAVORITES,
            -> {
                if (archiveAdapter.itemCount > 0) {
                    focusGridAt(0)
                    return true
                }
                pendingFocusFirstArchiveItemAfterLoad = true
                if (!binding.swipeRefresh.isRefreshing) ensureTabLoadedIfNeeded()
                return true
            }

            UpTab.COLLECTION_SERIES -> {
                if (seasonsSeriesSections.isNotEmpty()) {
                    focusFirstSectionVideo()
                    return true
                }
                pendingFocusFirstSectionItemAfterLoad = true
                ensureSeasonsSeriesLoadedIfNeeded()
                return true
            }
        }
    }

    private fun requestFocusFirstContentIfPossible(): Boolean {
        return when (currentTab) {
            UpTab.ARCHIVE,
            UpTab.DYNAMIC,
            UpTab.FAVORITES,
            -> {
                if (archiveAdapter.itemCount <= 0) return false
                focusGridAt(0)
                true
            }

            UpTab.COLLECTION_SERIES -> {
                if (seasonsSeriesSections.isEmpty()) return false
                focusFirstSectionVideo()
                true
            }
        }
    }

    private fun loadHeader() {
        val token = ++headerRequestToken
        binding.tvFollowingCount.text = "-"
        binding.tvFansCount.text = "-"
        binding.tvLikesCount.text = "-"
        binding.tvPlayCount.text = "-"
        lifecycleScope.launch {
            val info =
                try {
                    BiliApi.spaceAccInfo(mid)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.w("UpDetail", "loadHeader failed mid=$mid", t)
                    null
                }
            if (token != headerRequestToken) return@launch

            if (info != null) {
                loadedInitialInfo = true
                binding.tvName.text = info.name
                binding.tvSign.text = info.sign.orEmpty()
                binding.tvSign.isVisible = !info.sign.isNullOrBlank()
                blbl.cat3399.core.image.ImageLoader.loadInto(binding.ivAvatar, blbl.cat3399.core.image.ImageUrl.avatar(info.faceUrl))
                isFollowed = info.isFollowed
                updateFollowUi()
            } else if (!loadedInitialInfo) {
                binding.tvName.text = binding.tvName.text.takeIf { it.isNotBlank() } ?: "加载失败"
            }

            val relation =
                try {
                    BiliApi.relationStat(mid)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    null
                }
            if (relation != null) {
                if (token != headerRequestToken) return@launch
                binding.tvFollowingCount.text = Format.count(relation.following)
                binding.tvFansCount.text = Format.count(relation.follower)
            }

            if (BiliClient.cookies.hasSessData()) {
                val upstat =
                    try {
                        BiliApi.spaceUpStat(mid)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        null
                    }
                if (upstat != null) {
                    if (token != headerRequestToken) return@launch
                    binding.tvLikesCount.text = Format.count(upstat.likes)
                    binding.tvPlayCount.text = Format.count(upstat.archiveView)
                }
            }
        }
    }

    private fun resetAndLoadArchive() {
        if (forceFocusFirstContentAfterRefresh) {
            pendingFocusFirstArchiveItemAfterLoad = true
            archiveGridController?.parkFocusForDataSetReset()
        } else {
            val focused = currentFocus
            if (focused != null && focused != binding.recycler && FocusTreeUtils.isDescendantOf(focused, binding.recycler)) {
                pendingFocusFirstArchiveItemAfterLoad = true
            }
        }
        archiveRequestToken++
        loadedArchiveStableKeys.clear()
        archiveNextPage = 1
        archiveEndReached = false
        archiveIsLoadingMore = false
        archiveGridController?.clearPendingFocusAfterLoadMore()
        archiveAdapter.submit(emptyList())
        binding.swipeRefresh.isRefreshing = true
        loadMoreArchive(isRefresh = true)
    }

    private fun loadMoreArchive(isRefresh: Boolean = false) {
        if (archiveIsLoadingMore || archiveEndReached) return
        val token = archiveRequestToken
        val targetPage = archiveNextPage.coerceAtLeast(1)
        archiveIsLoadingMore = true
        lifecycleScope.launch {
            try {
                val page =
                    BiliApi.spaceArcSearchPage(
                        mid = mid,
                        pn = targetPage,
                        ps = 30,
                    )
                if (token != archiveRequestToken) return@launch

                archiveNextPage = targetPage + 1
                archiveEndReached = page.items.isEmpty() || !page.hasMore

                val visibleItems = VideoCardVisibilityFilter.filterVisibleFresh(page.items, loadedArchiveStableKeys)
                visibleItems.forEach { loadedArchiveStableKeys.add(it.stableKey()) }
                if (isRefresh) archiveAdapter.submit(visibleItems) else archiveAdapter.append(visibleItems)

                binding.recycler.post {
                    maybeConsumePendingFocusFirstContentAfterLoad()
                    archiveGridController?.consumePendingFocusAfterLoadMore()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("UpDetail", "loadArchive failed mid=$mid page=$targetPage", t)
                AppToast.show(this@UpDetailActivity, "加载失败，可查看 Logcat(标签 BLBL)")
            } finally {
                if (token == archiveRequestToken) binding.swipeRefresh.isRefreshing = false
                archiveIsLoadingMore = false
            }
        }
    }

    private fun resetAndLoadDynamic() {
        dynamicRequestToken++
        dynamicNextOffset = null
        dynamicEndReached = false
        dynamicIsLoading = false
        dynamicCards.clear()
        dynamicLoadedKeys.clear()
        archiveAdapter.submit(emptyList())
        binding.swipeRefresh.isRefreshing = true
        loadMoreDynamic(isRefresh = true)
    }

    private fun loadMoreDynamic(isRefresh: Boolean = false) {
        if (currentTab != UpTab.DYNAMIC) return
        if (dynamicIsLoading || dynamicEndReached) return
        val token = dynamicRequestToken
        dynamicIsLoading = true
        lifecycleScope.launch {
            try {
                val page =
                    BiliApi.dynamicSpaceVideo(
                        hostMid = mid,
                        offset = if (isRefresh) null else dynamicNextOffset,
                        minCardCount = 20,
                        maxPages = 1,
                    )
                if (token != dynamicRequestToken) return@launch
                val visible = VideoCardVisibilityFilter.filterVisibleFresh(page.items, dynamicLoadedKeys)
                dynamicNextOffset = page.nextOffset
                dynamicEndReached = page.nextOffset == null
                if (isRefresh) archiveAdapter.submit(visible) else archiveAdapter.append(visible)
                visible.forEach { dynamicLoadedKeys.add(it.stableKey()) }
                dynamicCards.clear()
                dynamicCards.addAll(archiveAdapter.snapshot())
                binding.recycler.post { maybeConsumePendingFocusFirstContentAfterLoad() }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("UpDetail", "load dynamic failed mid=$mid", t)
                AppToast.show(this@UpDetailActivity, "动态加载失败")
            } finally {
                if (token == dynamicRequestToken) binding.swipeRefresh.isRefreshing = false
                dynamicIsLoading = false
            }
        }
    }

    private fun resetAndLoadFavorites() {
        if (currentTab != UpTab.FAVORITES) return
        favoriteRequestToken++
        favoriteFolders.clear()
        favoriteLoadedKeys.clear()
        favoriteSelectedMediaId = 0L
        favoriteNextPage = 1
        favoriteEndReached = false
        favoriteIsLoading = false
        archiveAdapter.submit(emptyList())
        binding.favoriteFolderTabs.removeAllTabs()
        binding.swipeRefresh.isRefreshing = true
        val token = favoriteRequestToken
        lifecycleScope.launch {
            try {
                val folders = BiliApi.favFolders(mid)
                if (token != favoriteRequestToken) return@launch
                favoriteFolders.addAll(folders)
                binding.favoriteFolderTabs.removeAllTabs()
                folders.forEach { folder ->
                    binding.favoriteFolderTabs.addTab(
                        binding.favoriteFolderTabs.newTab().setText(folder.title.ifBlank { "收藏夹" }),
                    )
                }
                if (folders.isEmpty()) {
                    binding.swipeRefresh.isRefreshing = false
                    AppToast.show(this@UpDetailActivity, "暂无公开收藏夹")
                    return@launch
                }
                val first = binding.favoriteFolderTabs.getTabAt(0)
                if (first?.isSelected == true) {
                    onFavoriteFolderSelected(0)
                } else {
                    first?.select()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                binding.swipeRefresh.isRefreshing = false
                AppLog.e("UpDetail", "load favorite folders failed mid=$mid", t)
                AppToast.show(this@UpDetailActivity, "收藏夹加载失败")
            }
        }
    }

    private fun onFavoriteFolderSelected(position: Int) {
        if (currentTab != UpTab.FAVORITES) return
        val folder = favoriteFolders.getOrNull(position) ?: return
        if (favoriteSelectedMediaId != folder.mediaId) {
            favoriteSelectedMediaId = folder.mediaId
            favoriteLoadedKeys.clear()
            favoriteNextPage = 1
            favoriteEndReached = false
            archiveAdapter.submit(emptyList())
        }
        binding.swipeRefresh.isRefreshing = true
        loadMoreFavoriteFolder(isRefresh = true)
    }

    private fun loadMoreFavoriteFolder(isRefresh: Boolean = false) {
        if (currentTab != UpTab.FAVORITES) return
        val mediaId = favoriteSelectedMediaId.takeIf { it > 0L } ?: return
        if (favoriteIsLoading || favoriteEndReached) return
        val token = favoriteRequestToken
        favoriteIsLoading = true
        lifecycleScope.launch {
            try {
                val page = BiliApi.favFolderResources(mediaId = mediaId, pn = favoriteNextPage, ps = 20)
                if (token != favoriteRequestToken || currentTab != UpTab.FAVORITES) return@launch
                val visible = VideoCardVisibilityFilter.filterVisibleFresh(page.items, favoriteLoadedKeys)
                visible.forEach { favoriteLoadedKeys.add(it.stableKey()) }
                if (isRefresh) archiveAdapter.submit(visible) else archiveAdapter.append(visible)
                favoriteNextPage += 1
                favoriteEndReached = !page.hasMore
                binding.recycler.post { maybeConsumePendingFocusFirstContentAfterLoad() }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("UpDetail", "load favorite folder failed mediaId=$mediaId", t)
                AppToast.show(this@UpDetailActivity, "收藏夹内容加载失败")
            } finally {
                if (token == favoriteRequestToken) binding.swipeRefresh.isRefreshing = false
                favoriteIsLoading = false
            }
        }
    }

    private fun resetAndLoadSeasonsSeries() {
        if (forceFocusFirstContentAfterRefresh) {
            pendingFocusFirstSectionItemAfterLoad = true
            parkFocusInRecyclerForDataSetReset()
        } else {
            val focused = currentFocus
            if (focused != null && focused != binding.recycler && FocusTreeUtils.isDescendantOf(focused, binding.recycler)) {
                pendingFocusFirstSectionItemAfterLoad = true
            }
        }
        seasonsSeriesRequestToken++
        seasonsSeriesNextPage = 1
        seasonsSeriesEndReached = false
        seasonsSeriesIsLoadingMore = false
        loadedSectionStableIds.clear()
        seasonsSeriesSections.clear()
        sectionPagingStates.clear()
        sectionPendingAdvanceIndex.clear()
        binding.swipeRefresh.isRefreshing = true
        if (currentTab == UpTab.COLLECTION_SERIES) sectionAdapter.replaceAll(emptyList())
        loadMoreSeasonsSeries(isRefresh = true)
    }

    private fun ensureSectionPagingState(section: UpDetailSection): SectionPagingState {
        val key = section.stableId
        val state = sectionPagingStates.getOrPut(key) { SectionPagingState(totalCount = section.totalCount) }
        if (state.totalCount == null && section.totalCount != null && section.totalCount > 0) {
            state.totalCount = section.totalCount
        }
        for (v in section.videos) {
            val bvid = v.bvid.trim()
            if (bvid.isBlank()) continue
            state.loadedBvids.add(bvid)
        }
        val total = state.totalCount
        if (!state.endReached && total != null && total > 0 && state.loadedBvids.size >= total) {
            state.endReached = true
        }
        return state
    }

    private fun onSectionLoadMoreRequested(section: UpDetailSection, requestedNextIndex: Int) {
        val stableId = section.stableId
        val latest = seasonsSeriesSections.firstOrNull { it.stableId == stableId } ?: section
        val state = ensureSectionPagingState(latest)
        if (state.isLoading || state.endReached) return
        sectionPendingAdvanceIndex[stableId] = requestedNextIndex.coerceAtLeast(0)
        loadMoreSectionVideos(section = latest, state = state)
    }

    private data class SectionArchivesParsedPage(
        val totalCount: Int?,
        val videos: List<VideoCard>,
    )

    private fun List<VideoCard>.withOwnerFallback(ownerFallback: String): List<VideoCard> {
        if (ownerFallback.isBlank()) return this
        return map { card ->
            if (card.ownerName.isBlank()) card.copy(ownerName = ownerFallback) else card
        }
    }

    private fun VideoCollectionSection.toUpDetailSection(ownerFallback: String): UpDetailSection =
        UpDetailSection(
            kind =
                when (kind) {
                    VideoCollectionKind.SEASON -> UpDetailSectionKind.SEASON
                    VideoCollectionKind.SERIES -> UpDetailSectionKind.SERIES
                },
            id = id,
            title = title,
            totalCount = totalCount,
            videos = items.withOwnerFallback(ownerFallback),
        )

    private fun sectionArchivesPage(
        total: Int,
        videos: List<VideoCard>,
        ownerFallback: String,
    ): SectionArchivesParsedPage {
        return SectionArchivesParsedPage(
            totalCount = total.takeIf { it > 0 },
            videos = videos.withOwnerFallback(ownerFallback),
        )
    }

    private fun appendVideosToSectionInMemory(sectionStableId: String, videos: List<VideoCard>): UpDetailSection? {
        if (videos.isEmpty()) return null
        val idx = seasonsSeriesSections.indexOfFirst { it.stableId == sectionStableId }
        if (idx < 0) return null
        val old = seasonsSeriesSections[idx]
        val updated = old.copy(videos = old.videos + videos)
        seasonsSeriesSections[idx] = updated
        return updated
    }

    private fun loadMoreSectionVideos(section: UpDetailSection, state: SectionPagingState) {
        if (state.isLoading || state.endReached) return
        val token = seasonsSeriesRequestToken
        val targetPage = state.nextPage.coerceAtLeast(1)
        state.isLoading = true
        val ownerFallback = binding.tvName.text?.toString().orEmpty().trim()
        lifecycleScope.launch {
            try {
                val parsed =
                    when (section.kind) {
                        UpDetailSectionKind.SEASON -> {
                            val page =
                                BiliApi.ugcSeasonArchives(
                                    mid = mid,
                                    seasonId = section.id,
                                    pageNum = targetPage,
                                    pageSize = 30,
                                    sortReverse = false,
                                )
                            SectionArchivesParsedPage(
                                totalCount = page.totalCount,
                                videos = page.items.withOwnerFallback(ownerFallback),
                            )
                        }

                        UpDetailSectionKind.SERIES -> {
                            val page =
                                BiliApi.seriesArchives(
                                    mid = mid,
                                    seriesId = section.id,
                                    pageNum = targetPage,
                                    pageSize = 20,
                                    sort = "desc",
                                    onlyNormal = true,
                                )
                            sectionArchivesPage(
                                total = page.total,
                                videos = page.items,
                                ownerFallback = ownerFallback,
                            )
                        }
                    }
                if (token != seasonsSeriesRequestToken) return@launch

                val newVideos =
                    buildList {
                        for (v in parsed.videos) {
                            val bvid = v.bvid.trim()
                            if (bvid.isBlank()) continue
                            if (state.loadedBvids.add(bvid)) add(v)
                        }
                    }

                if (parsed.totalCount != null && parsed.totalCount > 0) {
                    state.totalCount = parsed.totalCount
                }

                state.nextPage = targetPage + 1
                if (parsed.videos.isEmpty()) state.endReached = true
                val total = state.totalCount
                if (!state.endReached && total != null && total > 0 && state.loadedBvids.size >= total) {
                    state.endReached = true
                }

                if (newVideos.isNotEmpty()) {
                    val stableId = section.stableId
                    appendVideosToSectionInMemory(stableId, newVideos)
                    sectionAdapter.appendVideos(stableId, newVideos)

                    val pendingIndex = sectionPendingAdvanceIndex.remove(stableId)
                    if (pendingIndex != null) {
                        binding.recycler.post { sectionAdapter.requestVideoFocus(stableId, pendingIndex) }
                    }
                } else {
                    sectionPendingAdvanceIndex.remove(section.stableId)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                val msg =
                    (t as? BiliApiException)?.apiMessage?.trim()?.takeIf { it.isNotBlank() }
                        ?: t.message.orEmpty()
                AppLog.w("UpDetail", "loadSectionVideos failed mid=$mid section=${section.stableId} page=$targetPage", t)
                AppToast.show(this@UpDetailActivity, msg.ifBlank { "加载失败" })
                sectionPendingAdvanceIndex.remove(section.stableId)
            } finally {
                state.isLoading = false
            }
        }
    }

    private fun loadMoreSeasonsSeries(isRefresh: Boolean = false) {
        if (seasonsSeriesIsLoadingMore || seasonsSeriesEndReached) return
        val token = seasonsSeriesRequestToken
        val targetPage = seasonsSeriesNextPage.coerceAtLeast(1)
        seasonsSeriesIsLoadingMore = true
        val ownerFallback = binding.tvName.text?.toString().orEmpty().trim()
        lifecycleScope.launch {
            try {
                val parsed = BiliApi.collectionSections(mid = mid, pageNum = targetPage, pageSize = 10)
                if (token != seasonsSeriesRequestToken) return@launch

                val hasAny = parsed.sections.isNotEmpty()
                seasonsSeriesNextPage = targetPage + 1
                seasonsSeriesEndReached =
                    when {
                        parsed.totalPages > 0 -> targetPage >= parsed.totalPages
                        else -> !hasAny
                    }

                val inserted = ArrayList<UpDetailSection>(parsed.sections.size)
                for (apiSection in parsed.sections) {
                    val section = apiSection.toUpDetailSection(ownerFallback = ownerFallback)
                    if (!loadedSectionStableIds.add(section.stableId)) continue
                    seasonsSeriesSections.add(section)
                    inserted.add(section)
                    ensureSectionPagingState(section)
                }

                if (currentTab == UpTab.COLLECTION_SERIES) {
                    if (isRefresh) sectionAdapter.replaceAll(seasonsSeriesSections) else sectionAdapter.appendSections(inserted)
                }

                binding.recycler.post {
                    // Always run: this might be triggered by DPAD_DOWN on tabs, not only pull-to-refresh.
                    maybeConsumePendingFocusFirstContentAfterLoad()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("UpDetail", "loadSeasonsSeries failed mid=$mid page=$targetPage", t)
                val msg =
                    (t as? BiliApiException)?.apiMessage?.trim()?.takeIf { it.isNotBlank() }
                        ?: t.message.orEmpty()
                AppToast.show(this@UpDetailActivity, msg.ifBlank { "加载失败" })
            } finally {
                if (token == seasonsSeriesRequestToken) binding.swipeRefresh.isRefreshing = false
                seasonsSeriesIsLoadingMore = false
            }
        }
    }

    private fun showUserExtras() {
        AppPopup.singleChoice(
            context = this,
            title = "TA 的空间",
            items = listOf("TA 的动态", "TA 的收藏"),
            checkedIndex = -1,
            onPicked = { index, _ ->
                when (index) {
                    0 -> loadUserDynamics()
                    1 -> loadUserFavorites()
                }
            },
        )
    }

    private fun loadUserDynamics() {
        if (mid <= 0L) return
        lifecycleScope.launch {
            try {
                val page = BiliApi.dynamicSpaceVideo(hostMid = mid, minCardCount = 20)
                if (page.items.isEmpty()) {
                    AppToast.show(this@UpDetailActivity, "暂无公开动态视频")
                } else {
                    showVideoCardsDialog("TA 的动态", page.items)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppToast.show(this@UpDetailActivity, "动态加载失败")
            }
        }
    }

    private fun loadUserFavorites() {
        if (mid <= 0L) return
        lifecycleScope.launch {
            try {
                val folders = BiliApi.favFolders(mid)
                if (folders.isEmpty()) {
                    AppToast.show(this@UpDetailActivity, "暂无公开收藏夹")
                    return@launch
                }
                AppPopup.singleChoice(
                    context = this@UpDetailActivity,
                    title = "TA 的收藏夹",
                    items = folders.map { "${it.title}（${it.mediaCount}）" },
                    checkedIndex = -1,
                    onPicked = { index, _ ->
                        val folder = folders.getOrNull(index) ?: return@singleChoice
                        loadFavoriteFolder(folder)
                    },
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppToast.show(this@UpDetailActivity, "收藏夹加载失败")
            }
        }
    }

    private fun loadFavoriteFolder(folder: blbl.cat3399.core.model.FavFolder) {
        lifecycleScope.launch {
            try {
                val page = BiliApi.favFolderResources(mediaId = folder.mediaId, pn = 1, ps = 20)
                if (page.items.isEmpty()) {
                    AppToast.show(this@UpDetailActivity, "收藏夹暂无视频")
                } else {
                    showVideoCardsDialog(folder.title, page.items)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppToast.show(this@UpDetailActivity, "收藏夹内容加载失败")
            }
        }
    }

    private fun showVideoCardsDialog(title: String, cards: List<VideoCard>) {
        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@UpDetailActivity, 3)
            setPadding(16, 8, 16, 8)
            clipToPadding = false
            isFocusable = true
        }
        val adapter =
            VideoCardAdapter(
                onClick = { card, _ ->
                    startActivity(
                        Intent(this@UpDetailActivity, VideoDetailActivity::class.java)
                            .putExtra(VideoDetailActivity.EXTRA_BVID, card.bvid)
                            .putExtra(VideoDetailActivity.EXTRA_AID, card.aid ?: 0L)
                            .putExtra(VideoDetailActivity.EXTRA_CID, card.cid ?: 0L),
                    )
                },
            )
        recycler.adapter = adapter
        adapter.submit(cards)
        val container =
            FrameLayout(this).apply {
                addView(
                    recycler,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (resources.displayMetrics.heightPixels * 0.65f).toInt(),
                    ),
                )
            }
        AppPopup.custom(
            context = this,
            title = title,
            cancelable = true,
            autoFocus = true,
            actions = listOf(PopupAction(role = PopupActionRole.NEGATIVE, text = "关闭")),
            content = { container },
        )
        recycler.post { recycler.requestFocus() }
    }

    private fun openRelationList(showFans: Boolean) {
        val targetMid = mid.takeIf { it > 0L } ?: return
        startActivity(
            Intent(this, FollowingListActivity::class.java)
                .putExtra(
                    FollowingListActivity.EXTRA_MODE,
                    if (showFans) FollowingListActivity.MODE_FANS else FollowingListActivity.MODE_FOLLOWING,
                )
                .putExtra(FollowingListActivity.EXTRA_VMID, targetMid)
                .putExtra(
                    FollowingListActivity.EXTRA_TITLE,
                    if (showFans) "TA的粉丝" else "TA的关注",
                ),
        )
    }

    private fun onFollowClicked() {
        if (!BiliClient.cookies.hasSessData()) {
            startActivity(Intent(this, QrLoginActivity::class.java))
            AppToast.show(this, "登录后才能关注")
            return
        }
        if (followActionInFlight) return
        val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")?.trim()?.toLongOrNull()
        if (selfMid != null && selfMid == mid) return

        val wantFollow = !isFollowed
        followActionInFlight = true
        updateFollowUi()
        lifecycleScope.launch {
            try {
                BiliApi.modifyRelation(fid = mid, act = if (wantFollow) 1 else 2, reSrc = 11)
                isFollowed = wantFollow
                AppToast.show(this@UpDetailActivity, if (wantFollow) "已关注" else "已取关")
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.w("UpDetail", "modifyRelation failed mid=$mid wantFollow=$wantFollow", t)
                val raw =
                    (t as? BiliApiException)?.apiMessage?.takeIf { it.isNotBlank() }
                        ?: t.message.orEmpty()
                val msg =
                    when (raw) {
                        "missing_csrf" -> "登录态不完整，请重新登录"
                        else -> raw
                    }
                AppToast.show(this@UpDetailActivity, if (msg.isBlank()) "操作失败" else msg)
            } finally {
                followActionInFlight = false
                updateFollowUi()
                loadHeader()
            }
        }
    }

    private fun updateFollowUi() {
        val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")?.trim()?.toLongOrNull()
        val isSelf = selfMid != null && selfMid == mid
        binding.btnFollow.isVisible = !isSelf
        if (isSelf) return

        binding.btnFollow.isEnabled = !followActionInFlight
        binding.btnFollow.text = if (isFollowed) "已关注" else "关注"

        val bg =
            if (isFollowed) {
                ThemeColor.resolve(this, MaterialR.attr.colorSurface, R.color.blbl_surface)
            } else {
                ThemeColor.resolve(this, R.attr.blblAccent, R.color.blbl_purple)
            }
        val fg =
            if (isFollowed) {
                ThemeColor.resolve(this, android.R.attr.textColorSecondary, R.color.blbl_text_secondary)
            } else {
                ThemeColor.resolve(this, MaterialR.attr.colorOnSecondary, R.color.blbl_text)
            }
        binding.btnFollow.backgroundTintList = ColorStateList.valueOf(bg)
        binding.btnFollow.setTextColor(fg)
    }

    private fun focusGridAt(position: Int) {
        val recycler = binding.recycler
        recycler.post outer@{
            if (isFinishing || isDestroyed) return@outer
            val vh = recycler.findViewHolderForAdapterPosition(position)
            if (vh != null) {
                vh.itemView.requestFocus()
                return@outer
            }
            recycler.scrollToPosition(position)
            recycler.post inner@{
                if (isFinishing || isDestroyed) return@inner
                recycler.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() ?: recycler.requestFocus()
            }
        }
    }

    private fun focusFirstSectionVideo() {
        val recycler = binding.recycler
        recycler.post outer@{
            if (isFinishing || isDestroyed) return@outer

            val outerVh = recycler.findViewHolderForAdapterPosition(0)
            val innerRecycler =
                outerVh?.itemView?.findViewById<RecyclerView>(R.id.recycler_videos)
            if (innerRecycler != null) {
                val innerVh = innerRecycler.findViewHolderForAdapterPosition(0)
                if (innerVh != null) {
                    innerVh.itemView.requestFocus()
                    return@outer
                }
                innerRecycler.scrollToPosition(0)
                innerRecycler.post {
                    if (isFinishing || isDestroyed) return@post
                    innerRecycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: innerRecycler.requestFocus()
                }
                return@outer
            }

            recycler.scrollToPosition(0)
            recycler.post inner@{
                if (isFinishing || isDestroyed) return@inner
                val vh = recycler.findViewHolderForAdapterPosition(0)
                val inner =
                    vh?.itemView?.findViewById<RecyclerView>(R.id.recycler_videos)
                        ?: return@inner
                inner.scrollToPosition(0)
                inner.post {
                    if (isFinishing || isDestroyed) return@post
                    inner.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: inner.requestFocus()
                }
            }
        }
    }

    private fun maybeConsumePendingFocusFirstContentAfterLoad() {
        if (!hasWindowFocus()) return

        val force = forceFocusFirstContentAfterRefresh
        if (!force) {
            val focused = currentFocus
            if (focused != null && focused != binding.recycler && FocusTreeUtils.isDescendantOf(focused, binding.recycler)) {
                pendingFocusFirstArchiveItemAfterLoad = false
                pendingFocusFirstSectionItemAfterLoad = false
                return
            }
        }

        when (currentTab) {
            UpTab.ARCHIVE,
            UpTab.DYNAMIC,
            UpTab.FAVORITES,
            -> {
                if (!force && !pendingFocusFirstArchiveItemAfterLoad) return
                if (archiveAdapter.itemCount <= 0) return
                pendingFocusFirstArchiveItemAfterLoad = false
                forceFocusFirstContentAfterRefresh = false
                focusGridAt(0)
            }

            UpTab.COLLECTION_SERIES -> {
                if (!force && !pendingFocusFirstSectionItemAfterLoad) return
                if (seasonsSeriesSections.isEmpty()) return
                pendingFocusFirstSectionItemAfterLoad = false
                forceFocusFirstContentAfterRefresh = false
                focusFirstSectionVideo()
            }
        }
    }

    private fun parkFocusInRecyclerForDataSetReset() {
        val focused = currentFocus
        if (focused != null && focused != binding.recycler && !FocusTreeUtils.isDescendantOf(focused, binding.recycler)) return
        val recycler = binding.recycler
        val original = recycler.descendantFocusability
        recycler.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        recycler.requestFocus()
        recycler.descendantFocusability = original
    }

    private fun installArchiveGridController() {
        if (archiveGridController != null) return
        archiveGridController?.release()
        archiveGridController =
            DpadGridController(
                recyclerView = binding.recycler,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            return focusTabsFromContentEdge()
                        }

                        override fun onLeftEdge(): Boolean {
                            return switchToPrevTabFromContentEdge()
                        }

                        override fun onRightEdge() {
                            switchToNextTabFromContentEdge()
                        }

                        override fun canLoadMore(): Boolean = !gridTabEndReached()

                        override fun loadMore() {
                            loadMoreGridTab()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { !isFinishing && !isDestroyed && isGridTab(currentTab) },
                        enableCenterLongPressToLongClick = true,
                        // Let DPAD_UP bubble if we couldn't move focus to the tab strip. Consuming
                        // unconditionally creates a focus trap (can't reach header/tab).
                        consumeUpAtTopEdge = false,
                    ),
            ).also { it.install() }
    }

    private fun releaseArchiveGridController() {
        archiveGridController?.release()
        archiveGridController = null
    }

    private fun switchToNextTabFromContentEdge(): Boolean {
        val cur = binding.tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
        val next = cur + 1
        if (next >= binding.tabLayout.tabCount) return false
        binding.tabLayout.getTabAt(next)?.select() ?: return false
        binding.recycler.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) { requestFocusFirstContentFromTab() }
        return true
    }

    private fun switchToPrevTabFromContentEdge(): Boolean {
        val cur = binding.tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
        val prev = cur - 1
        if (prev < 0) return false
        binding.tabLayout.getTabAt(prev)?.select() ?: return false
        binding.recycler.postIfAlive(isAlive = { !isFinishing && !isDestroyed }) { requestFocusFirstContentFromTab() }
        return true
    }

    private fun archivePlaybackHandle() =
        buildPagedVideoCardPlaybackHandle(
            source = "UpDetail:$mid:archive",
            cardsProvider = archiveAdapter::snapshot,
            nextCursorProvider = { archiveNextPage },
            hasMoreProvider = { !archiveEndReached },
        ) { pageNum ->
            val targetPage = pageNum.coerceAtLeast(1)
            val page = BiliApi.spaceArcSearchPage(mid = mid, pn = targetPage, ps = 30)
            VideoCardPlaylistPage(
                cards = page.items,
                nextCursor = targetPage + 1,
                hasMore = page.hasMore,
                canAdvance = page.hasMore && page.items.isNotEmpty(),
            )
        }

    private fun buildSectionPlaybackHandle(section: UpDetailSection) =
        buildVideoCardPlaybackHandle(
            source = "UpDetail:$mid:${section.stableId}",
            cardsProvider = { section.videos },
        ) { cards ->
            buildSectionPlaylistContinuation(section, cards)
        }

    private fun buildSectionPlaylistContinuation(
        section: UpDetailSection,
        cards: List<VideoCard>,
    ): PlayerPlaylistContinuation? {
        val state = sectionPagingStates[section.stableId] ?: return null
        val pageSize = if (section.kind == UpDetailSectionKind.SEASON) 30 else 20
        val ownerFallback = binding.tvName.text?.toString().orEmpty().trim()
        return buildFreshVideoCardPlaylistContinuation(
            seedCards = cards,
            nextCursor = state.nextPage,
            hasMore = !state.endReached,
            playlistItemFactory = ::defaultVideoCardPlaylistItem,
        ) { pageNum ->
            val safePageNum = pageNum.coerceAtLeast(1)
            val parsedPage =
                when (section.kind) {
                    UpDetailSectionKind.SEASON -> {
                        val page =
                            BiliApi.ugcSeasonArchives(
                                mid = mid,
                                seasonId = section.id,
                                pageNum = safePageNum,
                                pageSize = pageSize,
                                sortReverse = false,
                            )
                        SectionArchivesParsedPage(
                            totalCount = page.totalCount,
                            videos = page.items.withOwnerFallback(ownerFallback),
                        )
                    }

                    UpDetailSectionKind.SERIES -> {
                        val page =
                            BiliApi.seriesArchives(
                                mid = mid,
                                seriesId = section.id,
                                pageNum = safePageNum,
                                pageSize = pageSize,
                                sort = "desc",
                                onlyNormal = true,
                            )
                        sectionArchivesPage(
                            total = page.total,
                            videos = page.items,
                            ownerFallback = ownerFallback,
                        )
                    }
                }
            val totalCount = parsedPage.totalCount ?: state.totalCount
            val hasMore =
                totalCount?.let { safePageNum * pageSize < it }
                    ?: (parsedPage.videos.size >= pageSize)
            VideoCardPlaylistPage(
                cards = parsedPage.videos,
                nextCursor = safePageNum + 1,
                hasMore = hasMore,
                canAdvance = hasMore && parsedPage.videos.isNotEmpty(),
            )
        }
    }

    private fun openUpDetailForCard(card: blbl.cat3399.core.model.VideoCard) {
        val targetMid = card.ownerMid?.takeIf { it > 0L } ?: mid
        if (targetMid == mid) return
        startActivity(
            Intent(this, UpDetailActivity::class.java)
                .putExtra(EXTRA_MID, targetMid)
                .apply {
                    card.ownerName.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_NAME, it) }
                    card.ownerFace?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_AVATAR, it) }
                },
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

    private fun isNavKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_TAB,
            -> true

            else -> false
        }
    }

    companion object {
        const val EXTRA_MID: String = "mid"
        const val EXTRA_NAME: String = "name"
        const val EXTRA_AVATAR: String = "avatar"
        const val EXTRA_SIGN: String = "sign"
        private const val ACTIVITY_STACK_GROUP: String = "player_up_flow"
        private const val ACTIVITY_STACK_MAX_DEPTH: Int = 3
    }
}
