package com.envobyte.simpleequalizer.model

import androidx.annotation.StringRes

data class Preset(
    @StringRes
    val id: Int,
    val map: Map<Int, Float>
)