package blbl.cat3399.feature.message

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.ChatMessageItem
import blbl.cat3399.core.api.ChatVideoCard
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemChatMessageBinding

internal class ChatMessageAdapter(
    private val onVideoClick: (ChatVideoCard) -> Unit,
) : RecyclerView.Adapter<ChatMessageAdapter.Vh>() {
    private val items = ArrayList<ChatMessageItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<ChatMessageItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(item: ChatMessageItem) {
        items.add(item)
        notifyItemInserted(items.lastIndex)
    }

    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], onVideoClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItem, onVideoClick: (ChatVideoCard) -> Unit) {
            val video = item.video
            binding.tvChatMessage.visibility = if (video == null) View.VISIBLE else View.GONE
            binding.cardChatVideo.visibility = if (video == null) View.GONE else View.VISIBLE
            if (video == null) {
                binding.tvChatMessage.text = item.text
                binding.tvChatMessage.setBackgroundResource(
                    if (item.outgoing) R.drawable.bg_chat_bubble_out else R.drawable.bg_chat_bubble_in,
                )
                val params = binding.tvChatMessage.layoutParams as FrameLayout.LayoutParams
                params.gravity = if (item.outgoing) Gravity.END else Gravity.START
                binding.tvChatMessage.layoutParams = params
                return
            }

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
}
