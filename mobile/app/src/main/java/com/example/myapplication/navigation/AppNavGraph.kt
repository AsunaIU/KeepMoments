package com.example.myapplication.navigation

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.PhotoPreviewScreen
import com.example.myapplication.viewmodel.BookCreationViewModel
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppNavGraph(
    viewModel: BookCreationViewModel,
    navController: NavHostController = rememberNavController()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    onContinueClick = viewModel::onContinueClicked
                )
            }
        }
    }
}
