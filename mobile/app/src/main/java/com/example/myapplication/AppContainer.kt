package com.example.myapplication

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.auth.AuthApi
import com.example.myapplication.data.auth.AuthInterceptor
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.auth.SessionStore
import com.example.myapplication.data.auth.TokenAuthenticator
import com.example.myapplication.data.album.AlbumRepository
import com.example.myapplication.data.books.BackendBooksRepository
import com.example.myapplication.data.books.BooksRepository
import com.example.myapplication.data.books.PhotosApi
import com.example.myapplication.data.books.ProcessApi
import com.example.myapplication.data.books.RenderedBookStore
import com.example.myapplication.data.books.TemplatesApi
import com.example.myapplication.data.draft.DraftDatabase
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.AndroidMediaMetadataReader
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.data.media.PhotoValidator
import com.example.myapplication.data.pdf.AndroidPdfExporter
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(
    context: Context
) {

    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy {
        SessionStore(appContext)
    }

    private val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val authApi: AuthApi by lazy {
        baseRetrofit.create(AuthApi::class.java)
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            authApi = authApi,
            sessionStore = sessionStore
        )
    }

    val authorizedOkHttpClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor(sessionStore))
            .authenticator(TokenAuthenticator(authRepository))
            .build()
    }

    private val baseRetrofit: Retrofit by lazy {
        createRetrofit(baseOkHttpClient)
    }

    private val authorizedRetrofit: Retrofit by lazy {
        createRetrofit(authorizedOkHttpClient)
    }

    private val templatesApi: TemplatesApi by lazy {
        authorizedRetrofit.create(TemplatesApi::class.java)
    }

    private val photosApi: PhotosApi by lazy {
        authorizedRetrofit.create(PhotosApi::class.java)
    }

    private val processApi: ProcessApi by lazy {
        baseRetrofit.create(ProcessApi::class.java)
    }

    private val draftDatabase: DraftDatabase by lazy {
        Room.databaseBuilder(appContext, DraftDatabase::class.java, "keepmoments.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    val draftRepository: DraftRepository by lazy {
        DraftRepository(draftDatabase.draftDao())
    }

    val albumRepository: AlbumRepository by lazy {
        AlbumRepository(
            draftDao = draftDatabase.draftDao(),
            albumDao = draftDatabase.albumDao()
        )
    }

    val profileStore by lazy {
        com.example.myapplication.data.profile.ProfileStore(appContext)
    }

    val mediaMetadataReader: AndroidMediaMetadataReader by lazy {
        AndroidMediaMetadataReader(appContext.contentResolver)
    }

    private val photoValidator: PhotoValidator by lazy {
        PhotoValidator()
    }

    val photoImportService: PhotoImportService by lazy {
        PhotoImportService(
            mediaMetadataReader = mediaMetadataReader,
            photoValidator = photoValidator
        )
    }

    val renderedBookStore: RenderedBookStore by lazy {
        RenderedBookStore()
    }

    val pdfExporter by lazy {
        AndroidPdfExporter(appContext.contentResolver)
    }

    val booksRepository: BooksRepository by lazy {
        BackendBooksRepository(
            authRepository = authRepository,
            templatesApi = templatesApi,
            photosApi = photosApi,
            processApi = processApi,
            contentResolver = appContext.contentResolver
        )
    }

    private fun createRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private companion object {
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
    }
}
