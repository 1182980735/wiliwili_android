package blbl.cat3399.feature.player

import blbl.cat3399.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.util.Locale

internal data class DlnaDevice(
    val name: String,
    val location: String,
    val server: String,
    val deviceType: String,
    val usn: String,
) {
    val host: String
        get() = runCatching { URI(location).host }.getOrNull().orEmpty().ifBlank { location }
}

internal object DlnaDiscovery {
    private const val SSDP_HOST = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val RENDERER_SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

    suspend fun discover(timeoutMs: Long = 4_000L): List<DlnaDevice> =
        withContext(Dispatchers.IO) {
            runCatching { discoverBlocking(timeoutMs) }
                .onFailure { AppLog.w("PlayerDLNA", "SSDP discovery failed", it) }
                .getOrDefault(emptyList())
        }

    internal fun parseSearchResponse(raw: String): DlnaDevice? {
        if (raw.isBlank()) return null
        val headers = LinkedHashMap<String, String>()
        raw.lineSequence().forEach { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) headers[key] = value
        }
        val location = headers["location"].orEmpty()
        if (location.isBlank()) return null
        val st = headers["st"].orEmpty()
        val usn = headers["usn"].orEmpty()
        val server = headers["server"].orEmpty()
        if (st.isNotBlank() && st.contains("MediaRenderer", ignoreCase = true).not()) return null
        val host = runCatching { URI(location).host }.getOrNull().orEmpty()
        val name =
            when {
                server.isNotBlank() -> "DLNA 设备 ${host.ifBlank { server.take(32) }}"
                host.isNotBlank() -> "DLNA 设备 $host"
                else -> "DLNA 设备"
            }
        return DlnaDevice(
            name = name,
            location = location,
            server = server,
            deviceType = st,
            usn = usn,
        )
    }

    private fun discoverBlocking(timeoutMs: Long): List<DlnaDevice> {
        val request =
            buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_HOST:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 3\r\n")
                append("ST: $RENDERER_SEARCH_TARGET\r\n")
                append("\r\n")
            }.toByteArray(Charsets.US_ASCII)
        val responseBytes = ByteArray(8 * 1024)
        val devices = LinkedHashMap<String, DlnaDevice>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 500
            val address = InetAddress.getByName(SSDP_HOST)
            socket.send(DatagramPacket(request, request.size, address, SSDP_PORT))
            val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(1_500L, 8_000L)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(responseBytes, responseBytes.size)
                val received = runCatching { socket.receive(packet) }.isSuccess
                if (received.not()) continue
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val device = parseSearchResponse(text) ?: continue
                devices.putIfAbsent(device.usn.ifBlank { device.location }, device)
            }
        }
        return devices.values.sortedBy { it.name.lowercase(Locale.US) }
    }
}
