package com.example.myapplication.data.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/api/v1/auth/")) {
            return chain.proceed(request)
        }

        val session = runBlocking { sessionStore.getSession() }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "${session.tokenType} ${session.accessToken}")
                .build()
        )
    }
}
