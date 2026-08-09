package com.jolno.mygallery.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jolno.mygallery.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_OWNER = "jolno6212-dot"
private const val GITHUB_REPO = "mygallery"

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String
)

suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tagName = json.getString("tag_name")
        val remoteVersionCode = Regex("\\d+").findAll(tagName).lastOrNull()?.value?.toIntOrNull()
            ?: return@withContext null
        if (remoteVersionCode <= BuildConfig.VERSION_CODE) return@withContext null

        val assets = json.getJSONArray("assets")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }
        val downloadUrl = apkUrl ?: return@withContext null

        UpdateInfo(
            versionCode = remoteVersionCode,
            versionName = json.optString("name", tagName),
            downloadUrl = downloadUrl
        )
    }.getOrNull()
}

suspend fun downloadUpdate(context: Context, update: UpdateInfo): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "mygallery-update.apk")
        val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }.getOrNull()
}

fun installUpdate(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun canRequestPackageInstalls(context: Context): Boolean {
    return context.packageManager.canRequestPackageInstalls()
}

fun requestInstallPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}
