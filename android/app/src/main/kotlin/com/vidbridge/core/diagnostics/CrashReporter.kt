package com.vidbridge.core.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps a small, local, redacted crash record for beta/device diagnostics. */
class CrashReporter(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, "crash-reports")

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun latest(): File? = directory.listFiles()
        ?.filter { it.isFile }
        ?.maxByOrNull { it.lastModified() }

    /** Copies the latest redacted report into a shareable cache location. */
    fun copyLatestToCache(): File? {
        val report = latest() ?: return null
        val shareDirectory = File(appContext.cacheDir, "crash-share")
        shareDirectory.mkdirs()
        val target = File(shareDirectory, report.name)
        report.copyTo(target, overwrite = true)
        return target
    }

    private fun write(thread: Thread, throwable: Throwable) {
        directory.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = buildString {
            appendLine("VidBridge crash report")
            appendLine("time=$stamp")
            appendLine("thread=${thread.name}")
            appendLine()
            appendLine(CrashTextSanitizer.redact(throwable.stackTraceToString()))
        }
        File(directory, "crash-$stamp.txt").writeText(report, Charsets.UTF_8)
        directory.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
    }

    companion object {
        private const val MAX_REPORTS = 5
    }
}

internal object CrashTextSanitizer {
    private val authorityCredentials = Regex("(?i)([a-z][a-z0-9+.-]*://)([^/@\\s:]+):([^/@\\s]+)@")
    private val sensitiveFields = Regex("(?i)(password|passwd|pwd|token|authorization|cookie)\\s*[=:]\\s*([^\\s,;]+)")

    fun redact(value: String): String = value
        .replace(authorityCredentials, "$1<redacted>@")
        .replace(sensitiveFields, "$1=<redacted>")
}
