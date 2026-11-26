package com.intec.immichfire.data

data class ImmichSettings(
    val serverUrl: String = "",
    val apiKey: String = "",
    val slideshowIntervalMs: Long = 5_000L,
    val transition: Transition = Transition.Crossfade,
    val shuffle: Boolean = false,
    val loop: Boolean = true,
    val fitMode: FitMode = FitMode.Fit,
    val sortOrder: SortOrder = SortOrder.NewestFirst,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && apiKey.isNotBlank()
}

enum class Transition { None, Crossfade, Slide, KenBurns }
enum class FitMode { Fit, Fill, FillBlurBackdrop }
enum class SortOrder { NewestFirst, OldestFirst }
