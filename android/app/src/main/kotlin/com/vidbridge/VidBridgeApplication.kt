package com.vidbridge

import android.app.Application
import com.vidbridge.core.database.VidBridgeDatabase
import com.vidbridge.core.security.KeystoreCredentialStore
import com.vidbridge.core.settings.SettingsRepository
import com.vidbridge.library.MediaLibraryRepository
import com.vidbridge.protocol.api.RemoteFileSystemFactory
import com.vidbridge.playback.PlaybackHistoryRepository
import com.vidbridge.sources.SourceRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VidBridgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database = VidBridgeDatabase.create(application)
    val credentialStore = KeystoreCredentialStore(application)
    val settings = SettingsRepository(application)
    val sources = SourceRepository(application, database.mediaSources(), credentialStore)
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val playbackHistory = PlaybackHistoryRepository(database.playbackHistory())
    val mediaLibrary = MediaLibraryRepository(application, database.mediaLibrary(), database.scanJobs())
    val fileSystems = RemoteFileSystemFactory(application, sources, credentialStore, httpClient)
}
