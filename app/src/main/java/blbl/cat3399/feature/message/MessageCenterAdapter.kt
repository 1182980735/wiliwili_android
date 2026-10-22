package blbl.cat3399.feature.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.api.MessageCenterItem
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemMessageCenterBinding

internal class MessageCenterAdapter(
    private val onClick: (MessageCenterItem) -> Unit,
) : RecyclerView.Adapter<MessageCenterAdapter.Vh>() {
    private val items = ArrayList<MessageCenterItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<MessageCenterItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun markChatRead(talkerId: Long) {
        val index = items.indexOfFirst { it.talkerId == talkerId }
        if (index < 0 || items[index].unread.not()) return
        items[index] = items[index].copy(unread = false)
        notifyItemChanged(index)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemMessageCenterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemMessageCenterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MessageCenterItem, onClick: (MessageCenterItem) -> Unit) {
            binding.tvMessageTitle.text = item.title
            binding.tvMessageSubtitle.text = item.subtitle
            binding.tvMessageTime.text = if (item.timeSec > 0L) Format.pubDateText(item.timeSec) else ""
            binding.vMessageUnread.isVisible = item.unread
            ImageLoader.loadInto(binding.ivMessageAvatar, ImageUrl.avatar(item.avatarUrl))
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
