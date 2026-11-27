
package com.mio.launcher.models

data class AppFolder(
    val id: String,
    var name: String,
    val apps: MutableList<String> = mutableListOf()
)
