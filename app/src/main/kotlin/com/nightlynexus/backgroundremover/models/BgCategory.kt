package com.nightlynexus.backgroundremover.models

data class BgCategory(
    val title: String,
    val items: List<BgItem>
)

data class BgItem(
        val id: String,
        val thumbUrl: String,
        val fullUrl: String,
        var isDownloading: Boolean = false
)
