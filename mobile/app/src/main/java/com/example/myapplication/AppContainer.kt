package com.example.myapplication

import android.content.Context
import androidx.room.Room
import com.example.myapplication.data.auth.AuthApi
import com.example.myapplication.data.auth.AuthInterceptor
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.auth.SessionStore
import com.example.myapplication.data.auth.TokenAuthenticator
import com.example.myapplication.data.draft.DraftDatabase
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.AndroidMediaMetadataReader
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.data.media.PhotoValidator
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
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(baseOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
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

    private val draftDatabase: DraftDatabase by lazy {
        Room.databaseBuilder(appContext, DraftDatabase::class.java, "keepmoments.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val draftRepository: DraftRepository by lazy {
        DraftRepository(draftDatabase.draftDao())
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
}
