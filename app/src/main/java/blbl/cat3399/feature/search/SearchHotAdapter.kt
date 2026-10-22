package blbl.cat3399.feature.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.model.SearchHotItem
import blbl.cat3399.core.ui.ThemeColor
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemSearchHotBinding

class SearchHotAdapter(
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<SearchHotAdapter.Vh>() {
    private val items = ArrayList<SearchHotItem>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<SearchHotItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].keyword.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemSearchHotBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(position, items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemSearchHotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int, item: SearchHotItem, onClick: (String) -> Unit) {
            val context = binding.root.context
            binding.tvRank.text = (position + 1).toString()
            binding.tvRank.setTextColor(
                ThemeColor.resolve(
                    context,
                    if (position < 3) R.attr.blblAccent else com.google.android.material.R.attr.colorOnSurface,
                    if (position < 3) R.color.blbl_accent_wiliwili else R.color.blbl_text_secondary_wiliwili_light,
                ),
            )
            binding.tvKeyword.text = item.showName.ifBlank { item.keyword }
            val badge = item.icon?.trim().orEmpty()
            val badgeIsUrl = badge.startsWith("http://") || badge.startsWith("https://") || badge.startsWith("//")
            binding.ivHotBadge.visibility = if (badgeIsUrl) View.VISIBLE else View.GONE
            binding.tvHotBadge.visibility = if (badge.isNotBlank() && badgeIsUrl.not()) View.VISIBLE else View.GONE
            if (badgeIsUrl) {
                ImageLoader.loadInto(
                    binding.ivHotBadge,
                    if (badge.startsWith("//")) "https:$badge" else badge,
                )
            } else {
                binding.tvHotBadge.text = badge
            }
            binding.root.radius =
                binding.root.resources.getDimension(R.dimen.wiliwili_page_radius_panel)
            binding.root.setOnClickListener { onClick(item.keyword) }
        }
    }
}
