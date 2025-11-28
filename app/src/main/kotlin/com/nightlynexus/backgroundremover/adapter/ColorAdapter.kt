package com.nightlynexus.backgroundremover.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nightlynexus.backgroundremover.databinding.ItemColorBinding




class ColorAdapter(
        private val colors: List<Int>,
        private val onColorSelected: (Int) -> Unit
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    private var selectedPosition = 0

    inner class ColorViewHolder(val binding: ItemColorBinding) :
            RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemColorBinding.inflate(inflater, parent, false)
        return ColorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        val color = colors[position]

        val bgShape = holder.binding.colorCircle.background as GradientDrawable
        bgShape.setColor(color)

        // Highlight selected color
        val scale = if (selectedPosition == position) 1.3f else 1f
        holder.binding.colorCircle.scaleX = scale
        holder.binding.colorCircle.scaleY = scale

        holder.binding.colorCircle.setOnClickListener {
            selectedPosition = position
            onColorSelected(color)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = colors.size
}
