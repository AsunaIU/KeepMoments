package com.example.myapplication.navigation

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.data.pdf.AndroidPdfExporter
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.PhotoPreviewScreen
import com.example.myapplication.ui.RenderedBookScreen
import com.example.myapplication.ui.StoryPromptScreen
import com.example.myapplication.viewmodel.BookCreationViewModel
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppNavGraph(
    viewModel: BookCreationViewModel,
    navController: NavHostController = rememberNavController()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pdfExporter = remember(context) { AndroidPdfExporter(context.contentResolver) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route
        ) {
            composable(AppDestination.Home.route) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = BookCreationViewModel.PHOTO_LIMIT)
                ) { uris ->
                    viewModel.onPhotosPicked(uris)
                    if (uris.isNotEmpty()) {
                        navController.navigate(AppDestination.Preview.route)
                    }
                }

                HomeScreen(
                    onCreateBookClick = {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onLoginClick = { navController.navigate(AppDestination.Login.route) }
                )
            }

            composable(AppDestination.Login.route) {
                LoginScreen(
                    onBackClick = { navController.popBackStack() },
                    onLoginStubClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Экран входа пока в разработке")
                        }
                    }
                )
            }

            composable(AppDestination.Preview.route) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = BookCreationViewModel.PHOTO_LIMIT)
                ) { uris ->
                    viewModel.onAddMorePhotos(uris)
                }

                PhotoPreviewScreen(
                    uiState = uiState,
                    onBackClick = { navController.popBackStack() },
                    onAddMoreClick = {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveClick = viewModel::onRemovePhoto,
                    onContinueClick = {
                        if (uiState.canContinue) {
                            navController.navigate(AppDestination.Prompt.route)
                        }
                    }
                )
            }

            composable(AppDestination.Prompt.route) {
                LaunchedEffect(uiState.generatedBook?.filledTemplate?.id) {
                    if (uiState.generatedBook != null) {
                        navController.navigate(AppDestination.Book.route) {
                            popUpTo(AppDestination.Prompt.route) {
                                inclusive = true
                            }
                        }
                    }
                }

                StoryPromptScreen(
                    uiState = uiState,
                    onBackClick = {
                        viewModel.clearGenerationError()
                        navController.popBackStack()
                    },
                    onStoryPromptChanged = viewModel::onStoryPromptChanged,
                    onGenerateClick = viewModel::generateBook,
                    onRetryClick = viewModel::retryGeneration
                )
            }

            composable(AppDestination.Book.route) {
                val generatedBook = uiState.generatedBook
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    if (uri == null || generatedBook == null) return@rememberLauncherForActivityResult

                    scope.launch {
                        val result = pdfExporter.export(generatedBook, uri)
                        if (result.isSuccess) {
                            Toast.makeText(context, "PDF сохранён", Toast.LENGTH_SHORT).show()
                        } else {
                            snackbarHostState.showSnackbar("Не удалось сохранить PDF")
                        }
                    }
                }

                if (generatedBook == null) {
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                } else {
                    RenderedBookScreen(
                        book = generatedBook,
                        onBackClick = { navController.popBackStack() },
                        onDownloadPdfClick = {
                            createDocumentLauncher.launch("keepmoments-${generatedBook.filledTemplate.id}.pdf")
                        }
                    )
                }
            }
        }
    }
}
