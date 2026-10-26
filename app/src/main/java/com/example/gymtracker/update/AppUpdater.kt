package com.example.gymtracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class AppUpdate(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val sha256: String?
)

object AppUpdater {
    private const val RELEASE_API = "https://api.github.com/repos/lucaaa02/gymBuddy/releases/latest"
    private const val USER_AGENT = "GymTracker-Android-Updater"

    fun checkForUpdate(context: Context): AppUpdate? {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val tag = release.optString("tag_name").removePrefix("v")
            @Suppress("DEPRECATION")
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            if (compareVersions(tag, installed) <= 0) return null

            val assets = release.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).asSequence()
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").matches(Regex("gymtracker-v[0-9]+(?:\\.[0-9]+){1,2}\\.apk", RegexOption.IGNORE_CASE)) }
                ?: return null
            val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return AppUpdate(
                version = tag,
                notes = release.optString("body").take(800).ifBlank { "È disponibile una nuova versione." },
                downloadUrl = apk.getString("browser_download_url"),
                sha256 = digest
            )
        } finally {
            connection.disconnect()
        }
    }

    fun downloadUpdate(context: Context, update: AppUpdate): File {
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        val apkFile = File(directory, "gymtracker-update.apk")
        val partialFile = File(directory, "gymtracker-update.apk.part")
        val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("GitHub non ha restituito l'APK (HTTP ${connection.responseCode}).")
            }
            connection.inputStream.use { input ->
                partialFile.outputStream().buffered().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        if (partialFile.length() == 0L) {
            partialFile.delete()
            error("Il file scaricato è vuoto.")
        }
        update.sha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            partialFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                partialFile.delete()
                error("Il controllo di integrità dell'APK non è riuscito.")
            }
        }

        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(partialFile.absolutePath, 0)
            ?: run { partialFile.delete(); error("Il file scaricato non è un APK valido.") }
        if (archive.packageName != context.packageName) {
            partialFile.delete()
            error("L'APK non appartiene a Gym Tracker.")
        }
        val currentPackage = context.packageManager.getPackageInfo(context.packageName, 0)
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        @Suppress("DEPRECATION")
        val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) currentPackage.longVersionCode else currentPackage.versionCode.toLong()
        if (archive.versionName?.removePrefix("v") != update.version || archiveVersionCode <= installedVersionCode) {
            partialFile.delete()
            error("L'APK scaricato non corrisponde alla versione pubblicata o non è più recente.")
        }

        if (apkFile.exists()) apkFile.delete()
        if (!partialFile.renameTo(apkFile)) error("Non è stato possibile salvare l'APK scaricato.")
        return apkFile
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun compareVersions(first: String, second: String): Int {
        fun parts(version: String): List<Int>? {
            val match = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$").matchEntire(version) ?: return null
            return listOf(1, 2, 3).map { match.groupValues[it].toIntOrNull() ?: 0 }
        }
        val a = parts(first) ?: return -1
        val b = parts(second) ?: return 0
        for (index in 0..2) {
            if (a[index] != b[index]) return a[index].compareTo(b[index])
        }
        return 0
    }
}
