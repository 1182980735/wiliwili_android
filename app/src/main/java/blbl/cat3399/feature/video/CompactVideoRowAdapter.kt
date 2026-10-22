package blbl.cat3399.feature.video

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
        holder.bind(items[position], position, onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemVideoCompactRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: VideoCard, position: Int, onClick: (VideoCard, Int) -> Unit) {
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
        }
    }
}
