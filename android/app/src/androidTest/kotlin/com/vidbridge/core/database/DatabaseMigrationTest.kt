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

    private companion object {
        const val DATABASE_NAME = "migration-test"
    }
}
