package com.vidbridge.core.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.playerSettingsStore by preferencesDataStore("player_settings")

enum class VideoScale { FIT, FILL, ZOOM }
enum class BufferPreset { LOW_LATENCY, BALANCED, STABLE }

data class PlayerPreferences(
    val playbackSpeed: Float = 1f,
    val videoScale: VideoScale = VideoScale.FIT,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,
    val rememberProgress: Boolean = true,
    val completionThreshold: Float = 0.9f,
    val autoLandscape: Boolean = false,
    val keepScreenOn: Boolean = true,
    val showHiddenFiles: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.playerSettingsStore

    val preferences: Flow<PlayerPreferences> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::decode)

    suspend fun setPlaybackSpeed(value: Float) = edit { it[SPEED] = value.coerceIn(0.5f, 2f) }
    suspend fun setVideoScale(value: VideoScale) = edit { it[SCALE] = value.name }
    suspend fun setBufferPreset(value: BufferPreset) = edit { it[BUFFER] = value.name }
    suspend fun setRememberProgress(value: Boolean) = edit { it[REMEMBER] = value }
    suspend fun setCompletionThreshold(value: Float) = edit { it[COMPLETION] = value.coerceIn(0.5f, 1f) }
    suspend fun setAutoLandscape(value: Boolean) = edit { it[AUTO_LANDSCAPE] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[KEEP_SCREEN_ON] = value }
    suspend fun setShowHiddenFiles(value: Boolean) = edit { it[SHOW_HIDDEN] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private fun decode(values: Preferences) = PlayerPreferences(
        playbackSpeed = values[SPEED]?.coerceIn(0.5f, 2f) ?: 1f,
        videoScale = values[SCALE]?.let { runCatching { VideoScale.valueOf(it) }.getOrNull() } ?: VideoScale.FIT,
        bufferPreset = values[BUFFER]?.let { runCatching { BufferPreset.valueOf(it) }.getOrNull() } ?: BufferPreset.BALANCED,
        rememberProgress = values[REMEMBER] ?: true,
        completionThreshold = values[COMPLETION]?.coerceIn(0.5f, 1f) ?: 0.9f,
        autoLandscape = values[AUTO_LANDSCAPE] ?: false,
        keepScreenOn = values[KEEP_SCREEN_ON] ?: true,
        showHiddenFiles = values[SHOW_HIDDEN] ?: false,
    )

    private companion object {
        val SPEED = floatPreferencesKey("playback_speed")
        val SCALE = stringPreferencesKey("video_scale")
        val BUFFER = stringPreferencesKey("buffer_preset")
        val REMEMBER = booleanPreferencesKey("remember_progress")
        val COMPLETION = floatPreferencesKey("completion_threshold")
        val AUTO_LANDSCAPE = booleanPreferencesKey("auto_landscape")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
    }
}
