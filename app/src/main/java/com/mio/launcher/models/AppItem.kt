package com.mio.launcher.models

import android.graphics.Bitmap

class AppItem(
    val label: String,
    val pkg: String,
    val icon: Bitmap
) {
    var shown: String? = null
}
