package com.vidbridge.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vidbridge.protocol.api.RemoteFileSystemFactory
import com.vidbridge.protocol.api.RemotePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Reads artwork through the source abstraction and keeps a bounded disk cache. */
class ArtworkRepository(
    context: Context,
    private val fileSystems: RemoteFileSystemFactory,
    private val httpClient: OkHttpClient,
) {
    private val directory = File(context.applicationContext.cacheDir, "artwork").apply { mkdirs() }

    suspend fun load(sourceId: String, path: String): Bitmap? = withContext(Dispatchers.IO) {
        val cacheFile = File(directory, cacheKey(sourceId, path) + ".img")
        decode(cacheFile)?.let { return@withContext it }
        if (path.startsWith("https://", ignoreCase = true)) {
            return@withContext loadHttp(path, cacheFile)
        }
        val fileSystem = runCatching { fileSystems.create(sourceId) }.getOrNull() ?: return@withContext null
        try {
            fileSystem.connect()
            val handle = fileSystem.open(RemotePath(path))
            try {
                val length = handle.size ?: return@withContext null
                if (length <= 0L || length > MAX_ARTWORK_BYTES) return@withContext null
                val bytes = handle.readAt(0L, length.toInt())
                if (bytes.isEmpty()) return@withContext null
                val temporary = File(directory, cacheFile.name + ".tmp")
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(cacheFile)) temporary.delete()
                evictIfNeeded()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } finally {
                handle.close()
            }
        } catch (_: Throwable) {
            null
        } finally {
            fileSystem.close()
        }
    }

    private fun loadHttp(url: String, cacheFile: File): Bitmap? = runCatching {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bytes = response.body?.bytes() ?: return@runCatching null
            if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return@runCatching null
            val temporary = File(directory, cacheFile.name + ".tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(cacheFile)) temporary.delete()
            evictIfNeeded()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }.getOrNull()

    private fun decode(file: File): Bitmap? = if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null

    private fun evictIfNeeded() {
        val files = directory.listFiles { file -> file.isFile && file.extension == "img" }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun cacheKey(sourceId: String, path: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$sourceId\u0000$path".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
        const val MAX_CACHE_BYTES = 96L * 1024L * 1024L
    }
}
