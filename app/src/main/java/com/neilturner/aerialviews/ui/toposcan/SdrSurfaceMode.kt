package com.neilturner.aerialviews.ui.toposcan

enum class SdrSurfaceMode(
    val preference: String,
    val label: String,
    val alphaBits: Int,
    val mediaOverlay: Boolean,
) {
    RGBA("rgba", "RGBA8888 / standard layer", 8, false),
    LEGACY("legacy", "RGB / media overlay (legacy)", 0, true),
    TILED("tiled", "Tiled 4K / experimental", 8, false),
    ;

    companion object {
        fun fromPreference(value: String): SdrSurfaceMode = entries.firstOrNull { it.preference == value } ?: RGBA
    }
}
