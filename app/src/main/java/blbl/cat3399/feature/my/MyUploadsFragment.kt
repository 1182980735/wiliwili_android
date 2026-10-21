package blbl.cat3399.feature.my

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.requestFocusFirstItemOrSelfAfterRefresh
import blbl.cat3399.databinding.FragmentVideoGridBinding
import blbl.cat3399.feature.following.openUpDetailFromVideoCard
import blbl.cat3399.feature.player.PlayerActivity
import blbl.cat3399.feature.video.VideoCardActionController
import blbl.cat3399.feature.video.VideoCardAdapter
import blbl.cat3399.feature.video.VideoCardDismissBehavior
import blbl.cat3399.feature.video.VideoCardVisibilityFilter
import blbl.cat3399.feature.video.buildVideoCardPlaylistToken
import blbl.cat3399.feature.video.openVideoDetailFromCards
import blbl.cat3399.feature.video.removeVideoCardAndRestoreFocus
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MyUploadsFragment : Fragment(), MyTabSwitchFocusTarget, RefreshKeyHandler {
    private var _binding: FragmentVideoGridBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoCardAdapter
    private var initialLoadTriggered: Boolean = false
    private var requestToken: Int = 0
    private var pendingFocusFirstItemFromTabSwitch: Boolean = false
    private var pendingFocusFirstItemAfterRefresh: Boolean = false
    private var dpadGridController: DpadGridController? = null
    private var userMid: Long = 0L
    private var nextPage: Int = 1
    private var endReached: Boolean = false
    private var loadingPage: Boolean = false
    private val loadedKeys = HashSet<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVideoGridBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (!::adapter.isInitialized) {
            val actionController =
                VideoCardActionController(
                    context = requireContext(),
                    scope = viewLifecycleOwner.lifecycleScope,
                    dismissBehavior = VideoCardDismissBehavior.LocalNotInterested,
                    onOpenDetail = { _, pos -> openDetail(pos) },
                    onOpenUp = { card -> openUpDetailFromVideoCard(card) },
                    onCardRemoved = { stableKey ->
                        _binding?.recycler?.removeVideoCardAndRestoreFocus(
                            adapter = adapter,
                            stableKey = stableKey,
                            isAlive = { _binding != null && isResumed },
                        )
                    },
                )
            adapter =
                VideoCardAdapter(
                    onClick = { card, pos ->
                        val cards = adapter.snapshot()
                        if (BiliClient.prefs.playerOpenDetailBeforePlay) {
                            requireContext().openVideoDetailFromCards(
                                cards = cards,
                                position = pos,
                                source = "MyUploads",
                            )
                        } else {
                            val token =
                                cards.buildVideoCardPlaylistToken(
                                    index = pos,
                                    source = "MyUploads",
                                ) ?: return@VideoCardAdapter
                            startActivity(
                                Intent(requireContext(), PlayerActivity::class.java)
                                    .putExtra(PlayerActivity.EXTRA_BVID, card.bvid)
                                    .putExtra(PlayerActivity.EXTRA_CID, card.cid ?: -1L)
                                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_TOKEN, token)
                                    .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, pos),
                            )
                        }
                    },
                    onLongClick = { card, _ ->
                        openUpDetailFromVideoCard(card)
                        true
                    },
                    actionDelegate = actionController,
                )
        }
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), spanCountForWidth(resources))
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recycler.clearOnScrollListeners()
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || loadingPage || endReached) return
                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = adapter.itemCount
                    if (total <= 0) return
                    if (total - lastVisible - 1 <= 8) loadNextPage()
                }
            },
        )
        dpadGridController?.release()
        dpadGridController =
            DpadGridController(
                recyclerView = binding.recycler,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            focusSelectedMyTabIfAvailable()
                            return true
                        }

                        override fun onLeftEdge(): Boolean {
                            return switchToPrevMyTabFromContentEdge()
                        }

                        override fun onRightEdge() {
                            switchToNextMyTabFromContentEdge()
                        }

                        override fun canLoadMore(): Boolean = endReached.not()

                        override fun loadMore() {
                            loadNextPage()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { _binding != null && isResumed },
                        enableCenterLongPressToLongClick = true,
                    ),
            ).also { it.install() }
        binding.swipeRefresh.setOnRefreshListener {
            pendingFocusFirstItemAfterRefresh = true
            dpadGridController?.parkFocusForDataSetReset()
            resetAndLoad()
        }
    }

    override fun onResume() {
        super.onResume()
        (binding.recycler.layoutManager as? GridLayoutManager)?.spanCount = spanCountForWidth(resources)
        maybeTriggerInitialLoad()
        maybeConsumePendingFocusFirstItemFromTabSwitch()
    }

    override fun handleRefreshKey(): Boolean {
        val b = _binding ?: return false
        if (!isResumed) return false
        if (b.swipeRefresh.isRefreshing) return true
        b.swipeRefresh.isRefreshing = true
        pendingFocusFirstItemAfterRefresh = true
        dpadGridController?.parkFocusForDataSetReset()
        resetAndLoad()
        return true
    }

    override fun requestFocusFirstItemFromTabSwitch(): Boolean {
        pendingFocusFirstItemFromTabSwitch = true
        if (!isResumed) return true
        return maybeConsumePendingFocusFirstItemFromTabSwitch()
    }

    private fun maybeConsumePendingFocusFirstItemFromTabSwitch(): Boolean {
        if (!pendingFocusFirstItemFromTabSwitch) return false
        if (!isAdded || _binding == null) return false
        if (!isResumed) return false
        if (!this::adapter.isInitialized) return false

        val focused = activity?.currentFocus
        if (focused != null && focused != binding.recycler && FocusTreeUtils.isDescendantOf(focused, binding.recycler)) {
            pendingFocusFirstItemFromTabSwitch = false
            return false
        }

        if (adapter.itemCount <= 0) {
            binding.recycler.requestFocus()
            return true
        }

        val recycler = binding.recycler
        recycler.postIfAlive(isAlive = { _binding != null }) {
            val vh = recycler.findViewHolderForAdapterPosition(0)
            if (vh != null) {
                vh.itemView.requestFocus()
                pendingFocusFirstItemFromTabSwitch = false
                return@postIfAlive
            }
            recycler.scrollToPosition(0)
            recycler.postIfAlive(isAlive = { _binding != null }) {
                recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() ?: recycler.requestFocus()
                pendingFocusFirstItemFromTabSwitch = false
            }
        }
        return true
    }

    private fun maybeTriggerInitialLoad() {
        if (initialLoadTriggered) return
        if (!this::adapter.isInitialized) return
        if (adapter.itemCount != 0) {
            initialLoadTriggered = true
            return
        }
        if (binding.swipeRefresh.isRefreshing) return
        binding.swipeRefresh.isRefreshing = true
        resetAndLoad()
        initialLoadTriggered = true
    }

    private fun resetAndLoad() {
        requestToken++
        loadingPage = false
        userMid = 0L
        nextPage = 1
        endReached = false
        loadedKeys.clear()
        dpadGridController?.clearPendingFocusAfterLoadMore()
        adapter.submit(emptyList())
        loadPage(page = 1, token = requestToken, isFirstPage = true)
    }

    private fun loadNextPage() {
        if (loadingPage || endReached) return
        if (adapter.itemCount <= 0) return
        loadPage(page = nextPage, token = requestToken, isFirstPage = false)
    }

    private fun loadPage(page: Int, token: Int, isFirstPage: Boolean) {
        if (loadingPage) return
        loadingPage = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mid = userMid.takeIf { it > 0L } ?: run {
                    val nav = BiliApi.nav()
                    nav.optJSONObject("data")?.optLong("mid")?.also { userMid = it } ?: 0L
                }
                if (mid <= 0L) error("invalid mid")
                val result = BiliApi.spaceArcSearchPage(mid = mid, pn = page, ps = 30, order = "pubdate")
                if (token != requestToken) return@launch
                val visible = VideoCardVisibilityFilter.filterVisible(result.items)
                    .filter { loadedKeys.add(it.stableKey()) }
                if (isFirstPage) adapter.submit(visible) else adapter.append(visible)
                nextPage = page + 1
                endReached = result.hasMore.not() || result.items.isEmpty()
                _binding?.recycler?.postIfAlive(isAlive = { _binding != null }) {
                    if (isFirstPage) {
                        if (pendingFocusFirstItemAfterRefresh) {
                            pendingFocusFirstItemAfterRefresh = false
                            val recycler = binding.recycler
                            val isUiAlive = { _binding != null && isResumed }
                            recycler.requestFocusFirstItemOrSelfAfterRefresh(
                                itemCount = adapter.itemCount,
                                smoothScroll = false,
                                isAlive = isUiAlive,
                            )
                        } else {
                            maybeConsumePendingFocusFirstItemFromTabSwitch()
                        }
                    }
                    dpadGridController?.consumePendingFocusAfterLoadMore()
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("MyUploads", "load uploads page=$page failed", t)
                if (isFirstPage && token == requestToken) {
                    adapter.submit(emptyList())
                }
                context?.let { AppToast.show(it, "投稿内容加载失败，可下拉重试") }
            } finally {
                if (token == requestToken) {
                    loadingPage = false
                    _binding?.swipeRefresh?.isRefreshing = false
                }
            }
        }
    }

    override fun onDestroyView() {
        initialLoadTriggered = false
        loadingPage = false
        nextPage = 1
        endReached = false
        loadedKeys.clear()
        dpadGridController?.release()
        dpadGridController = null
        _binding = null
        super.onDestroyView()
    }

    private fun openDetail(position: Int) {
        requireContext().openVideoDetailFromCards(
            cards = adapter.snapshot(),
            position = position,
            source = "MyUploads",
        )
    }
}
