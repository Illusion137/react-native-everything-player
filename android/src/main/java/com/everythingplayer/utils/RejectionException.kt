package com.everythingplayer.utils

data class RejectionException(
    val code: String,
    override val message: String
) : Exception(message)
