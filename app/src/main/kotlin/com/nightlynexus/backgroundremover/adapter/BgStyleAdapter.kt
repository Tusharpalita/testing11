package com.nightlynexus.backgroundremover.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.nightlynexus.backgroundremover.R
import com.nightlynexus.backgroundremover.models.BgStyle

class BgStyleAdapter(
        private val list: List<BgStyle>,
        private val click: (BgStyle) -> Unit
) : RecyclerView.Adapter<BgStyleAdapter.Holder>() {

    inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val img = v.findViewById<ImageView>(R.id.bgPreviewImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bg_style, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = list[pos]

        if (item.type == "color") {
            h.img.setBackgroundColor(item.data)
        } else {
            h.img.setImageResource(item.data)
        }

        h.itemView.setOnClickListener { click(item) }
    }
}
