package com.everythingplayer.kotlinaudio.models

data class CacheConfig(
    val maxCacheSize: Long?,
    val identifier: String = "EverythingPlayer"
)
