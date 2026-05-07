package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import com.example.myapplication.AppContainer
import com.example.myapplication.navigation.AppNavGraph
import com.example.myapplication.ui.theme.KeepMomentsTheme

@Composable
fun KeepMomentsApp(appContainer: AppContainer) {
    KeepMomentsTheme {
        AppNavGraph(appContainer = appContainer)
    }
}
