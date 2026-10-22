package blbl.cat3399.feature.message

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import blbl.cat3399.R
import blbl.cat3399.core.api.MessageCenterApi
import blbl.cat3399.core.api.MessageCenterItem
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BaseActivity
import blbl.cat3399.core.ui.Immersive
import blbl.cat3399.databinding.ActivityMessageCenterBinding
import blbl.cat3399.feature.video.VideoDetailActivity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MessageCenterActivity : BaseActivity() {
    private lateinit var binding: ActivityMessageCenterBinding
    private lateinit var adapter: MessageCenterAdapter
    private var loadJob: Job? = null
    private var selectedTab: Int = 0
    private val cache: Array<List<MessageCenterItem>?> = arrayOfNulls(4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageCenterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.FILL)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)

        adapter = MessageCenterAdapter(onClick = ::openMessageItem)
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter
        binding.messageScrim.setOnClickListener { finish() }
        binding.btnMessageClose.setOnClickListener { finish() }

        val tabs = binding.tabsMessageCenter
        listOf("聊天列表", "回复我的", "@我的", "收到的赞").forEach { title ->
            tabs.addTab(tabs.newTab().setText(title))
        }
        tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = loadTab(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = loadTab(tab.position, force = true)
            },
        )
        tabs.getTabAt(0)?.select()
    }

    override fun onDestroy() {
        loadJob?.cancel()
        loadJob = null
        super.onDestroy()
    }

    private fun loadTab(index: Int, force: Boolean = false) {
        selectedTab = index
        val cached = cache[index]
        if (cached != null && force.not()) {
            adapter.submit(cached)
            updateHint(cached.isEmpty(), "暂无消息")
            return
        }
        loadJob?.cancel()
        adapter.submit(emptyList())
        updateHint(show = true, text = "加载中…")
        loadJob =
            lifecycleScope.launch {
                try {
                    val items =
                        when (index) {
                            0 -> MessageCenterApi.loadChatSessions()
                            1 -> MessageCenterApi.loadReplies()
                            2 -> MessageCenterApi.loadMentions()
                            else -> MessageCenterApi.loadLikes()
                        }
                    if (selectedTab != index) return@launch
                    cache[index] = items
                    adapter.submit(items)
                    updateHint(items.isEmpty(), "暂无消息")
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.e("MessageCenter", "load tab=$index failed", t)
                    if (selectedTab == index) {
                        updateHint(show = true, text = t.message ?: "加载失败，可稍后重试")
                    }
                }
            }
    }

    private fun updateHint(show: Boolean, text: String) {
        binding.tvMessageHint.text = text
        binding.tvMessageHint.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun openMessageItem(item: MessageCenterItem) {
        val uri = item.uri?.trim().orEmpty()
        if (uri.isBlank()) {
            AppToast.show(this, "聊天详情暂未接入")
            return
        }
        val bvid = VIDE_BVID_REGEX.find(uri)?.value
        if (bvid != null) {
            startActivity(
                Intent(this, VideoDetailActivity::class.java)
                    .putExtra(VideoDetailActivity.EXTRA_BVID, bvid),
            )
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }

    private companion object {
        val VIDE_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}")
    }
}
