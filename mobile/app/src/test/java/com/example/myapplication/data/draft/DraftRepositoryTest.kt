package com.example.myapplication.data.draft

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.data.media.PhotoLocalStorage
import com.example.myapplication.model.DraftOwnerType
import com.example.myapplication.testutil.factories.selectedPhoto
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DraftRepositoryTest {

    private lateinit var database: DraftDatabase
    private val photoLocalStorage = mockk<PhotoLocalStorage>(relaxed = true)
    private lateinit var repository: DraftRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DraftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DraftRepository(database.draftDao(), photoLocalStorage)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `signed in user sees guest drafts and their own`() = runTest {
        val guest = repository.createDraft(DraftOwnerType.GUEST, null, listOf(selectedPhoto()))
        val mine = repository.createDraft(DraftOwnerType.USER, 7, listOf(selectedPhoto()))
        repository.createDraft(DraftOwnerType.USER, 8, listOf(selectedPhoto()))

        val visible = repository.observeVisibleDrafts(7).first().map { it.id }.toSet()

        assertEquals(setOf(guest, mine), visible)
    }

    @Test
    fun `guest only sees guest drafts`() = runTest {
        val guest = repository.createDraft(DraftOwnerType.GUEST, null, listOf(selectedPhoto()))
        repository.createDraft(DraftOwnerType.USER, 7, listOf(selectedPhoto()))

        val visible = repository.observeVisibleDrafts(null).first().map { it.id }

        assertEquals(listOf(guest), visible)
    }

    @Test
    fun `addPhotos appends after the existing ones`() = runTest {
        val draftId = repository.createDraft(
            DraftOwnerType.USER, 7,
            listOf(selectedPhoto(id = "a"), selectedPhoto(id = "b"))
        )

        repository.addPhotos(draftId, listOf(selectedPhoto(id = "c"), selectedPhoto(id = "d")))

        val photos = repository.getDraft(draftId)!!.selectedPhotos
        assertEquals(listOf("a", "b", "c", "d"), photos.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), photos.map { it.position })
    }

    @Test
    fun `removePhoto reindexes the remaining photos`() = runTest {
        val draftId = repository.createDraft(
            DraftOwnerType.USER, 7,
            listOf(selectedPhoto(id = "a"), selectedPhoto(id = "b"), selectedPhoto(id = "c"))
        )

        repository.removePhoto(draftId, "b")

        val photos = repository.getDraft(draftId)!!.selectedPhotos
        assertEquals(listOf("a", "c"), photos.map { it.id })
        assertEquals(listOf(0, 1), photos.map { it.position })
        verify { photoLocalStorage.deleteLocalCopy(any()) }
    }

    @Test
    fun `deleteDraft removes the draft and cascades to photos`() = runTest {
        val draftId = repository.createDraft(
            DraftOwnerType.USER, 7,
            listOf(selectedPhoto(id = "a"), selectedPhoto(id = "b"))
        )

        repository.deleteDraft(draftId)

        assertNull(repository.getDraft(draftId))
        assertEquals(0, database.draftDao().countPhotos(draftId))
        verify { photoLocalStorage.deleteLocalCopy(any()) }
    }
}
