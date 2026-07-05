package com.example.myapplication

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.example.myapplication.data.auth.AuthApi
import com.example.myapplication.data.auth.AuthInterceptor
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.auth.DataStoreSessionStore
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
import com.example.myapplication.data.draft.DraftDatabaseMigrations
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.AndroidMediaMetadataReader
import com.example.myapplication.data.media.PhotoLocalStorage
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.data.media.PhotoValidator
import com.example.myapplication.data.pdf.AndroidPdfExporter
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(
    context: Context
) {

    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy {
        DataStoreSessionStore(appContext)
    }

    private val baseOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
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

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient(authorizedOkHttpClient)
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
            .addMigrations(*DraftDatabaseMigrations.ALL)
            .fallbackToDestructiveMigration()
            .build()
    }

    val draftRepository: DraftRepository by lazy {
        DraftRepository(
            draftDao = draftDatabase.draftDao(),
            photoLocalStorage = photoLocalStorage
        )
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

    private val photoLocalStorage: PhotoLocalStorage by lazy {
        PhotoLocalStorage(
            contentResolver = appContext.contentResolver,
            storageDir = File(appContext.filesDir, "imported_photos")
        )
    }

    val photoImportService: PhotoImportService by lazy {
        PhotoImportService(
            mediaMetadataReader = mediaMetadataReader,
            photoValidator = photoValidator,
            photoLocalStorage = photoLocalStorage
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
}
