package com.nightlynexus.backgroundremover.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter

class ColorsAdapter(
        private val context: Context,
        private val list: List<Int>
) : BaseAdapter() {

    override fun getCount() = list.size
    override fun getItem(p0: Int) = list[p0]
    override fun getItemId(p0: Int) = p0.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = View(context)
        v.layoutParams = AbsListView.LayoutParams(120, 120)
        v.setBackgroundColor(list[position])
        return v
    }
}
