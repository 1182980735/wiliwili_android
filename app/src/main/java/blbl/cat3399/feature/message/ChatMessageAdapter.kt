package blbl.cat3399.feature.message

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.api.ChatMessageItem
import blbl.cat3399.databinding.ItemChatMessageBinding

internal class ChatMessageAdapter : RecyclerView.Adapter<ChatMessageAdapter.Vh>() {
    private val items = ArrayList<ChatMessageItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<ChatMessageItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].key.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItem) {
            binding.tvChatMessage.text = item.text
            binding.tvChatMessage.setBackgroundResource(
                if (item.outgoing) R.drawable.bg_chat_bubble_out else R.drawable.bg_chat_bubble_in,
            )
            val params = binding.tvChatMessage.layoutParams as FrameLayout.LayoutParams
            params.gravity = if (item.outgoing) Gravity.END else Gravity.START
            binding.tvChatMessage.layoutParams = params
        }
    }
}
