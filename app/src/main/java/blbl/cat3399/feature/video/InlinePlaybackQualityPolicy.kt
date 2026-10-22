package blbl.cat3399.feature.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.util.Locale

internal data class InlineDecoderCapability(
    val hasHardwareAvcDecoder: Boolean,
    val hardwareAvcDecoderNames: List<String>,
)

internal object InlinePlaybackQualityPolicy {
    const val FALLBACK_QN: Int = 32
    const val FALLBACK_LABEL: String = "480P"

    val qualityOptions: List<Pair<Int, String>> =
        listOf(
            127 to "8K 超高清",
            120 to "4K 超清",
            116 to "1080P60 高帧率",
            80 to "1080P 高清",
            74 to "720P60 高帧率",
            64 to "720P 高清",
            32 to "480P 清晰",
            16 to "360P 流畅",
        )

    @Volatile
    private var cachedCapability: InlineDecoderCapability? = null

    fun detect(): InlineDecoderCapability {
        cachedCapability?.let { return it }
        return synchronized(this) {
            cachedCapability ?: detectUncached().also { cachedCapability = it }
        }
    }

    fun selectInlineQn(preferredQn: Int, capability: InlineDecoderCapability): Int =
        if (capability.hasHardwareAvcDecoder) {
            normalizePreferredQn(preferredQn)
        } else {
            FALLBACK_QN
        }

    fun qualityLabel(qn: Int): String =
        qualityOptions.firstOrNull { it.first == qn }?.second ?: run {
            when {
                qn >= 127 -> "8K"
                qn >= 120 -> "4K"
                qn >= 116 -> "1080P60"
                qn >= 80 -> "1080P"
                qn >= 74 -> "720P60"
                qn >= 64 -> "720P"
                qn >= 32 -> "480P"
                qn > 0 -> "360P"
                else -> "未知"
            }
        }

    fun shortQualityLabel(label: String): String =
        label.trim().substringBefore(' ').ifBlank { label.trim() }
    private fun normalizePreferredQn(preferredQn: Int): Int {
        if (preferredQn <= 0) return qualityOptions.first().first
        return qualityOptions.map { it.first }
            .minByOrNull { kotlin.math.abs(it - preferredQn) }
            ?: preferredQn
    }

    fun isHardwareDecoder(codecInfo: MediaCodecInfo): Boolean {
        if (codecInfo.isEncoder) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return codecInfo.isHardwareAccelerated && codecInfo.isAlias.not()
        }
        return isHardwareDecoderName(codecInfo.name)
    }

    fun isHardwareDecoderName(rawName: String): Boolean {
        val name = rawName.lowercase(Locale.US)
        if (name.startsWith("omx.google.")) return false
        if (name.startsWith("c2.android.")) return false
        if (name.startsWith("c2.google.")) return false
        if (name.contains(".sw.")) return false
        if (name.contains("software")) return false
        if (name.contains("ffmpeg")) return false
        return true
    }

    private fun detectUncached(): InlineDecoderCapability {
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
        val hardware = Build.HARDWARE.lowercase(Locale.US)
        val product = Build.PRODUCT.lowercase(Locale.US)
        val isEmulatorLike =
            Build.SUPPORTED_ABIS.any { it.lowercase(Locale.US).startsWith("x86") } ||
                fingerprint.contains("generic") ||
                fingerprint.contains("emulator") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk")
        if (isEmulatorLike) {
            return InlineDecoderCapability(false, emptyList())
        }
        val hardwareCodecs = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { info ->
                val supportsAvc = info.supportedTypes.any { mime -> mime.equals("video/avc", ignoreCase = true) }
                supportsAvc && isHardwareDecoder(info)
            }
        }.getOrDefault(emptyList())
        return InlineDecoderCapability(
            hasHardwareAvcDecoder = hardwareCodecs.isNotEmpty(),
            hardwareAvcDecoderNames = hardwareCodecs.map { it.name }.distinct().sorted(),
        )
    }
}
