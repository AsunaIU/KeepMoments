package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.myapplication.data.books.FakeBooksRepository
import com.example.myapplication.data.media.AndroidMediaMetadataReader
import com.example.myapplication.ui.KeepMomentsApp
import com.example.myapplication.viewmodel.BookCreationViewModel

class MainActivity : ComponentActivity() {

    private val bookCreationViewModel: BookCreationViewModel by viewModels {
        BookCreationViewModel.Factory(
            booksRepository = FakeBooksRepository(),
            mediaMetadataReader = AndroidMediaMetadataReader(contentResolver)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeepMomentsApp(viewModel = bookCreationViewModel)
        }
    }
}