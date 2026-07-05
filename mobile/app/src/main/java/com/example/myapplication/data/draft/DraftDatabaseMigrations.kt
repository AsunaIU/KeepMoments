package com.example.myapplication.data.draft

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DraftDatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS album_pages (
                    id TEXT NOT NULL PRIMARY KEY,
                    draftId TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    layoutId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(draftId) REFERENCES drafts(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_pages_draftId ON album_pages(draftId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_pages_position ON album_pages(position)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS album_slots (
                    id TEXT NOT NULL PRIMARY KEY,
                    pageId TEXT NOT NULL,
                    slotKey TEXT NOT NULL,
                    photoId TEXT,
                    caption TEXT NOT NULL,
                    cropScale REAL NOT NULL,
                    cropOffsetX REAL NOT NULL,
                    cropOffsetY REAL NOT NULL,
                    FOREIGN KEY(pageId) REFERENCES album_pages(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(photoId) REFERENCES draft_photos(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_slots_pageId ON album_slots(pageId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_slots_photoId ON album_slots(photoId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS album_stickers (
                    id TEXT NOT NULL PRIMARY KEY,
                    pageId TEXT NOT NULL,
                    sticker TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    scale REAL NOT NULL,
                    rotation REAL NOT NULL,
                    zIndex INTEGER NOT NULL,
                    FOREIGN KEY(pageId) REFERENCES album_pages(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_stickers_pageId ON album_stickers(pageId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_stickers_zIndex ON album_stickers(zIndex)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE drafts ADD COLUMN generateCaptions INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE album_slots ADD COLUMN remotePhotoId TEXT")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE album_pages ADD COLUMN caption TEXT NOT NULL DEFAULT ''")
        }
    }

    val ALL = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
