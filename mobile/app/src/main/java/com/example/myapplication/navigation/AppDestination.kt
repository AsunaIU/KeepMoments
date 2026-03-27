package com.example.myapplication.navigation

sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Login : AppDestination("login")
    data object Preview : AppDestination("preview")
}
