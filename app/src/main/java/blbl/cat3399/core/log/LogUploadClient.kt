package blbl.cat3399.core.log

import android.os.Build
import android.util.Base64
import blbl.cat3399.BuildConfig
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.net.await
import blbl.cat3399.core.net.ipv4OnlyDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 将日志 zip 保存到 Gitee 仓库 log/，并创建一个带下载链接的 Issue。
 *
 * Gitee OpenAPI 目前没有 Issue 附件上传接口，所以采用“仓库文件 + Issue 链接”的方式：
 * - 文件：log/<epoch>-<device8>.zip
 * - Issue 正文：应用/设备元数据和日志下载链接
 */
object LogUploadClient {
    private const val TAG = "LogUploadClient"

    private const val GITEE_API = "https://gitee.com/api/v5"
    private const val GITEE_OWNER = "anhuihl"
    private const val GITEE_REPO = "wiliwili_android"
    private const val GITEE_BRANCH = "main"
    private const val LOG_DIR = "log"
    private const val MAX_UPLOAD_BYTES: Long = 20L * 1024 * 1024

    data class UploadResult(
        /** Gitee 仓库中的文件路径，例如 log/1794xxxx-deadbeef.zip */
        val key: String,
        /** Gitee Issue 编号 */
        val id: String?,
        val sizeBytes: Long?,
        val issueUrl: String? = null,
        val fileUrl: String? = null,
    )

    private val okHttpLazy: Lazy<OkHttpClient> =
        lazy {
            OkHttpClient.Builder()
                .dns(ipv4OnlyDns { BiliClient.prefs.ipv4OnlyEnabled })
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()
        }

    private val okHttp: OkHttpClient
        get() = okHttpLazy.value

    suspend fun uploadZip(
        file: File,
        fileName: String = file.name,
        issueTitle: String? = null,
        issueBody: String? = null,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): UploadResult =
        withContext(Dispatchers.IO) {
            val f = file.takeIf { it.exists() && it.isFile } ?: throw IOException("日志文件不存在")
            val len = runCatching { f.length() }.getOrDefault(0L).coerceAtLeast(0L)
            if (len <= 0L) throw IOException("日志文件为空")
            if (len > MAX_UPLOAD_BYTES) throw IOException("日志文件过大：${len}B（上限 ${MAX_UPLOAD_BYTES}B）")

            val token = BuildConfig.GITEE_LOG_TOKEN.trim()
            if (token.isBlank()) {
                throw IOException("未配置 Gitee 日志上传 token，请重新构建应用")
            }

            val safeName = sanitizeFileName(fileName).ifBlank { f.name }
            val safeZipName = if (safeName.endsWith(".zip", ignoreCase = true)) safeName else "$safeName.zip"
            val encodedName = encodePathSegment(safeZipName)
            val filePath = "$LOG_DIR/$safeZipName"
            val fileApiUrl = "$GITEE_API/repos/$GITEE_OWNER/$GITEE_REPO/contents/$LOG_DIR/$encodedName"
            val rawFileUrl = "https://gitee.com/$GITEE_OWNER/$GITEE_REPO/raw/$GITEE_BRANCH/$LOG_DIR/$encodedName"

            onProgress?.invoke(0L, len)
            val base64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            val uploadForm =
                FormBody.Builder()
                    .add("access_token", token)
                    .add("content", base64)
                    .add("message", "chore: upload log $safeZipName")
                    .add("branch", GITEE_BRANCH)
                    .build()
            postForm(fileApiUrl, uploadForm, "日志文件上传")
            onProgress?.invoke(len / 2L, len)

            val body =
                buildString {
                    append(issueBody?.trim().orEmpty())
                    if (isNotEmpty()) append("\n\n")
                    append("## 日志文件\n\n")
                    append("- 文件：`$filePath`\n")
                    append("- 下载：[$safeZipName]($rawFileUrl)\n")
                    append("- 应用：`${BuildConfig.VERSION_NAME}` (`${BuildConfig.VERSION_CODE}`)\n")
                    append("- 设备：`${Build.MANUFACTURER} ${Build.MODEL}` / Android `${Build.VERSION.RELEASE}`\n")
                }
            val issueForm =
                FormBody.Builder()
                    .add("access_token", token)
                    .add("repo", GITEE_REPO)
                    .add("title", issueTitle?.trim().takeUnless { it.isNullOrBlank() } ?: "[日志] $safeZipName")
                    .add("body", body)
                    .build()
            val issueJson =
                postForm(
                    url = "$GITEE_API/repos/$GITEE_OWNER/issues",
                    form = issueForm,
                    action = "日志 Issue 创建",
                )
            onProgress?.invoke(len, len)

            UploadResult(
                key = filePath,
                id = issueJson.opt("number")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                sizeBytes = len,
                issueUrl = issueJson.optString("html_url", "").trim().takeIf { it.isNotBlank() },
                fileUrl = rawFileUrl,
            )
        }

    private suspend fun postForm(url: String, form: RequestBody, action: String): JSONObject {
        val req =
            Request.Builder()
                .url(url)
                .post(form)
                .header("Accept", "application/json")
                .header("User-Agent", "wiliwili_A-log-upload")
                .build()
        val res = okHttp.newCall(req).await()
        res.use { r ->
            val raw = withContext(Dispatchers.IO) { r.body?.string().orEmpty() }
            if (!r.isSuccessful) {
                val msg =
                    runCatching { JSONObject(raw).optString("message", "").trim() }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                AppLog.w(TAG, "$action failed http=${r.code} body=${raw.take(300)}")
                throw IOException("${action}失败：HTTP ${r.code}${if (msg.isNullOrBlank()) "" else "（$msg）"}")
            }
            return runCatching { JSONObject(raw) }
                .getOrElse { throw IOException("${action}响应解析失败") }
        }
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return ""
        return trimmed.replace(Regex("[\\\\/\\r\\n\\t\\u0000]"), "_").take(96)
    }
}
