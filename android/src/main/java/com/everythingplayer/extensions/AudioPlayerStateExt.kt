package com.everythingplayer.extensions

import com.everythingplayer.kotlinaudio.models.AudioPlayerState
import com.everythingplayer.model.State

val AudioPlayerState.asLibState: State
    get() {
        return when (this) {
            AudioPlayerState.LOADING -> State.Loading
            AudioPlayerState.READY -> State.Ready
            AudioPlayerState.BUFFERING -> State.Buffering
            AudioPlayerState.PAUSED -> State.Paused
            AudioPlayerState.PLAYING -> State.Playing
            AudioPlayerState.IDLE -> State.None
            AudioPlayerState.ENDED -> State.Ended
            AudioPlayerState.ERROR -> State.Error
            AudioPlayerState.STOPPED -> State.Stopped
        }
    }
