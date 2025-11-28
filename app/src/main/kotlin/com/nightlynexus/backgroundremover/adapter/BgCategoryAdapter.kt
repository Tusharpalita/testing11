package com.nightlynexus.backgroundremover.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nightlynexus.backgroundremover.models.BgCategory
import com.nightlynexus.backgroundremover.models.BgItem
import com.nightlynexus.backgroundremover.databinding.ItemCategoryBinding
class BgCategoryAdapter(
        private val categories: List<BgCategory>,
        private val onItemClick: (BgItem, Int, Int) -> Unit
) : RecyclerView.Adapter<BgCategoryAdapter.VH>() {

    inner class VH(val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        var childAdapter: BgItemAdapter? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCategoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount() = categories.size

    override fun onBindViewHolder(holder: VH, categoryPos: Int) {

        val category = categories[categoryPos]

        // MAKE NEW CHILD ADAPTER
        val adapter = BgItemAdapter(category.items) { item, itemIndex ->
            onItemClick(item, categoryPos, itemIndex)
        }

        holder.childAdapter = adapter

        holder.b.categoryTitle.text = category.title

        holder.b.bgItemsRecycler.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
        }
    }

    // REFRESH EXACT ITEM
    fun refreshChild(categoryPos: Int, itemPos: Int) {
        val vh = findViewHolder(categoryPos) ?: return
        vh.childAdapter?.refreshItem(itemPos)
    }

    private fun findViewHolder(position: Int): VH? {
        return recyclerView?.findViewHolderForAdapterPosition(position) as? VH
    }

    private var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
        recyclerView = rv
    }
}
