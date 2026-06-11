package com.example

import java.io.File

data class MinecraftWorld(
    val directoryName: String,
    val displayName: String,
    val localIconFile: File?,
    val sizeBytes: Long,
    val packageSource: String,
    val lastModified: Long
)
