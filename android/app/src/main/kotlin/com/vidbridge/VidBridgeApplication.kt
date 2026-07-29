package com.vidbridge

import android.app.Application
import com.vidbridge.core.diagnostics.CrashReporter
import com.vidbridge.core.database.VidBridgeDatabase
import com.vidbridge.core.security.KeystoreCredentialStore
import com.vidbridge.core.settings.SettingsRepository
import com.vidbridge.library.MediaLibraryRepository
import com.vidbridge.library.ArtworkRepository
import com.vidbridge.library.TmdbMetadataRepository
import com.vidbridge.library.PlaylistRepository
import com.vidbridge.library.DownloadRepository
import com.vidbridge.library.LibraryRefreshWorker
import com.vidbridge.library.MetadataRefreshWorker
import com.vidbridge.protocol.api.RemoteFileSystemFactory
import com.vidbridge.playback.PlaybackHistoryRepository
import com.vidbridge.playback.PlaybackSessionStore
import com.vidbridge.sources.SourceRepository
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class VidBridgeApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter(this).install()
        container = AppContainer(this)
        applicationScope.launch { container.downloads.reconcileOnStartup() }
        LibraryRefreshWorker.schedule(this)
        MetadataRefreshWorker.schedule(this)
    }
}

class AppContainer(application: Application) {
    val database = VidBridgeDatabase.create(application)
    val credentialStore = KeystoreCredentialStore(application)
    val settings = SettingsRepository(application, credentialStore)
    val sources = SourceRepository(application, database.mediaSources(), credentialStore)
    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val playbackHistory = PlaybackHistoryRepository(database.playbackHistory())
    val playbackSession = PlaybackSessionStore(application)
    val mediaLibrary = MediaLibraryRepository(application, database.mediaLibrary(), database.scanJobs())
    val fileSystems = RemoteFileSystemFactory(application, sources, credentialStore, httpClient)
    val artwork = ArtworkRepository(application, fileSystems, httpClient)
    val tmdb = TmdbMetadataRepository(database.mediaLibrary(), httpClient)
    val playlists = PlaylistRepository(database.playlists())
    val downloads = DownloadRepository(application, database.downloads(), sources)
}
