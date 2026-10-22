package blbl.cat3399.feature.dynamic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.api.BiliApi.DynamicArticleCard
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemDynamicArticleBinding

internal class DynamicArticleAdapter(
    private val onClick: (DynamicArticleCard) -> Unit,
) : RecyclerView.Adapter<DynamicArticleAdapter.Vh>() {
    private val items = ArrayList<DynamicArticleCard>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<DynamicArticleCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<DynamicArticleCard>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun snapshot(): List<DynamicArticleCard> = items.toList()

    override fun getItemId(position: Int): Long = items[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding = ItemDynamicArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemDynamicArticleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DynamicArticleCard, onClick: (DynamicArticleCard) -> Unit) {
            binding.tvArticleTitle.text = item.title
            binding.tvArticleSummary.visibility = if (item.summary.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.tvArticleSummary.text = item.summary.orEmpty()
            val date = item.publishTime?.let { Format.pubDateText(it) }.orEmpty()
            val stats =
                buildList {
                    item.likeCount?.let { add("${Format.count(it)}赞") }
                    item.commentCount?.let { add("${Format.count(it)}评论") }
                }.joinToString(" · ")
            binding.tvArticleMeta.text =
                listOfNotNull(item.authorName.takeIf { it.isNotBlank() }, date.takeIf { it.isNotBlank() }, stats.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            if (item.coverUrl.isNullOrBlank()) {
                binding.ivArticleCover.setImageDrawable(null)
            } else {
                ImageLoader.loadInto(binding.ivArticleCover, ImageUrl.cover(item.coverUrl))
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
