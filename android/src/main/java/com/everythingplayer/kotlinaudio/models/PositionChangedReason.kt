package com.everythingplayer.kotlinaudio.models

sealed class PositionChangedReason(val oldPosition: Long, val newPosition: Long) {
    class AUTO(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
    class QUEUE_CHANGED(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
    class SEEK(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
    class SEEK_FAILED(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
    class SKIPPED_PERIOD(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
    class UNKNOWN(oldPosition: Long, newPosition: Long) : PositionChangedReason(oldPosition, newPosition)
}
