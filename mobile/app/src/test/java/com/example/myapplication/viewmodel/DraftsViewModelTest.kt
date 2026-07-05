package com.example.myapplication.viewmodel

import android.net.Uri
import app.cash.turbine.test
import com.example.myapplication.data.auth.AuthRepository
import com.example.myapplication.data.draft.DraftRepository
import com.example.myapplication.data.media.PhotoImportService
import com.example.myapplication.model.AuthSession
import com.example.myapplication.model.BookDraftSummary
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.testutil.MainDispatcherRule
import com.example.myapplication.testutil.factories.authSession
import com.example.myapplication.testutil.factories.selectedPhoto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val draftRepository = mockk<DraftRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val photoImportService = mockk<PhotoImportService>(relaxed = true)

    private val sessionFlow = MutableStateFlow<AuthSession?>(null)
    private val draftsFlow = MutableStateFlow<List<BookDraftSummary>>(emptyList())

    private fun createViewModel(): DraftsViewModel {
        every { authRepository.session } returns sessionFlow
        every { draftRepository.observeVisibleDrafts(any()) } returns draftsFlow
        return DraftsViewModel(draftRepository, authRepository, photoImportService)
    }

    @Test
    fun `returns null for empty uris`() = runTest(mainDispatcherRule.dispatcher) {
        val result = createViewModel().createDraftFromUris(emptyList())

        assertNull(result)
        coVerify(exactly = 0) { photoImportService.createSelectedPhotos(any()) }
    }

    @Test
    fun `creates a guest draft when signed out`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { authRepository.currentSession() } returns null
        coEvery { photoImportService.createSelectedPhotos(any()) } returns listOf(selectedPhoto())
        coEvery { draftRepository.createDraft(any(), any(), any(), any(), any()) } returns "draft-1"

        val result = createViewModel().createDraftFromUris(listOf(Uri.parse("u1")))

        assertEquals("draft-1", result)
        coVerify {
            draftRepository.createDraft(
                ownerType = DraftOwnerType.GUEST,
                ownerUserId = null,
                photos = any(),
                storyPrompt = any(),
                generateCaptions = any()
            )
        }
    }

    @Test
    fun `creates a user draft when signed in`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { authRepository.currentSession() } returns authSession(userId = 7)
        coEvery { photoImportService.createSelectedPhotos(any()) } returns listOf(selectedPhoto())
        coEvery { draftRepository.createDraft(any(), any(), any(), any(), any()) } returns "draft-2"

        val result = createViewModel().createDraftFromUris(listOf(Uri.parse("u1")))

        assertEquals("draft-2", result)
        coVerify {
            draftRepository.createDraft(
                ownerType = DraftOwnerType.USER,
                ownerUserId = 7L,
                photos = any(),
                storyPrompt = any(),
                generateCaptions = any()
            )
        }
    }

    @Test
    fun `returns null when nothing could be imported`() = runTest(mainDispatcherRule.dispatcher) {
        coEvery { authRepository.currentSession() } returns null
        coEvery { photoImportService.createSelectedPhotos(any()) } returns emptyList()

        val result = createViewModel().createDraftFromUris(listOf(Uri.parse("u1")))

        assertNull(result)
        coVerify(exactly = 0) { draftRepository.createDraft(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `deletes imported copies when draft creation fails`() = runTest(mainDispatcherRule.dispatcher) {
        val photos = listOf(selectedPhoto())
        coEvery { authRepository.currentSession() } returns null
        coEvery { photoImportService.createSelectedPhotos(any()) } returns photos
        coEvery { draftRepository.createDraft(any(), any(), any(), any(), any()) } throws RuntimeException("db down")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val result = viewModel.createDraftFromUris(listOf(Uri.parse("u1")))
            assertNull(result)
            assertEquals("db down", expectMostRecentItem().errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
        verify { photoImportService.deleteLocalCopies(photos) }
    }

    @Test
    fun `deleteDraft delegates to the repository`() = runTest(mainDispatcherRule.dispatcher) {
        createViewModel().deleteDraft("d1")

        coVerify { draftRepository.deleteDraft("d1") }
    }

    @Test
    fun `renameDraft delegates to the repository`() = runTest(mainDispatcherRule.dispatcher) {
        createViewModel().renameDraft("d1", "Trip")

        coVerify { draftRepository.updateDraftTitle("d1", "Trip") }
    }
}
