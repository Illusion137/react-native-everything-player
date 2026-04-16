package com.everythingplayer.kotlinaudio.models

sealed class AudioItemTransitionReason(val oldPosition: Long) {
    class AUTO(oldPosition: Long) : AudioItemTransitionReason(oldPosition)
    class SEEK_TO_ANOTHER_AUDIO_ITEM(oldPosition: Long) : AudioItemTransitionReason(oldPosition)
    class REPEAT(oldPosition: Long) : AudioItemTransitionReason(oldPosition)
    class QUEUE_CHANGED(oldPosition: Long) : AudioItemTransitionReason(oldPosition)
}
