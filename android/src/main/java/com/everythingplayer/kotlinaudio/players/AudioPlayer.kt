package com.everythingplayer.kotlinaudio.players

import android.content.Context
import com.everythingplayer.kotlinaudio.models.PlayerOptions

open class AudioPlayer(context: Context, playerConfig: PlayerOptions = PlayerOptions()) : BaseAudioPlayer(context, playerConfig)
