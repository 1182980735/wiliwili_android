package blbl.cat3399.feature.dynamic

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.ui.ThemeColor
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemFollowingBinding
import com.google.android.material.R as MaterialR

class FollowingAdapter(
    private val onClick: (FollowingUi) -> Unit,
) : RecyclerView.Adapter<FollowingAdapter.Vh>() {
    data class FollowingUi(
        val mid: Long,
        val name: String,
        val avatarUrl: String?,
        val showRecentUpdateDot: Boolean = false,
        val isAll: Boolean = false,
    )

    private val items = ArrayList<FollowingUi>()
    private var selectedMid: Long = MID_ALL

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<FollowingUi>, selected: Long = MID_ALL) {
        val prevSelected = selectedMid
        val sameItems =
            items.size == list.size &&
                items.indices.all { idx -> items[idx] == list[idx] }

        selectedMid = selected
        if (sameItems) {
            val prevPos = items.indexOfFirst { it.mid == prevSelected }
            val newPos = items.indexOfFirst { it.mid == selectedMid }
            if (prevPos >= 0) notifyItemChanged(prevPos)
            if (newPos >= 0 && newPos != prevPos) notifyItemChanged(newPos)
            return
        }

        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].mid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemFollowingBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) =
        holder.bind(items[position], items[position].mid == selectedMid, onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemFollowingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FollowingUi, selected: Boolean, onClick: (FollowingUi) -> Unit) {
            binding.tvName.text = item.name
            val ctx = binding.root.context
            val primaryText = ThemeColor.resolve(ctx, MaterialR.attr.colorOnSurface, blbl.cat3399.R.color.blbl_text)
            val secondaryText = ThemeColor.resolve(ctx, android.R.attr.textColorSecondary, blbl.cat3399.R.color.blbl_text_secondary)
            val accent = ThemeColor.resolve(ctx, blbl.cat3399.R.attr.blblAccent, blbl.cat3399.R.color.blbl_accent_wiliwili)
            val pad = (13 * ctx.resources.displayMetrics.density).toInt()
            if (item.isAll) {
                binding.ivAvatar.setImageResource(blbl.cat3399.R.drawable.wiliwili_ico_dynamic)
                binding.ivAvatar.setBackgroundResource(blbl.cat3399.R.drawable.bg_wiliwili_all_dynamic_circle)
                binding.ivAvatar.setPadding(pad, pad, pad, pad)
                binding.ivAvatar.imageTintList =
                    android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            } else {
                binding.ivAvatar.setBackgroundResource(0)
                binding.ivAvatar.setPadding(0, 0, 0, 0)
                binding.ivAvatar.imageTintList = null
                ImageLoader.loadInto(binding.ivAvatar, ImageUrl.avatar(item.avatarUrl))
            }
            binding.vAvatarUpdateDot.isVisible = !item.isAll && item.showRecentUpdateDot
            binding.vSelected.visibility = android.view.View.GONE
            binding.root.isSelected = selected
            binding.tvName.setTextColor(if (item.isAll) primaryText else if (selected) accent else secondaryText)
            binding.root.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        const val MID_ALL: Long = -1L
    }
}
