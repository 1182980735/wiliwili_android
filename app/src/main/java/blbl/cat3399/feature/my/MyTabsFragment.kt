package blbl.cat3399.feature.my

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.ui.enableDpadTabFocus
import blbl.cat3399.core.ui.findCurrentViewPagerChildFragment
import blbl.cat3399.core.ui.findCurrentViewPagerChildFragmentAs
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.requestFocusSelectedTab
import blbl.cat3399.databinding.FragmentMyTabsBinding
import blbl.cat3399.ui.BackPressHandler
import com.google.android.material.tabs.TabLayoutMediator
import blbl.cat3399.ui.RefreshKeyHandler
import blbl.cat3399.ui.SidebarFocusHost
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class MyTabsFragment : Fragment(), MyTabContentSwitchFocusHost, BackPressHandler {
    private var _binding: FragmentMyTabsBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private var pageCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null
    private var tabs: List<MyTabSpec> = emptyList()
    private var pendingFocusFirstItemFromContentSwitch: Boolean = false
    private var profileLoadJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setTabs(MyTabs.visibleTabs(BiliClient.prefs), force = true)
        binding.tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = Unit

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) {
                    refreshCurrentPageFromTabReselect()
                }
            },
        )

        pageCallback =
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val tab = tabs.getOrNull(position)
                    AppLog.d("My", "page selected pos=$position key=${tab?.key} t=${SystemClock.uptimeMillis()}")
                    if (pendingFocusFirstItemFromContentSwitch) {
                        if (focusCurrentPageFirstItemFromContentSwitch()) {
                            pendingFocusFirstItemFromContentSwitch = false
                        }
                    }
                }
            }
        binding.viewPager.registerOnPageChangeCallback(pageCallback!!)
        loadProfileHeader()
    }

    override fun onResume() {
        super.onResume()
        setTabs(MyTabs.visibleTabs(BiliClient.prefs))
        loadProfileHeader()
    }

    private fun setTabs(nextTabs: List<MyTabSpec>, force: Boolean = false) {
        val b = _binding ?: return
        val next = nextTabs.ifEmpty { MyTabs.all }
        if (!force && tabs.map { it.key } == next.map { it.key }) return

        val currentKey = tabs.getOrNull(b.viewPager.currentItem)?.key
        mediator?.detach()
        mediator = null
        tabs = next

        b.viewPager.adapter = MyPagerAdapter(this, tabs)
        mediator =
            TabLayoutMediator(b.tabLayout, b.viewPager) { tab, position ->
                tab.text = tabs.getOrNull(position)?.let { getString(it.titleRes) }.orEmpty()
            }.also { it.attach() }

        val targetIndex = currentKey?.let { key -> tabs.indexOfFirst { it.key == key }.takeIf { it >= 0 } } ?: 0
        if (targetIndex != b.viewPager.currentItem && targetIndex in tabs.indices) {
            b.viewPager.setCurrentItem(targetIndex, false)
        }

        installTabFocusHandlers(b)
    }

    private fun installTabFocusHandlers(b: FragmentMyTabsBinding) {
        val tabLayout = b.tabLayout
        tabLayout.postIfAlive(isAlive = { _binding === b }) {
            tabLayout.enableDpadTabFocus(selectOnFocusProvider = { BiliClient.prefs.tabSwitchFollowsFocus }) { position ->
                val tab = tabs.getOrNull(position)
                AppLog.d("My", "tab focus pos=$position key=${tab?.key} t=${SystemClock.uptimeMillis()}")
            }
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@postIfAlive
            for (i in 0 until tabStrip.childCount) {
                tabStrip.getChildAt(i).setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        return@setOnKeyListener focusCurrentPageFirstItem()
                    }
                    false
                }
            }
        }
    }

    private fun refreshCurrentPageFromTabReselect(): Boolean {
        val target = findCurrentViewPagerChildFragmentAs<RefreshKeyHandler>(binding.viewPager) ?: return false
        return target.handleRefreshKey()
    }

    private fun focusCurrentPageFirstItem(): Boolean {
        val byTag = findCurrentViewPagerChildFragment(binding.viewPager)
        val target = byTag as? MyTabSwitchFocusTarget
        if (target != null) return target.requestFocusFirstItemFromTabSwitch()

        val pageFragment =
            if (byTag?.view?.findViewById<RecyclerView?>(R.id.recycler) != null) {
                byTag
            } else {
                childFragmentManager.fragments.firstOrNull { it.isVisible && it.view?.findViewById<RecyclerView?>(R.id.recycler) != null }
            } ?: return false
        val recycler = pageFragment.view?.findViewById<RecyclerView?>(R.id.recycler) ?: return false

        recycler.postIfAlive(isAlive = { _binding != null }) {
            val vh = recycler.findViewHolderForAdapterPosition(0)
            when {
                vh != null -> {
                    vh.itemView.requestFocus()
                }

                recycler.adapter?.itemCount == 0 -> {
                    recycler.requestFocus()
                }

                else -> {
                    recycler.scrollToPosition(0)
                    recycler.postIfAlive(isAlive = { _binding != null }) {
                        recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                            ?: recycler.requestFocus()
                    }
                }
            }
        }
        return true
    }

    private fun focusCurrentPageFirstItemFromContentSwitch(): Boolean {
        val target = findCurrentViewPagerChildFragmentAs<MyTabSwitchFocusTarget>(binding.viewPager)
            ?: return false
        return target.requestFocusFirstItemFromTabSwitch()
    }

    private fun focusSelectedTab(): Boolean {
        val b = _binding ?: return false
        return b.tabLayout.requestFocusSelectedTab(fallbackPosition = b.viewPager.currentItem) { _binding != null }
    }

    override fun requestFocusCurrentPageFirstItemFromContentSwitch(): Boolean {
        pendingFocusFirstItemFromContentSwitch = true
        if (focusCurrentPageFirstItemFromContentSwitch()) {
            pendingFocusFirstItemFromContentSwitch = false
        }
        return true
    }

    override fun handleBackPressed(): Boolean {
        val b = _binding ?: return false
        val scheme = BiliClient.prefs.mainBackFocusScheme

        // Tab strip is a navigation layer: Back should always return to the left sidebar.
        if (b.tabLayout.hasFocus()) {
            return (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
        }

        // Only handle the Back key when focus is inside the page content area.
        val inContent = b.viewPager.hasFocus() && !b.tabLayout.hasFocus()
        if (!inContent) return false

        return when (scheme) {
            AppPrefs.MAIN_BACK_FOCUS_SCHEME_A -> focusSelectedTab()
            AppPrefs.MAIN_BACK_FOCUS_SCHEME_B -> {
                if (b.viewPager.currentItem != 0) {
                    pendingFocusFirstItemFromContentSwitch = true
                    // Use non-smooth switch: smooth scrolling may trigger intermediate onPageSelected callbacks
                    // and consume the pending focus restore on the wrong page.
                    b.viewPager.setCurrentItem(0, false)
                    true
                } else {
                    (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
                }
            }
            AppPrefs.MAIN_BACK_FOCUS_SCHEME_C -> {
                (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
            }
            else -> focusSelectedTab()
        }
    }

    private fun loadProfileHeader() {
        val b = _binding ?: return
        profileLoadJob?.cancel()
        profileLoadJob =
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val nav = BiliApi.nav()
                    val data = nav.optJSONObject("data")
                    val isLogin = data?.optBoolean("isLogin") ?: false
                    if (!isLogin) {
                        b.tvMyName.text = "未登录"
                        b.tvMyUid.text = ""
                        b.tvMyLevel.text = "Lv0"
                        b.tvMyExp.text = ""
                        b.tvMyStatDynamic.text = "--"
                        b.tvMyStatFollowing.text = "--"
                        b.tvMyStatFollower.text = "--"
                        b.tvMyStatCoins.text = "--"
                        return@launch
                    }

                    val mid = data?.optLong("mid") ?: 0L
                    val name = data?.optString("uname", "").orEmpty()
                    val avatarUrl = data?.optString("face")?.takeIf { it.isNotBlank() }
                    val levelInfo = data?.optJSONObject("level_info")
                    val level = levelInfo?.optInt("current_level") ?: 0
                    val currentExp = parseInt(levelInfo, "current_exp") ?: 0
                    val nextExp = parseInt(levelInfo, "next_exp")
                    val stat = if (mid > 0L) BiliApi.relationStat(mid) else null
                    val dynamicCount =
                        if (mid > 0L) {
                            runCatching { BiliApi.dynamicCount(mid) }.getOrDefault(0L)
                        } else {
                            0L
                        }

                    b.tvMyName.text = name.ifBlank { "未命名用户" }
                    b.tvMyUid.text = getString(R.string.label_uid_fmt, mid.toString())
                    b.tvMyLevel.text = getString(R.string.label_level_fmt, level)
                    val sign = data?.optString("sign", "").orEmpty().trim()
                    b.tvMyExp.text = sign.ifBlank { "这个人很神秘，什么都没有写" }
                    b.tvMyStatDynamic.text = compactCount(dynamicCount)
                    b.tvMyStatFollowing.text = compactCount(stat?.following ?: 0L)
                    b.tvMyStatFollower.text = compactCount(stat?.follower ?: 0L)
                    b.tvMyStatCoins.text = formatCoins(parseCoins(data))
                    ImageLoader.loadInto(b.ivMyAvatar, ImageUrl.avatar(avatarUrl))
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.w("My", "load profile header failed", t)
                    b.tvMyName.text = "加载失败"
                    b.tvMyExp.text = t.message.orEmpty()
                }
            }
    }

    private fun compactCount(value: Long): String {
        val v = value.coerceAtLeast(0L)
        return when {
            v >= 100_000_000L -> String.format(Locale.getDefault(), "%.1f亿", v / 100_000_000.0)
            v >= 10_000L -> String.format(Locale.getDefault(), "%.1f万", v / 10_000.0)
            else -> v.toString()
        }
    }

    private fun parseCoins(data: JSONObject?): Double {
        if (data == null) return 0.0
        val money = data.optDouble("money", Double.NaN)
        if (!money.isNaN()) return money
        return data.optDouble("coins", 0.0)
    }

    private fun formatCoins(value: Double): String {
        val v = value.coerceAtLeast(0.0)
        return if (v >= 1000) String.format(Locale.getDefault(), "%.0f", v)
        else String.format(Locale.getDefault(), "%.1f", v)
    }

    private fun parseInt(obj: JSONObject?, key: String): Int? {
        val any = obj?.opt(key) ?: return null
        return when (any) {
            is Number -> any.toInt()
            is String -> any.toIntOrNull()
            else -> null
        }
    }

    override fun onDestroyView() {
        profileLoadJob?.cancel()
        profileLoadJob = null
        pageCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null
        mediator?.detach()
        mediator = null
        tabs = emptyList()
        _binding = null
        super.onDestroyView()
    }
}
