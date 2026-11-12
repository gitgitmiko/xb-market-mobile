package com.xbmarket.mobile.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.xbmarket.mobile.BuildConfig
import com.xbmarket.mobile.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Rilis yang lebih baru dari versi terpasang. */
data class AppUpdate(val versionName: String, val notes: String, val apkUrl: String, val size: Long)

/**
 * Update OTA dari GitHub Releases (repo publik, tanpa token). Rilis dibuat oleh tools/release.sh:
 * tag `v<versionName>` dengan satu aset APK yang ditandatangani kunci rilis yang sama.
 */
object AppUpdater {
    private const val LATEST = "https://api.github.com/repos/gitgitmiko/xb-market-mobile/releases/latest"

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val url: String,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val downloadClient = Http.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    /** Null bila sudah versi terbaru atau rilis tidak punya APK. */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "XBMarket/${BuildConfig.VERSION_NAME}")
            .build()
        val release = Http.client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null // belum ada rilis
            if (!response.isSuccessful) error("GitHub HTTP ${response.code}")
            json.decodeFromString<Release>(response.body!!.string())
        }
        if (release.draft || release.prerelease) return@withContext null
        val version = release.tag.removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
        AppUpdate(version, release.body.orEmpty().trim(), apk.url, apk.size)
    }

    /** Bandingkan versi "1.2.3" per angka; bagian yang tidak ada dianggap 0. */
    fun isNewer(remote: String, installed: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(context: Context, update: AppUpdate, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "xb-market-${update.versionName}.apk")
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "XBMarket/${BuildConfig.VERSION_NAME}").build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Unduhan gagal (HTTP ${response.code})")
            val body = response.body!!
            val total = body.contentLength().takeIf { it > 0 } ?: update.size
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (update.size > 0 && out.length() != update.size) error("File unduhan tidak lengkap")
        out
    }

    /** Android 8+: izin "Instal aplikasi tidak dikenal" untuk XB Market. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
