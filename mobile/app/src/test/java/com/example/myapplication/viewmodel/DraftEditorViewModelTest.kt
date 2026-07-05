package com.example.myapplication.viewmodel

import android.net.Uri
import app.cash.turbine.test
import com.example.myapplication.data.album.AlbumRepository
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.books.BooksRepository
import com.example.myapplication.data.books.RenderedBookStore
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.model.AuthSession
import com.example.myapplication.model.BookDraft
import com.example.myapplication.testutil.MainDispatcherRule
import com.example.myapplication.testutil.factories.authSession
import com.example.myapplication.testutil.factories.bookDraft
import com.example.myapplication.testutil.factories.renderedBook
import com.example.myapplication.testutil.factories.selectedPhoto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val draftId = "draft-1"
    private val draftRepository = mockk<DraftRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val photoImportService = mockk<PhotoImportService>(relaxed = true)
    private val booksRepository = mockk<BooksRepository>(relaxed = true)
    private val renderedBookStore = mockk<RenderedBookStore>(relaxed = true)
    private val albumRepository = mockk<AlbumRepository>(relaxed = true)

    private val sessionFlow = MutableStateFlow<AuthSession?>(authSession())
    private val draftFlow = MutableStateFlow<BookDraft?>(null)

    private fun createViewModel(): DraftEditorViewModel {
        every { authRepository.session } returns sessionFlow
        every { draftRepository.observeVisibleDraft(eq(draftId), any()) } returns draftFlow
        return DraftEditorViewModel(
            draftId = draftId,
            draftRepository = draftRepository,
            authRepository = authRepository,
            photoImportService = photoImportService,
            booksRepository = booksRepository,
            renderedBookStore = renderedBookStore,
            albumRepository = albumRepository
        )
    }

    @Test
    fun `rejects photos beyond the limit`() = runTest(mainDispatcherRule.dispatcher) {
        draftFlow.value = bookDraft(photos = (0 until 50).map { selectedPhoto(uriString = "u$it", position = it) })
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onAddMorePhotos(listOf(Uri.parse("u50"), Uri.parse("u51")))
            assertEquals("Можно выбрать максимум 50 фото", expectMostRecentItem().errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { photoImportService.createSelectedPhotos(any()) }
    }

    @Test
    fun `skips photos already present in the draft`() = runTest(mainDispatcherRule.dispatcher) {
        draftFlow.value = bookDraft(photos = listOf(selectedPhoto(uriString = "u1")))
        coEvery { photoImportService.createSelectedPhotos(any()) } returns listOf(selectedPhoto(uriString = "u2"))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onAddMorePhotos(listOf(Uri.parse("u1"), Uri.parse("u2")))
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { photoImportService.createSelectedPhotos(match { it.size == 1 && it.first().toString() == "u2" }) }
        coVerify { draftRepository.addPhotos(draftId, any()) }
    }

    @Test
    fun `blocks continue without a valid photo`() = runTest(mainDispatcherRule.dispatcher) {
        draftFlow.value = bookDraft(photos = listOf(selectedPhoto(isValid = false)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onContinueClicked()
            assertEquals("Добавьте хотя бы одно валидное фото", expectMostRecentItem().errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { booksRepository.generateRenderedBook(any()) }
    }

    @Test
    fun `asks for auth when continuing without a session`() = runTest(mainDispatcherRule.dispatcher) {
        sessionFlow.value = null
        draftFlow.value = bookDraft(photos = listOf(selectedPhoto(isValid = true)))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onContinueClicked()
            val state = expectMostRecentItem()
            assertEquals("Для создания книги нужен вход в аккаунт", state.errorMessage)
            assertTrue(state.requiresAuthToContinue)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { booksRepository.generateRenderedBook(any()) }
    }

    @Test
    fun `continue builds the book and exposes its draft id`() = runTest(mainDispatcherRule.dispatcher) {
        draftFlow.value = bookDraft(photos = listOf(selectedPhoto(isValid = true)))
        coEvery { booksRepository.generateRenderedBook(any()) } returns Result.success(renderedBook(draftId))
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onContinueClicked()
            assertEquals(draftId, expectMostRecentItem().generatedBookDraftId)
            cancelAndIgnoreRemainingEvents()
        }
        verify { renderedBookStore.save(draftId, any()) }
        coVerify { albumRepository.createInitialAlbumFromRenderedBook(any()) }
    }

    @Test
    fun `ignores continue while a book is already generating`() = runTest(mainDispatcherRule.dispatcher) {
        draftFlow.value = bookDraft(photos = listOf(selectedPhoto(isValid = true)))
        val gate = CompletableDeferred<Unit>()
        coEvery { booksRepository.generateRenderedBook(any()) } coAnswers {
            gate.await()
            Result.success(renderedBook(draftId))
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onContinueClicked()
            viewModel.onContinueClicked()
            gate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { booksRepository.generateRenderedBook(any()) }
    }
}
