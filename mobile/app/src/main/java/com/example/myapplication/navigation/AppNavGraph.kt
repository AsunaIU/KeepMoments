package com.example.myapplication.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.AppContainer
import com.example.myapplication.ui.AuthScreen
import com.example.myapplication.ui.DraftsScreen
import com.example.myapplication.ui.HomeScreen
import com.example.myapplication.ui.PhotoPreviewScreen
import com.example.myapplication.ui.ProfileScreen
import com.example.myapplication.ui.ProfileSettingsScreen
import com.example.myapplication.ui.RenderedBookScreen
import com.example.myapplication.viewmodel.AuthViewModel
import com.example.myapplication.viewmodel.DraftEditorViewModel
import com.example.myapplication.viewmodel.DraftsViewModel
import com.example.myapplication.viewmodel.ProfileViewModel
import com.example.myapplication.viewmodel.RenderedBookViewModel
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    appContainer: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navigationTag = "Navigation"
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(appContainer.authRepository)
    )
    val draftsViewModel: DraftsViewModel = viewModel(
        factory = DraftsViewModel.Factory(
            draftRepository = appContainer.draftRepository,
            authRepository = appContainer.authRepository,
            photoImportService = appContainer.photoImportService
        )
    )
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(
            authRepository = appContainer.authRepository,
            profileStore = appContainer.profileStore
        )
    )

    val authUiState = authViewModel.uiState.collectAsStateWithLifecycle().value
    val draftsUiState = draftsViewModel.uiState.collectAsStateWithLifecycle().value
    val profileUiState = profileViewModel.uiState.collectAsStateWithLifecycle().value
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(authUiState.errorMessage) {
        authUiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.clearError()
        }
    }

    LaunchedEffect(draftsUiState.errorMessage) {
        draftsUiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            draftsViewModel.clearError()
        }
    }

    LaunchedEffect(profileUiState.errorMessage) {
        profileUiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            profileViewModel.clearError()
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
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = DraftsViewModel.PHOTO_LIMIT)
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        persistReadPermissions(context, uris)
                        scope.launch {
                            val draftId = draftsViewModel.createDraftFromUris(uris)
                            if (draftId != null) {
                                navController.navigate(AppDestination.Preview.createRoute(draftId))
                            }
                        }
                    }
                }

                HomeScreen(
                    isAuthenticated = authUiState.isAuthenticated,
                    userEmail = authUiState.session?.email,
                    greetingName = profileUiState.homeGreetingName,
                    isCreatingDraft = draftsUiState.isCreating,
                    onCreateBookClick = {
                        if (authUiState.isAuthenticated) {
                            launcher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            Log.d(navigationTag, "create book requires auth from home")
                            navController.navigate(AppDestination.Auth.route)
                        }
                    },
                    onProfileClick = { navController.navigate(AppDestination.Profile.route) },
                    onAuthClick = { navController.navigate(AppDestination.Auth.route) },
                    onLogoutClick = authViewModel::logout
                )
            }

            composable(AppDestination.Auth.route) {
                LaunchedEffect(authUiState.isAuthenticated) {
                    if (authUiState.isAuthenticated) {
                        navController.popBackStack()
                    }
                }

                AuthScreen(
                    onBackClick = { navController.popBackStack() },
                    uiState = authUiState,
                    onSubmit = authViewModel::submit
                )
            }

            composable(AppDestination.Drafts.route) {
                DraftsScreen(
                    uiState = draftsUiState,
                    onBackClick = { navController.popBackStack() },
                    onOpenDraftClick = { draftId ->
                        navController.navigate(AppDestination.Preview.createRoute(draftId))
                    },
                    onDeleteDraftClick = draftsViewModel::deleteDraft,
                    onCreateDraftClick = { navController.popBackStack() }
                )
            }

            composable(AppDestination.Profile.route) {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = DraftsViewModel.PHOTO_LIMIT)
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        persistReadPermissions(context, uris)
                        scope.launch {
                            val draftId = draftsViewModel.createDraftFromUris(uris)
                            if (draftId != null) {
                                navController.navigate(AppDestination.Preview.createRoute(draftId))
                            }
                        }
                    }
                }

                val latestDraft = draftsUiState.drafts.firstOrNull()
                ProfileScreen(
                    profileUiState = profileUiState,
                    latestDraft = latestDraft,
                    hasMoreDrafts = draftsUiState.drafts.size > 1,
                    onBackClick = {
                        navController.navigate(AppDestination.Home.route) {
                            popUpTo(AppDestination.Home.route) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onCreateNewClick = {
                        if (authUiState.isAuthenticated) {
                            launcher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            Log.d(navigationTag, "create book requires auth from profile")
                            navController.navigate(AppDestination.Auth.route)
                        }
                    },
                    onOpenDraftClick = { draftId ->
                        navController.navigate(AppDestination.Preview.createRoute(draftId))
                    },
                    onAllBooksClick = { navController.navigate(AppDestination.Drafts.route) },
                    onSettingsClick = { navController.navigate(AppDestination.ProfileSettings.route) }
                )
            }

            composable(AppDestination.ProfileSettings.route) {
                val pendingAvatarUriString = rememberSaveable(profileUiState.avatarUriString) {
                    mutableStateOf(profileUiState.avatarUriString)
                }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    uri?.let {
                        persistReadPermissions(context, listOf(it))
                        pendingAvatarUriString.value = it.toString()
                    }
                }

                ProfileSettingsScreen(
                    uiState = profileUiState,
                    avatarUriString = pendingAvatarUriString.value,
                    onBackClick = { navController.popBackStack() },
                    onPickAvatarClick = {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAvatarUriChange = { avatarUriString ->
                        pendingAvatarUriString.value = avatarUriString
                    },
                    onSaveClick = { displayName, avatarUriString ->
                        profileViewModel.saveProfile(
                            displayName = displayName,
                            avatarUriString = avatarUriString
                        ) {
                            navController.popBackStack()
                        }
                    },
                    onLogoutClick = if (authUiState.isAuthenticated) {
                        {
                            authViewModel.logout()
                            navController.popBackStack(AppDestination.Home.route, inclusive = false)
                        }
                    } else {
                        null
                    }
                )
            }

            composable(
                route = AppDestination.Preview.route,
                arguments = listOf(
                    navArgument(AppDestination.Preview.DRAFT_ID_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val draftId = backStackEntry.arguments?.getString(AppDestination.Preview.DRAFT_ID_ARG)
                    ?: return@composable
                val draftEditorViewModel: DraftEditorViewModel = viewModel(
                    key = "draft-$draftId",
                    factory = DraftEditorViewModel.Factory(
                        draftId = draftId,
                        draftRepository = appContainer.draftRepository,
                        authRepository = appContainer.authRepository,
                        photoImportService = appContainer.photoImportService,
                        booksRepository = appContainer.booksRepository,
                        renderedBookStore = appContainer.renderedBookStore
                    )
                )
                val uiState = draftEditorViewModel.uiState.collectAsStateWithLifecycle().value

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = DraftEditorViewModel.PHOTO_LIMIT)
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        persistReadPermissions(context, uris)
                        draftEditorViewModel.onAddMorePhotos(uris)
                    }
                }

                LaunchedEffect(uiState.errorMessage) {
                    uiState.errorMessage?.let {
                        snackbarHostState.showSnackbar(it)
                        draftEditorViewModel.clearError()
                    }
                }

                LaunchedEffect(uiState.requiresAuthToContinue) {
                    if (uiState.requiresAuthToContinue) {
                        Log.d(navigationTag, "preview continue requires auth draftId=$draftId")
                        draftEditorViewModel.consumeAuthRequirement()
                        navController.navigate(AppDestination.Auth.route)
                    }
                }

                LaunchedEffect(uiState.generatedBookDraftId) {
                    uiState.generatedBookDraftId?.let { generatedDraftId ->
                        Log.d(navigationTag, "navigate to rendered draftId=$generatedDraftId")
                        draftEditorViewModel.consumeGeneratedBookNavigation()
                        navController.navigate(AppDestination.Rendered.createRoute(generatedDraftId))
                    }
                }

                PhotoPreviewScreen(
                    uiState = uiState,
                    onBackClick = { navController.popBackStack() },
                    onAddMoreClick = {
                        launcher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveClick = draftEditorViewModel::onRemovePhoto,
                    onContinueClick = draftEditorViewModel::onContinueClicked,
                    onOpenDraftsClick = {
                        navController.navigate(AppDestination.Drafts.route)
                    }
                )
            }

            composable(
                route = AppDestination.Rendered.route,
                arguments = listOf(
                    navArgument(AppDestination.Rendered.DRAFT_ID_ARG) {
                        type = NavType.StringType
                    }
                )
            ) { backStackEntry ->
                val draftId = backStackEntry.arguments?.getString(AppDestination.Rendered.DRAFT_ID_ARG)
                    ?: return@composable
                val renderedBookViewModel: RenderedBookViewModel = viewModel(
                    key = "rendered-$draftId",
                    factory = RenderedBookViewModel.Factory(
                        draftId = draftId,
                        renderedBookStore = appContainer.renderedBookStore,
                        pdfExporter = appContainer.pdfExporter
                    )
                )
                val renderedBookUiState = renderedBookViewModel.uiState.collectAsStateWithLifecycle().value
                val createDocumentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    Log.d(navigationTag, "createDocument returned uri=$uri")
                    if (uri != null) {
                        renderedBookViewModel.exportToPdf(uri)
                    }
                }

                LaunchedEffect(renderedBookUiState.message) {
                    renderedBookUiState.message?.let {
                        snackbarHostState.showSnackbar(it)
                        renderedBookViewModel.clearMessage()
                    }
                }

                RenderedBookScreen(
                    book = renderedBookUiState.book,
                    isExporting = renderedBookUiState.isExporting,
                    onBackClick = { navController.popBackStack() },
                    onProfileClick = {
                        navController.navigate(AppDestination.Profile.route) {
                            popUpTo(AppDestination.Home.route) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onDownloadPdfClick = {
                        createDocumentLauncher.launch("keepmoments-$draftId.pdf")
                    }
                )
            }
        }
    }
}

private fun persistReadPermissions(context: Context, uris: List<Uri>) {
    uris.forEach { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}
