package com.vidbridge.playback

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vidbridge.MainActivity
import com.vidbridge.R

/** Small home-screen controller; video remains in the app and is never rendered in the widget. */
class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val session = PlaybackSessionStore(context).read()
        update(
            context = context,
            manager = manager,
            appWidgetIds = appWidgetIds,
            title = session?.title ?: "VidBridge",
            state = PlayerState(isPlaying = false, positionMs = session?.positionMs ?: 0L),
        )
    }

    companion object {
        fun update(context: Context, title: String, state: PlayerState) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, PlaybackWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) update(context, manager, ids, title, state)
        }

        private fun update(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            title: String,
            state: PlayerState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_playback).apply {
                setTextViewText(R.id.widget_title, title.ifBlank { "VidBridge" })
                setTextViewText(R.id.widget_status, if (state.isPlaying) "正在播放" else "已暂停")
                setTextViewText(
                    R.id.widget_progress,
                    "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                )
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                )
                setOnClickPendingIntent(R.id.widget_open, activityIntent(context))
                setOnClickPendingIntent(
                    R.id.widget_play_pause,
                    serviceIntent(context, if (state.isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_PLAY),
                )
                setOnClickPendingIntent(
                    R.id.widget_stop,
                    serviceIntent(context, PlaybackService.ACTION_STOP),
                )
            }
            manager.updateAppWidget(appWidgetIds, views)
        }

        private fun activityIntent(context: Context) = PendingIntent.getActivity(
            context,
            401,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun serviceIntent(context: Context, action: String) = PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun formatTime(milliseconds: Long): String {
            val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
            return "%02d:%02d".format(seconds / 60L, seconds % 60L)
        }
    }
}
