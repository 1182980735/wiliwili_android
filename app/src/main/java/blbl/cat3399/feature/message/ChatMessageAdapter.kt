package blbl.cat3399.feature.message

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.ChatEmote
import blbl.cat3399.core.api.ChatMessageItem
import blbl.cat3399.core.api.ChatVideoCard
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemChatMessageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ChatMessageAdapter(
    private val onVideoClick: (ChatVideoCard) -> Unit,
) : RecyclerView.Adapter<ChatMessageAdapter.Vh>() {
    private val items = ArrayList<ChatMessageItem>()
    private val emotes = LinkedHashMap<String, ChatEmote>()
    private val emoteBitmaps = HashMap<String, Bitmap>()
    private val loadingEmotes = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<ChatMessageItem>, newEmotes: Map<String, ChatEmote> = emptyMap()) {
        items.clear()
        items.addAll(list)
        mergeEmotes(newEmotes)
        notifyDataSetChanged()
    }

    /** 返回实际插入数量，供调用方修正滚动位置。 */
    fun prepend(list: List<ChatMessageItem>, newEmotes: Map<String, ChatEmote> = emptyMap()): Int {
        mergeEmotes(newEmotes)
        val existing = HashSet<String>(items.size * 2)
        for (item in items) existing += stableKey(item)
        val toAdd = list.filter { existing.add(stableKey(it)) }
        if (toAdd.isNotEmpty()) {
            items.addAll(0, toAdd)
            notifyItemRangeInserted(0, toAdd.size)
        }
        if (newEmotes.isNotEmpty()) notifyItemRangeChanged(toAdd.size, items.size - toAdd.size)
        return toAdd.size
    }

    fun append(item: ChatMessageItem): Boolean {
        if (items.any { stableKey(it) == stableKey(item) }) return false
        items.add(item)
        notifyItemInserted(items.lastIndex)
        return true
    }

    fun close() {
        scope.cancel()
        emoteBitmaps.clear()
        loadingEmotes.clear()
    }

    override fun getItemId(position: Int): Long = stableKey(items[position]).hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], emotes, onVideoClick, ::getOrRequestEmoteBitmap)
    }

    override fun getItemCount(): Int = items.size

    private fun mergeEmotes(newEmotes: Map<String, ChatEmote>) {
        if (newEmotes.isEmpty()) return
        emotes.putAll(newEmotes)
    }

    private fun stableKey(item: ChatMessageItem): String =
        if (item.seqno > 0L) "seq:${item.seqno}" else "key:${item.key}"

    private fun getOrRequestEmoteBitmap(emote: ChatEmote): Bitmap? {
        val cached = emoteBitmaps[emote.text]
        if (cached == null) requestEmote(emote)
        return cached
    }

    private fun requestEmote(emote: ChatEmote) {
        if (emoteBitmaps.containsKey(emote.text) || loadingEmotes.contains(emote.text)) return
        loadingEmotes.add(emote.text)
        scope.launch {
            try {
                val url = normalizeEmoteUrl(emote.url)
                val bytes = withContext(Dispatchers.IO) { BiliClient.getBytes(url) }
                val bitmap =
                    withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                if (bitmap != null) {
                    emoteBitmaps[emote.text] = bitmap
                    notifyDataSetChanged()
                }
            } catch (t: Throwable) {
                AppLog.w("ChatMessageAdapter", "load emote failed text=${emote.text}", t)
            } finally {
                loadingEmotes.remove(emote.text)
            }
        }
    }

    private fun normalizeEmoteUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
    }

    class Vh(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: ChatMessageItem,
            emotes: Map<String, ChatEmote>,
            onVideoClick: (ChatVideoCard) -> Unit,
            getEmoteBitmap: (ChatEmote) -> Bitmap?,
        ) {
            val video = item.video
            val image = item.image
            val showText = video == null && image == null && item.text.isNotBlank()
            binding.tvChatMessage.visibility = if (showText) View.VISIBLE else View.GONE
            binding.cardChatImage.visibility = if (image == null) View.GONE else View.VISIBLE
            binding.cardChatVideo.visibility = if (video == null) View.GONE else View.VISIBLE

            if (showText) {
                binding.tvChatMessage.text = buildEmoteText(item.text, emotes, getEmoteBitmap)
                binding.tvChatMessage.setBackgroundResource(
                    if (item.outgoing) R.drawable.bg_chat_bubble_out else R.drawable.bg_chat_bubble_in,
                )
                val params = binding.tvChatMessage.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (item.outgoing) Gravity.END else Gravity.START
                binding.tvChatMessage.layoutParams = params
            }

            if (image != null) {
                ImageLoader.loadInto(binding.ivChatImage, ImageUrl.commentThumbnail(image.url))
                val params = binding.cardChatImage.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (item.outgoing) Gravity.END else Gravity.START
                binding.cardChatImage.layoutParams = params
            }

            if (video != null) {
                binding.tvChatVideoTitle.text = video.title
                val stats =
                    buildList {
                        video.viewCount?.let { add("${Format.count(it)}播放") }
                        video.danmakuCount?.let { add("${Format.count(it)}弹幕") }
                    }.joinToString(" · ")
                binding.tvChatVideoMeta.text =
                    listOfNotNull(video.description?.takeIf { it.isNotBlank() }, stats.takeIf { it.isNotBlank() })
                        .joinToString(" · ")
                binding.tvChatVideoDuration.visibility = if (video.durationSec > 0) View.VISIBLE else View.GONE
                if (video.durationSec > 0) {
                    binding.tvChatVideoDuration.text = Format.duration(video.durationSec)
                }
                val hasCover = video.coverUrl?.isNotBlank() == true
                binding.ivChatVideoCover.visibility = if (hasCover) View.VISIBLE else View.GONE
                binding.ivChatVideoPlaceholder.visibility = if (hasCover) View.GONE else View.VISIBLE
                if (hasCover) {
                    ImageLoader.loadInto(binding.ivChatVideoCover, ImageUrl.cover(video.coverUrl))
                }
                val params = binding.cardChatVideo.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (item.outgoing) Gravity.END else Gravity.START
                binding.cardChatVideo.layoutParams = params
                binding.cardChatVideo.setOnClickListener { onVideoClick(video) }
            }
        }

        private fun buildEmoteText(
            text: String,
            emotes: Map<String, ChatEmote>,
            getEmoteBitmap: (ChatEmote) -> Bitmap?,
        ): CharSequence {
            if (emotes.isEmpty()) return text
            val ordered = emotes.values.sortedByDescending { it.text.length }
            val builder = SpannableStringBuilder()
            var index = 0
            while (index < text.length) {
                val emote = ordered.firstOrNull { text.startsWith(it.text, index) }
                if (emote == null) {
                    builder.append(text[index])
                    index++
                    continue
                }
                val bitmap = getEmoteBitmap(emote)
                if (bitmap == null) {
                    builder.append(emote.text)
                } else {
                    val start = builder.length
                    builder.append('\uFFFC')
                    val sizeDp = if (emote.size >= 2) 28 else 20
                    val sizePx = (sizeDp * binding.root.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    val drawable = BitmapDrawable(binding.root.resources, bitmap)
                    drawable.setBounds(0, 0, sizePx, sizePx)
                    builder.setSpan(
                        ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        start,
                        builder.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                index += emote.text.length
            }
            return builder
        }

    }
}
