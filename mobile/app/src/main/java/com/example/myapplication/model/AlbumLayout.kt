package com.example.myapplication.model

enum class SlotOrientation {
    Portrait,
    Landscape,
    Any
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class AlbumLayoutSlotSpec(
    val key: String,
    val rect: NormalizedRect,
    val orientation: SlotOrientation
)

data class AlbumLayoutSpec(
    val id: String,
    val title: String,
    val photoCount: Int,
    val slots: List<AlbumLayoutSlotSpec>
)

object AlbumLayouts {
    const val THREE_PORTRAIT = "three_portrait"
    const val TWO_LANDSCAPE = "two_landscape"
    const val SINGLE_PORTRAIT_FULL = "single_portrait_full"
    const val SINGLE_LANDSCAPE_FULL = "single_landscape_full"
    const val PORTRAIT_LEFT_TWO_LANDSCAPE_RIGHT = "portrait_left_two_landscape_right"

    val all: List<AlbumLayoutSpec> = listOf(
        AlbumLayoutSpec(
            id = THREE_PORTRAIT,
            title = "3 вертикальных",
            photoCount = 3,
            slots = listOf(
                AlbumLayoutSlotSpec("left", NormalizedRect(0f, 0f, 0.32f, 1f), SlotOrientation.Portrait),
                AlbumLayoutSlotSpec("center", NormalizedRect(0.34f, 0f, 0.66f, 1f), SlotOrientation.Portrait),
                AlbumLayoutSlotSpec("right", NormalizedRect(0.68f, 0f, 1f, 1f), SlotOrientation.Portrait)
            )
        ),
        AlbumLayoutSpec(
            id = TWO_LANDSCAPE,
            title = "2 горизонтальных",
            photoCount = 2,
            slots = listOf(
                AlbumLayoutSlotSpec("top", NormalizedRect(0f, 0f, 1f, 0.49f), SlotOrientation.Landscape),
                AlbumLayoutSlotSpec("bottom", NormalizedRect(0f, 0.51f, 1f, 1f), SlotOrientation.Landscape)
            )
        ),
        AlbumLayoutSpec(
            id = SINGLE_PORTRAIT_FULL,
            title = "1 вертикальное",
            photoCount = 1,
            slots = listOf(
                AlbumLayoutSlotSpec("main", NormalizedRect(0f, 0f, 1f, 1f), SlotOrientation.Portrait)
            )
        ),
        AlbumLayoutSpec(
            id = SINGLE_LANDSCAPE_FULL,
            title = "1 горизонтальное",
            photoCount = 1,
            slots = listOf(
                AlbumLayoutSlotSpec("main", NormalizedRect(0f, 0.18f, 1f, 0.82f), SlotOrientation.Landscape)
            )
        ),
        AlbumLayoutSpec(
            id = PORTRAIT_LEFT_TWO_LANDSCAPE_RIGHT,
            title = "1 + 2",
            photoCount = 3,
            slots = listOf(
                AlbumLayoutSlotSpec("left", NormalizedRect(0f, 0f, 0.45f, 1f), SlotOrientation.Portrait),
                AlbumLayoutSlotSpec("top_right", NormalizedRect(0.47f, 0f, 1f, 0.49f), SlotOrientation.Landscape),
                AlbumLayoutSlotSpec("bottom_right", NormalizedRect(0.47f, 0.51f, 1f, 1f), SlotOrientation.Landscape)
            )
        )
    )

    fun require(layoutId: String): AlbumLayoutSpec = all.firstOrNull { it.id == layoutId }
        ?: all.first { it.id == SINGLE_PORTRAIT_FULL }

    fun forPhotoCount(photoCount: Int): List<AlbumLayoutSpec> = all.filter { it.photoCount == photoCount }

    fun defaultSingleLayout(photo: SelectedPhoto?): String {
        return if ((photo?.width ?: 0) > (photo?.height ?: 0)) {
            SINGLE_LANDSCAPE_FULL
        } else {
            SINGLE_PORTRAIT_FULL
        }
    }
}

fun SelectedPhoto.orientation(): SlotOrientation {
    val width = width ?: return SlotOrientation.Any
    val height = height ?: return SlotOrientation.Any
    return if (width > height) SlotOrientation.Landscape else SlotOrientation.Portrait
}

fun SelectedPhoto.matches(orientation: SlotOrientation): Boolean {
    return orientation == SlotOrientation.Any || orientation() == SlotOrientation.Any || orientation() == orientation
}
