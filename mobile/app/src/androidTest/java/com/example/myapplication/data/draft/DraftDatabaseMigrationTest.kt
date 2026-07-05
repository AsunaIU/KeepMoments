package com.example.myapplication.data.draft

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftDatabaseMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DraftDatabase::class.java
    )

    @Test
    fun migrate2To3_keepsExistingDraftAndAddsAlbumTables() {
        val now = System.currentTimeMillis()
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO drafts " +
                    "(id, ownerType, ownerUserId, title, bookType, storyPrompt, styleId, tone, fontSet, createdAt, updatedAt, lastOpenedAt) " +
                    "VALUES ('d1', 'USER', 7, 'Trip', 'PHOTOBOOK', 'hello', NULL, NULL, NULL, $now, $now, $now)"
            )
            db.execSQL(
                "INSERT INTO draft_photos " +
                    "(id, draftId, uriString, displayName, mimeType, sizeBytes, width, height, position, isValid, validationMessage) " +
                    "VALUES ('p1', 'd1', 'file:///p1.jpg', 'p1.jpg', 'image/jpeg', 1000, 1200, 900, 0, 1, NULL)"
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, DraftDatabaseMigrations.MIGRATION_2_3)

        db.query("SELECT title, storyPrompt FROM drafts WHERE id = 'd1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Trip", cursor.getString(0))
            assertEquals("hello", cursor.getString(1))
        }
        db.query("SELECT COUNT(*) FROM draft_photos WHERE draftId = 'd1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'album_pages'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
    }
}
