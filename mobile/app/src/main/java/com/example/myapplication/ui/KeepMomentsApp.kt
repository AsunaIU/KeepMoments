package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import com.example.myapplication.navigation.AppNavGraph
import com.example.myapplication.ui.theme.KeepMomentsTheme
import com.example.myapplication.viewmodel.BookCreationViewModel

@Composable
fun KeepMomentsApp(viewModel: BookCreationViewModel) {
    KeepMomentsTheme {
        AppNavGraph(viewModel = viewModel)
    }
}
