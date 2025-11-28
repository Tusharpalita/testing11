package com.nightlynexus.backgroundremover.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nightlynexus.backgroundremover.models.BgItem
import com.nightlynexus.backgroundremover.databinding.ItemBackgroundBinding
import java.io.File
class BgItemAdapter(
        private val items: List<BgItem>,
        private val onClick: (BgItem, Int) -> Unit
) : RecyclerView.Adapter<BgItemAdapter.VH>() {

    inner class VH(val b: ItemBackgroundBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemBackgroundBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        val cache = File(ctx.cacheDir, "bg_cache/${item.id}.jpg")

        // Load image (cache if downloaded)
        Glide.with(ctx)
                .load(if (cache.exists()) cache else item.thumbUrl)
                .into(holder.b.bgThumb)

        // UI state handling
        when {
            cache.exists() -> {
                holder.b.progressSmall.visibility = View.GONE
                holder.b.downloadIcon.visibility = View.GONE
            }

            item.isDownloading -> {
                holder.b.progressSmall.visibility = View.VISIBLE
                holder.b.downloadIcon.visibility = View.GONE
            }

            else -> {
                holder.b.progressSmall.visibility = View.GONE
                holder.b.downloadIcon.visibility = View.VISIBLE
            }
        }

        // CLICK HANDLER
        holder.itemView.setOnClickListener {
            onClick(item, holder.bindingAdapterPosition)
        }
    }

    fun refreshItem(index: Int) {
        notifyItemChanged(index)
    }
}
