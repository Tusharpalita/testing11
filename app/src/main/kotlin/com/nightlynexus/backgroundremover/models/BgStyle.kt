package com.nightlynexus.backgroundremover.models

data class BgStyle(
    val type: String,   // "color" or "frame"
    val data: Int       // color int OR drawable resource
)
