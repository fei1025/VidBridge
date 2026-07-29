package com.vidbridge.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vidbridge.VidBridgeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles actions from an active download notification without duplicating download state. */
class DownloadNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_PAUSE) {
                    (context.applicationContext as VidBridgeApplication).container.downloads.pause(id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.vidbridge.action.PAUSE_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
