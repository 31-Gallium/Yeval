package com.mobilecontroller

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val hasUpdate: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val publishedAt: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_REPO = "31-Gallium/Yeval"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Robust Semantic Version Comparison
     * Evaluates integer components: 1.0.9 is strictly lower than 1.0.10, 1.2.0 > 1.1.9, etc.
     */
    fun isNewerVersion(currentVersionStr: String, remoteVersionStr: String): Boolean {
        try {
            val cleanCurrent = currentVersionStr.removePrefix("v").removePrefix("V").trim()
            val cleanRemote = remoteVersionStr.removePrefix("v").removePrefix("V").trim()

            val curParts = cleanCurrent.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val remParts = cleanRemote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(curParts.size, remParts.size)
            for (i in 0 until maxLen) {
                val c = if (i < curParts.size) curParts[i] else 0
                val r = if (i < remParts.size) remParts[i] else 0
                if (r > c) return true
                if (r < c) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $currentVersionStr vs $remoteVersionStr", e)
        }
        return false
    }

    /**
     * Checks GitHub for the latest release asynchronously.
     */
    fun checkForUpdate(context: Context, callback: (UpdateInfo?) -> Unit) {
        val currentVersion = getAppVersionName(context)

        Thread {
            try {
                val request = Request.Builder()
                    .url(RELEASES_API_URL)
                    .header("User-Agent", "Yeval-Android-App")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GitHub API returned ${response.code}")
                        mainHandler.post { callback(null) }
                        return@Thread
                    }

                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)

                    val tagName = json.optString("tag_name", "")
                    val releaseNotes = json.optString("body", "")
                    val publishedAt = json.optString("published_at", "")

                    var apkUrl = ""
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    val hasUpdate = apkUrl.isNotEmpty() && isNewerVersion(currentVersion, tagName)

                    val info = UpdateInfo(
                        hasUpdate = hasUpdate,
                        currentVersion = currentVersion,
                        latestVersion = tagName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl,
                        publishedAt = publishedAt
                    )

                    mainHandler.post { callback(info) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                mainHandler.post { callback(null) }
            }
        }.start()
    }

    /**
     * Downloads the APK file with progress reporting and triggers the installer intent.
     */
    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()

                val destinationFile = File(updateDir, "Yeval-Update.apk")
                if (destinationFile.exists()) destinationFile.delete()

                val request = Request.Builder()
                    .url(apkUrl)
                    .header("User-Agent", "Yeval-Android-App")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        mainHandler.post { onError("Download failed (HTTP ${response.code})") }
                        return@Thread
                    }

                    val body = response.body ?: throw Exception("Empty response body")
                    val totalBytes = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesCopied = 0L
                            var lastProgress = -1

                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesCopied += read

                                if (totalBytes > 0) {
                                    val progress = ((bytesCopied * 100) / totalBytes).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        mainHandler.post { onProgress(progress) }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    mainHandler.post {
                        onProgress(100)
                        installApk(context, destinationFile)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK update", e)
                mainHandler.post { onError("Download failed: ${e.localizedMessage}") }
            }
        }.start()
    }

    /**
     * Launches the native Android Package Installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return
            }

            // Ensure file permissions allow the external package installer process to read the APK
            apkFile.setReadable(true, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch APK installer", e)
        }
    }

    fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
