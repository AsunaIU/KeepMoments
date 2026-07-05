package com.example.myapplication.data.auth

import com.example.myapplication.testutil.factories.authSession
import com.example.myapplication.testutil.fakes.FakeSessionStore
import com.example.myapplication.testutil.fixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthRepositoryTest {

    private val server = MockWebServer()
    private lateinit var store: FakeSessionStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
        store = FakeSessionStore()
        repository = AuthRepository(api, store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login stores the session on success`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("auth_success.json")))

        val result = repository.login("  user@example.com  ", " secret ")

        val session = result.getOrThrow()
        assertEquals("access-123", session.accessToken)
        assertEquals(42L, session.userId)
        assertEquals("access-123", store.savedResponse?.accessToken)

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        assertTrue(request.body.readUtf8().contains("\"email\":\"user@example.com\""))
    }

    @Test
    fun `register hits the register endpoint and stores the session`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("auth_success.json")))

        val result = repository.register("user@example.com", "secret")

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/auth/register", server.takeRequest().path)
    }

    @Test
    fun `login surfaces the server error message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(422).setBody(fixture("auth_error.json")))

        val result = repository.login("user@example.com", "secret")

        assertEquals("Неверный email или пароль", result.exceptionOrNull()?.message)
        assertNull(store.savedResponse)
    }

    @Test
    fun `login falls back to the raw error body when it is not json`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("upstream is down"))

        val result = repository.login("user@example.com", "secret")

        assertEquals("upstream is down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `login reports a generic error for an empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.login("user@example.com", "secret")

        assertEquals("Не удалось выполнить запрос", result.exceptionOrNull()?.message)
    }

    @Test
    fun `login fails when the success body is malformed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{ not json"))

        val result = repository.login("user@example.com", "secret")

        assertTrue(result.isFailure)
        assertNull(store.savedResponse)
    }

    @Test
    fun `refresh fails without a stored session`() = runTest {
        val result = repository.refreshTokens()

        assertTrue(result.isFailure)
        assertEquals("Сессия отсутствует", result.exceptionOrNull()?.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `refresh sends the stored refresh token`() = runTest {
        store = FakeSessionStore(authSession(refreshToken = "refresh-456"))
        repository = AuthRepository(
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AuthApi::class.java),
            store
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture("auth_success.json")))

        val result = repository.refreshTokens()

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", request.path)
        assertTrue(request.body.readUtf8().contains("\"refresh_token\":\"refresh-456\""))
    }
}
