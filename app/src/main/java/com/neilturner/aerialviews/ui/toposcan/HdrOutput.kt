package com.neilturner.aerialviews.ui.toposcan

enum class HdrOutput(
    val label: String,
    val eglColourSpace: Int,
) {
    HDR10("HDR10 / BT.2020 PQ", 0x3340),
    HLG("HLG / BT.2020 (experimental)", 0x3540),
}
