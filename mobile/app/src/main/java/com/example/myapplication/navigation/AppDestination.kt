package com.example.myapplication.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Auth : AppDestination("auth")
    data object Profile : AppDestination("profile")
    data object ProfileSettings : AppDestination("profile/settings")
    data object Drafts : AppDestination("drafts")
    data object Preview : AppDestination("preview/{draftId}") {
        const val DRAFT_ID_ARG = "draftId"

        fun createRoute(draftId: String): String = "preview/$draftId"
    }

    data object Rendered : AppDestination("rendered/{draftId}") {
        const val DRAFT_ID_ARG = "draftId"

        fun createRoute(draftId: String): String = "rendered/$draftId"
    }
}
