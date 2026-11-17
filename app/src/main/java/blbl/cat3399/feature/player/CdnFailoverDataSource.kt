@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package blbl.cat3399.feature.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

internal enum class DebugStreamKind { VIDEO, AUDIO, MAIN }

internal class CdnFailoverState(
    val kind: DebugStreamKind,
    val candidates: List<Uri>,
) {
    @Volatile
    private var preferredIndex: Int = 0

    @Synchronized
    fun getPreferredIndex(): Int {
        val last = candidates.lastIndex
        return preferredIndex.coerceIn(0, last.coerceAtLeast(0))
    }

    @Synchronized
    fun prefer(index: Int) {
        val last = candidates.lastIndex
        preferredIndex = index.coerceIn(0, last.coerceAtLeast(0))
    }
}

internal class CdnFailoverDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = CdnFailoverDataSource(upstreamFactory, state)
}

internal class CdnFailoverDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource {
    private var upstream: DataSource? = null
    private var currentSpec: DataSpec? = null
    private var bytesRead = 0L
    private val transferListeners = ArrayList<TransferListener>(2)

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeQuietly()
        val candidates = state.candidates
        if (candidates.isEmpty()) throw IOException("No CDN candidates (kind=${state.kind})")

        currentSpec = dataSpec
        bytesRead = 0L
        val start = state.getPreferredIndex()
        var lastException: IOException? = null
        for (attempt in candidates.indices) {
            val idx = (start + attempt) % candidates.size
            val uri = candidates[idx]
            val ds = upstreamFactory.createDataSource()
            transferListeners.forEach { ds.addTransferListener(it) }
            val spec = dataSpec.buildUpon().setUri(uri).build()
            try {
                val openedLength = ds.open(spec)
                upstream = ds
                state.prefer(idx)
                return openedLength
            } catch (e: IOException) {
                runCatching { ds.close() }
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to open any CDN candidate (kind=${state.kind})")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val ds = upstream ?: throw IllegalStateException("read() before open() (kind=${state.kind})")
        return try {
            ds.read(buffer, offset, length).also { read ->
                if (read > 0) bytesRead += read
            }
        } catch (e: IOException) {
            if (!tryFailoverAfterReadError(e)) throw e
            val retry = upstream ?: throw e
            retry.read(buffer, offset, length).also { read ->
                if (read > 0) bytesRead += read
            }
        }
    }

    /** 播放中途 CDN 读失败时，从当前位置切换到下一个候选线路继续读取。 */
    private fun tryFailoverAfterReadError(firstError: IOException): Boolean {
        val originalSpec = currentSpec ?: return false
        val candidates = state.candidates
        if (candidates.isEmpty()) return false
        val nextPosition = originalSpec.position + bytesRead
        val start = state.getPreferredIndex()
        closeQuietly()
        for (attempt in candidates.indices) {
            val idx = (start + attempt) % candidates.size
            val uri = candidates[idx]
            val ds = upstreamFactory.createDataSource()
            transferListeners.forEach { ds.addTransferListener(it) }
            val spec =
                originalSpec.buildUpon()
                    .setUri(uri)
                    .setPosition(nextPosition)
                    .build()
            try {
                ds.open(spec)
                upstream = ds
                currentSpec = spec
                bytesRead = 0L
                state.prefer(idx)
                return true
            } catch (_: IOException) {
                runCatching { ds.close() }
            }
        }
        return false
    }

    override fun getUri(): Uri? = upstream?.uri

    override fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { upstream?.close() }
        upstream = null
    }
}
