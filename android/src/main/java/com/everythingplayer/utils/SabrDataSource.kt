package com.everythingplayer.utils

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

@UnstableApi
class SabrDataSource(
    private val session: SabrPlaybackSession
) : BaseDataSource(true) {
    private var currentUri: Uri? = null
    private var dataSpec: DataSpec? = null
    private var readPosition = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        currentUri = dataSpec.uri
        readPosition = dataSpec.position
        transferInitializing(dataSpec)
        session.start()
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val spec = dataSpec ?: throw IOException("SABR data source not opened")
        val bytesRead = session.read(readPosition, buffer, offset, length)
        if (bytesRead > 0) {
            readPosition += bytesRead
            bytesTransferred(bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        if (opened) {
            opened = false
            currentUri = null
            dataSpec = null
            transferEnded()
        }
    }

    class Factory(
        private val session: SabrPlaybackSession
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SabrDataSource(session)
    }
}
