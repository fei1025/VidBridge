package com.vidbridge.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VidBridgeDatabase::class.java,
    )

    @Test
    fun migrate2To3MatchesExportedSchema() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """INSERT INTO media_sources (
                    id, displayName, type, scheme, host, port, tls, rootPath, shareName,
                    rootUri, username, credentialId, enabled, createdAtEpochMs, updatedAtEpochMs
                ) VALUES ('source', 'NAS', 'SMB', 'smb', '192.168.1.2', 445, 0, '', 'Videos',
                    NULL, 'user', 'credential', 1, 1, 1)""",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            VidBridgeDatabase.MIGRATION_2_3,
        ).close()
    }

    @Test
    fun migrate3To4AddsMediaGroupingColumns() {
        helper.createDatabase(DATABASE_NAME + "-v3", 3).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME + "-v3",
            4,
            true,
            VidBridgeDatabase.MIGRATION_3_4,
        ).close()
    }

    @Test
    fun migrate4To5AddsPlaylists() {
        helper.createDatabase(DATABASE_NAME + "-v4", 4).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME + "-v4",
            5,
            true,
            VidBridgeDatabase.MIGRATION_4_5,
        ).close()
    }

    @Test
    fun migrate5To6AddsDownloads() {
        helper.createDatabase(DATABASE_NAME + "-v5", 5).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME + "-v5",
            6,
            true,
            VidBridgeDatabase.MIGRATION_5_6,
        ).close()
    }

    @Test
    fun migrate6To7AddsCreditsMetadata() {
        helper.createDatabase(DATABASE_NAME + "-v6", 6).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME + "-v6",
            7,
            true,
            VidBridgeDatabase.MIGRATION_6_7,
        ).close()
    }

    @Test
    fun migrate7To8AddsPlaybackIndexes() {
        helper.createDatabase(DATABASE_NAME + "-v7", 7).close()
        helper.runMigrationsAndValidate(
            DATABASE_NAME + "-v7",
            8,
            true,
            VidBridgeDatabase.MIGRATION_7_8,
        ).close()
    }

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
