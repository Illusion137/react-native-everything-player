package com.everythingplayer.kotlinaudio.models

enum class AudioPlayerState {
    LOADING,
    READY,
    BUFFERING,
    PAUSED,
    STOPPED,
    PLAYING,
    IDLE,
    ENDED,
    ERROR
}
