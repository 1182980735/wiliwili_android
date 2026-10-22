package blbl.cat3399.feature.message

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import blbl.cat3399.R
import blbl.cat3399.core.api.ChatVideoCard
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
    private var chatLoadJob: Job? = null
    private var chatSendJob: Job? = null
    private var selectedTab: Int = 0
    private var chatOpen: Boolean = false
    private var chatTalkerId: Long? = null
    private var chatHistoryLoading: Boolean = false
    private var chatSending: Boolean = false
    private lateinit var chatAdapter: ChatMessageAdapter
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        Immersive.apply(this, BiliClient.prefs.fullscreenEnabled)

        adapter = MessageCenterAdapter(onClick = ::openMessageItem)
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerMessages.adapter = adapter

        chatAdapter = ChatMessageAdapter { video -> openChatVideo(video) }
        binding.chatPanel.recyclerChatMessages.layoutManager = LinearLayoutManager(this)
        binding.chatPanel.recyclerChatMessages.adapter = chatAdapter
        binding.chatPanel.btnChatBack.setOnClickListener { closeChat() }
        binding.chatPanel.btnChatSend.setOnClickListener { sendChatText() }
        binding.chatPanel.etChatInput.setOnEditorActionListener { _, actionId, event ->
            val isSendAction =
                actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isSendAction) {
                sendChatText()
                true
            } else {
                false
            }
        }
        binding.messageScrim.setOnClickListener { finish() }
        binding.btnMessageClose.setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (chatOpen) closeChat() else finish()
                }
            },
        )

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
        chatLoadJob?.cancel()
        chatLoadJob = null
        chatSendJob?.cancel()
        chatSendJob = null
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
        val talkerId = item.talkerId
        if (talkerId != null && talkerId > 0L) {
            openChat(item, talkerId)
            return
        }
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

    private fun openChat(item: MessageCenterItem, talkerId: Long) {
        chatSendJob?.cancel()
        chatSendJob = null
        chatSending = false
        chatHistoryLoading = true
        chatOpen = true
        chatTalkerId = talkerId
        binding.messagePanel.visibility = View.GONE
        binding.chatPanel.root.visibility = View.VISIBLE
        binding.chatPanel.tvChatTitle.text = item.title
        binding.chatPanel.etChatInput.setText("")
        chatAdapter.submit(emptyList())
        updateChatComposerState()
        updateChatHint(show = true, text = "加载中…")
        chatLoadJob?.cancel()
        chatLoadJob =
            lifecycleScope.launch {
                try {
                    val messages = MessageCenterApi.loadChatMessages(talkerId)
                    if (chatOpen.not() || chatTalkerId != talkerId) return@launch
                    chatAdapter.submit(messages)
                    updateChatHint(show = messages.isEmpty(), text = "暂无聊天记录")
                    binding.chatPanel.recyclerChatMessages.post {
                        if (messages.isNotEmpty()) {
                            binding.chatPanel.recyclerChatMessages.scrollToPosition(messages.lastIndex)
                        }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.e("MessageCenter", "load chat failed talker=$talkerId", t)
                    updateChatHint(show = true, text = t.message ?: "聊天加载失败")
                } finally {
                    if (chatOpen && chatTalkerId == talkerId) {
                        chatHistoryLoading = false
                        updateChatComposerState()
                    }
                }
            }
    }

    private fun sendChatText() {
        val talkerId = chatTalkerId ?: return
        if (chatOpen.not() || chatHistoryLoading || chatSending) return
        val input = binding.chatPanel.etChatInput
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            input.requestFocus()
            AppToast.show(this, "请输入消息内容")
            return
        }

        chatSending = true
        updateChatComposerState()
        chatSendJob =
            lifecycleScope.launch {
                try {
                    val sentMessage = MessageCenterApi.sendChatMessage(talkerId, text)
                    if (chatOpen.not() || chatTalkerId != talkerId) return@launch
                    input.setText("")
                    chatAdapter.append(sentMessage)
                    updateChatHint(show = false, text = "")
                    binding.chatPanel.recyclerChatMessages.post {
                        val lastIndex = chatAdapter.itemCount - 1
                        if (lastIndex >= 0) {
                            binding.chatPanel.recyclerChatMessages.scrollToPosition(lastIndex)
                        }
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    AppLog.e("MessageCenter", "send chat failed talker=$talkerId", t)
                    AppToast.showLong(this@MessageCenterActivity, "发送失败：${t.message ?: "未知错误"}")
                } finally {
                    chatSending = false
                    updateChatComposerState()
                }
            }
    }

    private fun updateChatComposerState() {
        val historyReady = chatOpen && chatHistoryLoading.not()
        binding.chatPanel.etChatInput.isEnabled = historyReady
        binding.chatPanel.btnChatSend.isEnabled = historyReady && chatSending.not()
        binding.chatPanel.btnChatSend.text = if (chatSending) "发送中…" else "发送"
    }

    private fun openChatVideo(video: ChatVideoCard) {
        val intent = Intent(this, VideoDetailActivity::class.java)
        if (video.bvid.isNotBlank()) {
            intent.putExtra(VideoDetailActivity.EXTRA_BVID, video.bvid)
        } else {
            val aid = video.aid ?: return
            intent.putExtra(VideoDetailActivity.EXTRA_AID, aid)
        }
        startActivity(intent)
    }

    private fun closeChat() {
        chatLoadJob?.cancel()
        chatLoadJob = null
        chatSendJob?.cancel()
        chatSendJob = null
        chatOpen = false
        chatTalkerId = null
        chatHistoryLoading = false
        chatSending = false
        binding.chatPanel.etChatInput.setText("")
        updateChatComposerState()
        binding.chatPanel.root.visibility = View.GONE
        binding.messagePanel.visibility = View.VISIBLE
    }

    private fun updateChatHint(show: Boolean, text: String) {
        binding.chatPanel.tvChatHint.text = text
        binding.chatPanel.tvChatHint.visibility = if (show) View.VISIBLE else View.GONE
    }

    private companion object {
        val VIDE_BVID_REGEX = Regex("BV[0-9A-Za-z]{10}")
    }
}
