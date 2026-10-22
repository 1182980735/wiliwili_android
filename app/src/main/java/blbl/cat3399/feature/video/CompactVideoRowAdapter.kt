package blbl.cat3399.feature.video

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemVideoCompactRowBinding

internal class CompactVideoRowAdapter(
    private val onClick: (VideoCard, Int) -> Unit,
    private val actionDelegate: VideoCardActionDelegate? = null,
    private val onLongClick: ((VideoCard, Int) -> Boolean)? = null,
) : RecyclerView.Adapter<CompactVideoRowAdapter.Vh>() {
    private val items = ArrayList<VideoCard>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<VideoCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun snapshot(): List<VideoCard> = items.toList()

    fun removeByStableKey(stableKey: String): Int {
        val index = items.indexOfFirst { it.stableKey() == stableKey }
        if (index < 0) return -1
        items.removeAt(index)
        notifyItemRemoved(index)
        if (index < items.size) notifyItemRangeChanged(index, items.size - index)
        return index
    }

    fun invalidateSizing() {
        if (items.isEmpty()) return
        notifyItemRangeChanged(0, items.size)
    }

    override fun getItemId(position: Int): Long = items[position].stableKey().hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemVideoCompactRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], position, onClick, actionDelegate, onLongClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemVideoCompactRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: VideoCard,
            position: Int,
            onClick: (VideoCard, Int) -> Unit,
            actionDelegate: VideoCardActionDelegate?,
            onLongClick: ((VideoCard, Int) -> Boolean)?,
        ) {
            binding.tvCompactTitle.text = item.title
            val dateText = item.pubDateText?.takeIf { it.isNotBlank() }
                ?: item.pubDate?.let { Format.pubDateText(it) }.orEmpty()
            binding.tvCompactMeta.text =
                buildString {
                    if (item.ownerName.isNotBlank()) append("UP ").append(item.ownerName)
                    if (dateText.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(dateText)
                    }
                }
            val viewCount = item.view?.takeIf { it > 0L }
            binding.tvCompactView.isVisible = viewCount != null
            viewCount?.let { binding.tvCompactView.text = Format.count(it) }
            binding.tvCompactDuration.isVisible = item.durationSec > 0
            if (item.durationSec > 0) binding.tvCompactDuration.text = Format.duration(item.durationSec)
            ImageLoader.loadInto(binding.ivCompactCover, ImageUrl.cover(item.coverUrl))
            binding.root.contentDescription = item.title
            binding.root.setOnClickListener { onClick(item, position) }
            binding.root.setOnLongClickListener {
                val menuShown = actionDelegate?.showMenu(item, position) == true
                if (menuShown) true else onLongClick?.invoke(item, position) ?: false
            }
            binding.root.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_Y)
                ) {
                    val menuShown = actionDelegate?.showMenu(item, position) == true
                    if (menuShown) true else onLongClick?.invoke(item, position) ?: false
                } else {
                    false
                }
            }
        }
    }
}
