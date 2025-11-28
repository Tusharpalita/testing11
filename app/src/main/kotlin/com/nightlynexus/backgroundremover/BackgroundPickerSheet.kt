package com.nightlynexus.backgroundremover


import android.graphics.Color
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.LinearLayoutManager

import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nightlynexus.backgroundremover.adapter.BgCategoryAdapter
import com.nightlynexus.backgroundremover.databinding.ActivityBackgroundPickerBinding
import com.nightlynexus.backgroundremover.models.BgCategory
import com.nightlynexus.backgroundremover.models.BgItem
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
class BackgroundPickerSheet(
        private val onBackgroundSelected: (type: String, data: String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var binding: ActivityBackgroundPickerBinding
    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var categoryAdapter: BgCategoryAdapter

    // ---------------------------------------------------------
    // BACKGROUND LIST
    // ---------------------------------------------------------
    private val categories = listOf(
            BgCategory("Landscape", listOf(
                    BgItem("land101", "https://picsum.photos/seed/land101/300/300", "https://picsum.photos/seed/land101/1500/1500"),
                    BgItem("land102", "https://picsum.photos/seed/land102/300/300", "https://picsum.photos/seed/land102/1500/1500"),
                    BgItem("land103", "https://picsum.photos/seed/land103/300/300", "https://picsum.photos/seed/land103/1500/1500"),
                    BgItem("land104", "https://picsum.photos/seed/land104/300/300", "https://picsum.photos/seed/land104/1500/1500"),
                    BgItem("land105", "https://picsum.photos/seed/land105/300/300", "https://picsum.photos/seed/land105/1500/1500")
            )),

            BgCategory("Nature", listOf(
                    BgItem("nature201", "https://picsum.photos/seed/nature201/300/300", "https://picsum.photos/seed/nature201/1500/1500"),
                    BgItem("nature202", "https://picsum.photos/seed/nature202/300/300", "https://picsum.photos/seed/nature202/1500/1500"),
                    BgItem("nature203", "https://picsum.photos/seed/nature203/300/300", "https://picsum.photos/seed/nature203/1500/1500"),
                    BgItem("nature204", "https://picsum.photos/seed/nature204/300/300", "https://picsum.photos/seed/nature204/1500/1500")
            )),

            BgCategory("Texture", listOf(
                    BgItem("tex301", "https://picsum.photos/seed/tex301/300/300", "https://picsum.photos/seed/tex301/1500/1500"),
                    BgItem("tex302", "https://picsum.photos/seed/tex302/300/300", "https://picsum.photos/seed/tex302/1500/1500"),
                    BgItem("tex303", "https://picsum.photos/seed/tex303/300/300", "https://picsum.photos/seed/tex303/1500/1500"),
                    BgItem("tex304", "https://picsum.photos/seed/tex304/300/300", "https://picsum.photos/seed/tex304/1500/1500")
            ))
    )

    // ---------------------------------------------------------
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = ActivityBackgroundPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    // ---------------------------------------------------------
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupTopBar()
        setupTools()

        // SET CATEGORY ADAPTER
        categoryAdapter = BgCategoryAdapter(categories) { item, catPos, itemPos ->
            onBackgroundItemClicked(item, catPos, itemPos)
        }

        binding.categoryRecycler.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        binding.categoryRecycler.adapter = categoryAdapter
    }

    // ---------------------------------------------------------
    private fun setupTopBar() {
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    // ---------------------------------------------------------
    private fun setupTools() {
        val icons = listOf(
                R.drawable.circle_bg,
                R.drawable.circle_bg,
                R.drawable.circle_bg,
                R.drawable.circle_bg,
                R.drawable.circle_bg
        )

        icons.forEach {
            val view = layoutInflater.inflate(R.layout.item_tool, null) as AppCompatImageView
            view.setImageResource(it)
            binding.toolsRow.addView(view)
        }

        binding.toolsRow.getChildAt(4).setOnClickListener {
            onBackgroundSelected("color", Color.TRANSPARENT.toString())
        }
    }

    // ---------------------------------------------------------
    // USER CLICKED ON BACKGROUND ITEM
    // ---------------------------------------------------------
    private fun onBackgroundItemClicked(item: BgItem, catIndex: Int, itemIndex: Int) {

        val cacheFile = getCachedFile(item.id)

        // Already downloaded → Apply Directly
        if (cacheFile.exists()) {
            onBackgroundSelected("file", cacheFile.absolutePath)
            return
        }

        // Mark downloading
        item.isDownloading = true
        categoryAdapter.refreshChild(catIndex, itemIndex)

        // Start download
        scope.launch {
            try {
                val bytes = URL(item.fullUrl).readBytes()
                cacheFile.writeBytes(bytes)

                withContext(Dispatchers.Main) {
                    item.isDownloading = false
                    categoryAdapter.refreshChild(catIndex, itemIndex)

                    onBackgroundSelected("file", cacheFile.absolutePath)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    item.isDownloading = false
                    categoryAdapter.refreshChild(catIndex, itemIndex)
                }
            }
        }
    }

    // ---------------------------------------------------------
    private fun getCachedFile(id: String): File {
        val dir = File(requireContext().cacheDir, "bg_cache")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$id.jpg")
    }
}
