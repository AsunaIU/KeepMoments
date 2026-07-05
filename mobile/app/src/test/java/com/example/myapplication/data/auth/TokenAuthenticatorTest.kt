package com.example.myapplication.data.auth

import com.example.myapplication.testutil.factories.authSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TokenAuthenticatorTest {

    private val server = MockWebServer()
    private val authRepository = mockk<AuthRepository>()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .authenticator(TokenAuthenticator(authRepository))
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun execute(path: String, authorization: String? = "Bearer old"): Response {
        val builder = Request.Builder().url(server.url(path))
        authorization?.let { builder.header("Authorization", it) }
        return client.newCall(builder.build()).execute()
    }

    @Test
    fun `refreshes and retries the request with the new token`() {
        coEvery { authRepository.refreshTokens() } returns Result.success(authSession(accessToken = "new"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200))

        val response = execute("/api/v1/books")
        assertEquals(200, response.code)
        response.close()

        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        coVerify(exactly = 1) { authRepository.refreshTokens() }
    }

    @Test
    fun `gives up after a second unauthorized response`() {
        coEvery { authRepository.refreshTokens() } returns Result.success(authSession(accessToken = "new"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401))

        val response = execute("/api/v1/books")
        assertEquals(401, response.code)
        response.close()

        assertEquals(2, server.requestCount)
        coVerify(exactly = 1) { authRepository.refreshTokens() }
    }

    @Test
    fun `does not refresh the auth endpoints`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val response = execute("/api/v1/auth/login")
        assertEquals(401, response.code)
        response.close()

        assertEquals(1, server.requestCount)
        coVerify(exactly = 0) { authRepository.refreshTokens() }
    }

    @Test
    fun `does not refresh unauthenticated requests`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val response = execute("/api/v1/books", authorization = null)
        assertEquals(401, response.code)
        response.close()

        coVerify(exactly = 0) { authRepository.refreshTokens() }
    }
}
