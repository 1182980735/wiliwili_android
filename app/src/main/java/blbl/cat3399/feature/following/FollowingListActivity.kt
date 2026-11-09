package blbl.cat3399.feature.following

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.Following
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.paging.PagedGridStateMachine
import blbl.cat3399.core.paging.appliedOrNull
import blbl.cat3399.core.tv.RemoteKeys
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.core.ui.popup.AppPopup
import blbl.cat3399.core.ui.popup.PopupAction
import blbl.cat3399.core.ui.popup.PopupActionRole
import blbl.cat3399.core.ui.popup.PopupHandle
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ActivityFollowingListBinding
import blbl.cat3399.feature.login.QrLoginActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FollowingListActivity : BaseActivity() {
    private lateinit var binding: ActivityFollowingListBinding
    private lateinit var adapter: FollowingGridAdapter

    private var vmid: Long = 0L
    private var mode: String = MODE_FOLLOWING
    private var titleOverride: String? = null
    private var forceLoginUi: Boolean = false
    private var total: Int = 0
    private val loadedMids = HashSet<Long>()
    private val paging = PagedGridStateMachine(initialKey = 1)
    private var currentFollowingListOrder: String = BiliClient.prefs.followingListOrder
    private var selectedTagId: Long? = null
    private var selectedTagName: String = "全部"

    private var dpadGridController: DpadGridController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFollowingListBinding.inflate(layoutInflater.cloneInUserScale(this))
        setContentView(binding.root)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        mode = intent.getStringExtra(EXTRA_MODE)?.trim()?.takeIf { it == MODE_FANS } ?: MODE_FOLLOWING
        vmid = intent.getLongExtra(EXTRA_VMID, 0L).takeIf { it > 0L } ?: 0L
        titleOverride = intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf { it.isNotBlank() }
        binding.tvTitle.text = titleOverride ?: if (mode == MODE_FANS) "我的粉丝" else "我的关注"
        binding.tvTip.text = if (mode == MODE_FANS) "粉丝列表需要登录" else "关注列表需要登录"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLogin.setOnClickListener { startActivity(Intent(this, QrLoginActivity::class.java)) }
        binding.btnGroup.visibility = if (mode == MODE_FANS) View.GONE else View.VISIBLE
        binding.btnGroup.setOnClickListener { showGroupPicker() }

        adapter =
            FollowingGridAdapter { following ->
                startActivity(
                    Intent(this, UpDetailActivity::class.java)
                        .putExtra(UpDetailActivity.EXTRA_MID, following.mid)
                        .putExtra(UpDetailActivity.EXTRA_NAME, following.name)
                        .putExtra(UpDetailActivity.EXTRA_AVATAR, following.avatarUrl)
                        .putExtra(UpDetailActivity.EXTRA_SIGN, following.sign),
                )
            }

        binding.recycler.setHasFixedSize(true)
        binding.recycler.layoutManager = GridLayoutManager(this, spanCountForWidth())
        binding.recycler.adapter = adapter
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        binding.recycler.clearOnScrollListeners()
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val s = paging.snapshot()
                    if (s.isLoading || s.endReached) return
                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val totalCount = adapter.itemCount
                    if (totalCount <= 0) return
                    if (totalCount - lastVisible - 1 <= 12) loadNextPage()
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
                            binding.btnBack.requestFocus()
                            return true
                        }

                        override fun onLeftEdge(): Boolean {
                            // Allow default focus-search (e.g. to back button if the system picks it).
                            return false
                        }

                        override fun onRightEdge() = Unit

                        override fun canLoadMore(): Boolean = !paging.snapshot().endReached

                        override fun loadMore() {
                            loadNextPage()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { !isFinishing && !isDestroyed },
                    ),
            ).also { it.install() }

        binding.swipeRefresh.setOnRefreshListener { resetAndLoad() }

        refreshLoginUi()
        if (binding.loginContainer.isVisible) {
            binding.btnLogin.requestFocus()
        } else {
            binding.swipeRefresh.isRefreshing = true
            resetAndLoad()
        }
    }

    override fun onResume() {
        super.onResume()
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)
        adapter.invalidateSizing()
        (binding.recycler.layoutManager as? GridLayoutManager)?.spanCount = spanCountForWidth()
        // If user just came back from login, allow re-checking.
        forceLoginUi = false
        refreshLoginUi()
        if (!binding.loginContainer.isVisible && adapter.itemCount == 0 && !binding.swipeRefresh.isRefreshing) {
            binding.swipeRefresh.isRefreshing = true
            resetAndLoad()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && RemoteKeys.isRefreshKey(event.keyCode)) {
            if (binding.loginContainer.isVisible) {
                AppToast.show(this, "请先登录")
                binding.btnLogin.requestFocus()
                return true
            }
            if (binding.swipeRefresh.isRefreshing) return true
            binding.swipeRefresh.isRefreshing = true
            resetAndLoad()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && currentFocus == null && isNavKey(event.keyCode)) {
            ensureInitialFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun refreshLoginUi() {
        val loggedIn = !forceLoginUi && BiliClient.cookies.hasSessData()
        binding.loginContainer.isVisible = !loggedIn
        binding.swipeRefresh.isVisible = loggedIn
        if (!loggedIn) {
            binding.swipeRefresh.isRefreshing = false
            vmid = 0L
        }
    }

    private fun ensureInitialFocus() {
        if (currentFocus != null) return
        if (binding.loginContainer.isVisible) {
            binding.btnLogin.requestFocus()
            return
        }
        if (adapter.itemCount <= 0) {
            binding.recycler.requestFocus()
            return
        }
        focusGridAt(0)
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

    private fun resetAndLoad() {
        currentFollowingListOrder = BiliClient.prefs.followingListOrder
        paging.reset()
        loadedMids.clear()
        total = 0
        dpadGridController?.clearPendingFocusAfterLoadMore()
        adapter.submit(emptyList())
        loadNextPage(isRefresh = true)
    }

    private data class FetchedPage(
        val items: List<Following>,
        val total: Int,
        val hasMore: Boolean,
    )

    private fun loadNextPage(isRefresh: Boolean = false) {
        val startSnap = paging.snapshot()
        if (startSnap.isLoading || startSnap.endReached) return
        val startGen = startSnap.generation
        val startPage = startSnap.nextKey
        lifecycleScope.launch {
            try {
                var latestFetched: FetchedPage? = null
                val result =
                    paging.loadNextPage(
                        isRefresh = isRefresh,
                        fetch = { page ->
                            val mid = ensureUserMid()
                            if (mid == null) {
                                null
                            } else {
                                val res =
                                    if (mode == MODE_FANS) {
                                        BiliApi.followersPage(vmid = mid, pn = page, ps = 50)
                                    } else {
                                        val tagId = selectedTagId
                                        if (tagId != null) {
                                            BiliApi.followingsByTagPage(tagId = tagId, pn = page, ps = 50)
                                        } else {
                                            BiliApi.followingsPage(vmid = mid, pn = page, ps = 50, order = currentFollowingListOrder)
                                        }
                                    }
                                FetchedPage(items = res.items, total = res.total, hasMore = res.hasMore).also { latestFetched = it }
                            }
                        },
                        reduce = { page, fetched ->
                            if (fetched.items.isEmpty()) {
                                PagedGridStateMachine.Update(
                                    items = emptyList<Following>(),
                                    nextKey = page,
                                    endReached = true,
                                )
                            } else {
                                val seen = HashSet<Long>(fetched.items.size)
                                val filtered =
                                    fetched.items.filter {
                                        if (loadedMids.contains(it.mid)) return@filter false
                                        seen.add(it.mid)
                                    }
                                PagedGridStateMachine.Update(
                                    items = filtered,
                                    nextKey = page + 1,
                                    endReached = !fetched.hasMore,
                                )
                            }
                        },
                    )

                val applied = result.appliedOrNull() ?: return@launch
                val fetched = latestFetched ?: return@launch
                total = fetched.total
                if (fetched.items.isEmpty()) {
                    if (applied.isRefresh) {
                        AppToast.show(this@FollowingListActivity, if (mode == MODE_FANS) "暂无粉丝" else "暂无关注")
                    }
                    return@launch
                }
                applied.items.forEach { loadedMids.add(it.mid) }
                if (applied.isRefresh) adapter.submit(applied.items) else adapter.append(applied.items)
                binding.recycler.post { dpadGridController?.consumePendingFocusAfterLoadMore() }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("FollowingList", "load failed page=$startPage", t)
                AppToast.show(this@FollowingListActivity, "加载失败，可查看 Logcat(标签 BLBL)")
            } finally {
                if (paging.snapshot().generation == startGen) binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showGroupPicker() {
        lifecycleScope.launch {
            try {
                val tags = BiliApi.followingTags()
                val names = ArrayList<String>(tags.size + 4)
                names.add("全部")
                tags.forEach { names.add(it.name + "（" + it.count + "）") }
                val createIndex = names.size
                names.add("＋ 新建分组")
                val renameIndex = names.size
                names.add("重命名当前分组…")
                val deleteIndex = names.size
                names.add("删除当前分组…")
                val checked = if (selectedTagId == null) 0 else tags.indexOfFirst { it.tagId == selectedTagId } + 1
                var handle: PopupHandle? = null
                handle =
                    AppPopup.singleChoice(
                        context = this@FollowingListActivity,
                        title = "关注分组",
                        items = names,
                        checkedIndex = checked.coerceAtLeast(0),
                        onPicked = { index, _ ->
                            handle?.dismiss()
                            when (index) {
                                createIndex -> {
                                    showCreateGroupDialog()
                                    return@singleChoice
                                }
                                renameIndex -> {
                                    val tag = tags.firstOrNull { it.tagId == selectedTagId }
                                    if (tag == null) {
                                        AppToast.show(this@FollowingListActivity, "请先进入一个分组")
                                    } else {
                                        showRenameGroupDialog(tag)
                                    }
                                    return@singleChoice
                                }
                                deleteIndex -> {
                                    val tag = tags.firstOrNull { it.tagId == selectedTagId }
                                    if (tag == null) {
                                        AppToast.show(this@FollowingListActivity, "请先进入一个分组")
                                    } else {
                                        showDeleteGroupConfirm(tag)
                                    }
                                    return@singleChoice
                                }
                            }
                            if (index == 0) {
                                selectedTagId = null
                                selectedTagName = "全部"
                            } else {
                                val tag = tags.getOrNull(index - 1) ?: return@singleChoice
                                selectedTagId = tag.tagId
                                selectedTagName = tag.name
                            }
                            binding.btnGroup.text = "分组：" + selectedTagName
                            binding.swipeRefresh.isRefreshing = true
                            resetAndLoad()
                        },
                    )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppToast.show(this@FollowingListActivity, "分组加载失败")
            }
        }
    }

    private fun showCreateGroupDialog() {
        AppPopup.input(
            context = this,
            title = "新建关注分组",
            initial = "",
            hint = "输入分组名称",
            onPositive = { text ->
                val name = text.trim()
                if (name.isBlank()) return@input
                lifecycleScope.launch {
                    try {
                        BiliApi.createFollowingTag(name)
                        AppToast.show(this@FollowingListActivity, "已创建分组：$name")
                        showGroupPicker()
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppToast.show(this@FollowingListActivity, groupError(t))
                    }
                }
            },
        )
    }

    private fun showRenameGroupDialog(tag: BiliApi.FollowingTag) {
        AppPopup.input(
            context = this,
            title = "重命名分组",
            initial = tag.name,
            hint = "输入新分组名称",
            onPositive = { text ->
                val name = text.trim()
                if (name.isBlank()) return@input
                lifecycleScope.launch {
                    try {
                        BiliApi.renameFollowingTag(tag.tagId, name)
                        selectedTagName = name
                        binding.btnGroup.text = "分组：$name"
                        AppToast.show(this@FollowingListActivity, "分组已重命名")
                        showGroupPicker()
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppToast.show(this@FollowingListActivity, groupError(t))
                    }
                }
            },
        )
    }

    private fun showDeleteGroupConfirm(tag: BiliApi.FollowingTag) {
        AppPopup.confirm(
            context = this,
            title = "删除关注分组",
            message = "确定删除分组“${tag.name}”吗？分组内的关注不会被取消。",
            positiveText = "删除",
            negativeText = "取消",
            onPositive = {
                lifecycleScope.launch {
                    try {
                        BiliApi.deleteFollowingTag(tag.tagId)
                        selectedTagId = null
                        selectedTagName = "全部"
                        binding.btnGroup.text = "分组：全部"
                        binding.swipeRefresh.isRefreshing = true
                        resetAndLoad()
                        AppToast.show(this@FollowingListActivity, "分组已删除")
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        AppToast.show(this@FollowingListActivity, groupError(t))
                    }
                }
            },
        )
    }

    private fun groupError(t: Throwable): String {
        val raw = (t as? blbl.cat3399.core.api.BiliApiException)?.apiMessage?.trim().orEmpty()
            .ifBlank { t.message.orEmpty() }
        return when (raw) {
            "missing_csrf" -> "登录态缺少 CSRF，请重新登录"
            "" -> "分组操作失败"
            else -> raw
        }
    }

    private suspend fun ensureUserMid(): Long? {
        if (vmid > 0) return vmid
        return runCatching {
            val nav = BiliApi.nav()
            val data = nav.optJSONObject("data")
            val isLogin = data?.optBoolean("isLogin") ?: false
            val mid = data?.optLong("mid") ?: 0L
            if (!isLogin || mid <= 0) {
                forceLoginUi = true
                refreshLoginUi()
                AppToast.show(this, "登录态失效，请重新登录")
                null
            } else {
                vmid = mid
                mid
            }
        }.getOrElse {
            AppLog.w("FollowingList", "nav failed", it)
            null
        }
    }

    private fun spanCountForWidth(): Int {
        return followingSpanCountForWidth(binding.root.context)
    }

    override fun onDestroy() {
        dpadGridController?.release()
        dpadGridController = null
        super.onDestroy()
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
        const val EXTRA_MODE: String = "following_mode"
        const val EXTRA_VMID: String = "following_vmid"
        const val EXTRA_TITLE: String = "following_title"
        const val MODE_FOLLOWING: String = "following"
        const val MODE_FANS: String = "fans"
    }
}
