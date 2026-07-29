package com.vidbridge.playback

import android.content.Context

data class PlaybackSession(
    val sourceId: String,
    val path: String,
    val title: String,
    val positionMs: Long = 0L,
    val localPath: String? = null,
    val playlistId: String? = null,
)

/** Returns a recoverable local file only for the exact persisted media item. */
internal fun localRecoveryPath(
    session: PlaybackSession?,
    sourceId: String,
    path: String,
    isFile: (String) -> Boolean,
): String? = session
    ?.takeIf { it.sourceId == sourceId && it.path == path }
    ?.localPath
    ?.takeIf(isFile)

/** Persists only non-sensitive playback identity so a killed process can reopen the last item. */
class PlaybackSessionStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("playback_session", Context.MODE_PRIVATE)

    fun read(): PlaybackSession? {
        val sourceId = preferences.getString(SOURCE_ID, null)?.takeIf(String::isNotBlank) ?: return null
        val path = preferences.getString(PATH, null)?.takeIf(String::isNotBlank) ?: return null
        return PlaybackSession(
            sourceId,
            path,
            preferences.getString(TITLE, "VidBridge").orEmpty(),
            preferences.getLong(POSITION_MS, 0L).coerceAtLeast(0L),
            preferences.getString(LOCAL_PATH, null),
            preferences.getString(PLAYLIST_ID, null),
        )
    }

    fun save(session: PlaybackSession) {
        preferences.edit()
            .putString(SOURCE_ID, session.sourceId)
            .putString(PATH, session.path)
            .putString(TITLE, session.title)
            .putLong(POSITION_MS, session.positionMs.coerceAtLeast(0L))
            .apply {
                if (session.localPath.isNullOrBlank()) remove(LOCAL_PATH)
                else putString(LOCAL_PATH, session.localPath)
                if (session.playlistId.isNullOrBlank()) remove(PLAYLIST_ID)
                else putString(PLAYLIST_ID, session.playlistId)
            }
            .apply()
    }

    fun updatePosition(positionMs: Long) {
        if (read() == null) return
        preferences.edit().putLong(POSITION_MS, positionMs.coerceAtLeast(0L)).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val SOURCE_ID = "source_id"
        const val PATH = "path"
        const val TITLE = "title"
        const val POSITION_MS = "position_ms"
        const val LOCAL_PATH = "local_path"
        const val PLAYLIST_ID = "playlist_id"
    }
}
