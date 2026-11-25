package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.remote.SupabaseSession
import com.example.data.repository.CatalogRepository
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.components.ConnectivitySyncBanner
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CollectionsScreen
import com.example.ui.screens.CustomCollectionDetailScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.TitleDetailScreen
import com.example.ui.theme.ArchivalSurface
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.IvoryInk
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SecondaryInk
import com.example.ui.theme.WarmBrass
import com.example.ui.viewmodel.MuvantaViewModel

object MuvantaRoutes {
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val PROFILE = "profile"
    const val STATISTICS = "statistics"
    const val SIGN_IN = "login"
    const val SIGN_UP = "signup"
    const val DETAIL = "detail/{itemId}"
    const val COLLECTION_DETAIL = "collection/{collectionId}"

    fun detail(itemId: String): String = "detail/$itemId"
    fun collectionDetail(collectionId: Long): String = "collection/$collectionId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val TOP_DESTINATIONS = listOf(
    TopLevelDestination(MuvantaRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(MuvantaRoutes.DISCOVER, "Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
    TopLevelDestination(MuvantaRoutes.LIBRARY, "Library", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    TopLevelDestination(MuvantaRoutes.COLLECTIONS, "Lists", Icons.Filled.CollectionsBookmark, Icons.Outlined.CollectionsBookmark),
    TopLevelDestination(MuvantaRoutes.PROFILE, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun MuvantaApp(
    viewModel: MuvantaViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val ioScope = rememberCoroutineScope()

    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val discoverState by viewModel.discoverUiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val libraryFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val librarySearch by viewModel.librarySearchQuery.collectAsStateWithLifecycle()
    val librarySort by viewModel.librarySort.collectAsStateWithLifecycle()
    val libraryListMode by viewModel.libraryListMode.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val profile by viewModel.profileState.collectAsStateWithLifecycle()
    val customCollections by viewModel.customCollections.collectAsStateWithLifecycle()
    val enrichedById by viewModel.enrichedById.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val canUndoBackup by viewModel.canUndoBackupAction.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val authBusy by viewModel.authBusy.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authNotice by viewModel.authNotice.collectAsStateWithLifecycle()
    val prefilledSignInEmail by viewModel.prefilledSignInEmail.collectAsStateWithLifecycle()
    val signOutPendingWarning by viewModel.signOutPendingWarning.collectAsStateWithLifecycle()
    val cachedPosterCount by viewModel.cachedPosterCount.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.authSuccessNavigateHome.collect {
            navController.navigate(MuvantaRoutes.HOME) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = false
                }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.authSuccessNavigateSignIn.collect {
            navController.navigate(MuvantaRoutes.SIGN_IN) {
                popUpTo(MuvantaRoutes.SIGN_UP) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Zero-permission Storage Access Framework (SAF) Backup & Export Launchers
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val payload = viewModel.generateJsonBackupPayload()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                viewModel.notifyMessage("JSON backup exported successfully.")
            }.onFailure {
                viewModel.notifyMessage("Could not write JSON backup file.")
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val payload = viewModel.generateCsvExportPayload()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                viewModel.notifyMessage("CSV watchlist exported successfully.")
            }.onFailure {
                viewModel.notifyMessage("Could not write CSV file.")
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            ioScope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            var total = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                total += read
                                if (total > CatalogRepository.MAX_IMPORT_BYTES) {
                                    throw IllegalArgumentException("Backup file exceeds the 5 MB safety limit.")
                                }
                                out.write(buffer, 0, read)
                            }
                            out.toString(Charsets.UTF_8.name())
                        }
                    }
                }
                readResult.onSuccess { content ->
                    if (content != null) {
                        viewModel.stageJsonImport(content)
                    } else {
                        viewModel.notifyMessage("Selected file could not be read.")
                    }
                }.onFailure { err ->
                    viewModel.notifyMessage(err.message ?: "Failed to open backup file.")
                }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MuvantaRoutes.HOME
    val showTopLevelChrome = currentRoute in TOP_DESTINATIONS.map { it.route }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun redirectToLogin(fromRoute: String) {
        navController.navigate(MuvantaRoutes.SIGN_IN) {
            popUpTo(fromRoute) { inclusive = true }
            launchSingleTop = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ElevatedSurface,
                        contentColor = IvoryInk,
                        actionColor = WarmBrass,
                    )
                }
            },
            bottomBar = {
                if (!useNavRail && showTopLevelChrome) {
                    NavigationBar(
                        containerColor = ArchivalSurface,
                        contentColor = IvoryInk,
                        tonalElevation = 0.dp,
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = WarmBrass,
                                    selectedTextColor = IvoryInk,
                                    unselectedIconColor = SecondaryInk,
                                    unselectedTextColor = SecondaryInk,
                                    indicatorColor = ElevatedSurface,
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail && showTopLevelChrome) {
                    NavigationRail(
                        containerColor = ArchivalSurface,
                        contentColor = IvoryInk,
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding(),
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = WarmBrass,
                                    selectedTextColor = IvoryInk,
                                    unselectedIconColor = SecondaryInk,
                                    unselectedTextColor = SecondaryInk,
                                    indicatorColor = ElevatedSurface,
                                ),
                                modifier = Modifier.testTag("rail_${dest.route}"),
                            )
                        }
                    }
                    VerticalDivider(color = HairlineBorder)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    ConnectivitySyncBanner(
                        syncState = syncState,
                        onSyncNow = { viewModel.syncNow(manual = true) },
                        onOpenSyncCenter = {
                            if (!syncState.isSignedIn || syncState.authExpired) {
                                navController.navigate(MuvantaRoutes.SIGN_IN) { launchSingleTop = true }
                            } else {
                                navigateTopLevel(MuvantaRoutes.PROFILE)
                            }
                        },
                    )

                    NavHost(
                        navController = navController,
                        startDestination = MuvantaRoutes.HOME,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    ) {
                    composable(MuvantaRoutes.HOME) {
                        HomeScreen(
                            uiState = homeState,
                            profileName = profile.displayName,
                            isSignedIn = syncState.isSignedIn,
                            onSearchQueryChange = viewModel::updateHomeSearch,
                            onSearchSubmitToDiscover = { query ->
                                viewModel.openDiscoverWithSearch(query)
                                navigateTopLevel(MuvantaRoutes.DISCOVER)
                            },
                            onOpenCategory = { category ->
                                viewModel.openDiscoverWithCategory(category)
                                navigateTopLevel(MuvantaRoutes.DISCOVER)
                            },
                            onOpenMood = { mood ->
                                viewModel.openDiscoverWithMood(mood)
                                navigateTopLevel(MuvantaRoutes.DISCOVER)
                            },
                            onOpenTitle = { id -> navController.navigate(MuvantaRoutes.detail(id)) },
                            onToggleWatched = viewModel::toggleQuickWatched,
                            onRequestMetadata = { item -> viewModel.requestMetadata(item) },
                            onOpenStatistics = { navController.navigate(MuvantaRoutes.STATISTICS) },
                            onOpenProfile = { navigateTopLevel(MuvantaRoutes.PROFILE) },
                            onOpenSignIn = { navController.navigate(MuvantaRoutes.SIGN_IN) },
                            onPickUnwatched = {
                                viewModel.pickRandomUnwatchedId()?.let { id ->
                                    navController.navigate(MuvantaRoutes.detail(id))
                                }
                            },
                        )
                    }

                    composable(MuvantaRoutes.DISCOVER) {
                        DiscoverScreen(
                            uiState = discoverState,
                            searchHistory = searchHistory,
                            onSearchChange = viewModel::updateDiscoverSearch,
                            onSearchSubmit = viewModel::recordSearchQuery,
                            onClearSearchHistory = viewModel::clearSearchHistory,
                            onCategoryChange = viewModel::updateDiscoverCategory,
                            onToggleMood = viewModel::toggleDiscoverMood,
                            onClearMoods = viewModel::clearDiscoverMoods,
                            onGenreChange = viewModel::updateDiscoverGenre,
                            onLanguageOrCountryChange = viewModel::updateDiscoverLanguageOrCountry,
                            onMinRatingChange = viewModel::updateDiscoverMinRating,
                            onStatusFilterChange = viewModel::updateDiscoverStatusFilter,
                            onTierChange = viewModel::updateDiscoverTier,
                            onDecadeChange = viewModel::updateDiscoverDecade,
                            onSortChange = viewModel::updateDiscoverSort,
                            onModeChange = viewModel::updateDiscoverMode,
                            onPageChange = viewModel::setDiscoverPage,
                            onResetFilters = viewModel::resetDiscoverFilters,
                            onPickUnwatched = {
                                viewModel.pickRandomUnwatchedId()?.let { id ->
                                    navController.navigate(MuvantaRoutes.detail(id))
                                }
                            },
                            onOpenTitle = { id -> navController.navigate(MuvantaRoutes.detail(id)) },
                            onToggleWatched = viewModel::toggleQuickWatched,
                            onRequestMetadata = { item -> viewModel.requestMetadata(item) },
                        )
                    }

                    composable(MuvantaRoutes.LIBRARY) {
                        ProtectedRoute(
                            isSignedIn = syncState.isSignedIn,
                            checkSession = viewModel::getActiveSession,
                            onUnauthenticated = { redirectToLogin(MuvantaRoutes.LIBRARY) },
                        ) {
                            LibraryScreen(
                                items = libraryItems,
                                activeFilter = libraryFilter,
                                searchQuery = librarySearch,
                                sortOption = librarySort,
                                isListMode = libraryListMode,
                                stats = stats,
                                onFilterChange = viewModel::updateLibraryFilter,
                                onSearchChange = viewModel::updateLibrarySearch,
                                onSortChange = viewModel::updateLibrarySort,
                                onToggleViewMode = viewModel::toggleLibraryListMode,
                                onOpenTitle = { id -> navController.navigate(MuvantaRoutes.detail(id)) },
                                onToggleWatched = viewModel::toggleQuickWatched,
                                onRequestMetadata = { item -> viewModel.requestMetadata(item) },
                                onOpenStatistics = { navController.navigate(MuvantaRoutes.STATISTICS) },
                                onExploreCatalog = { navigateTopLevel(MuvantaRoutes.DISCOVER) },
                            )
                        }
                    }

                    composable(MuvantaRoutes.COLLECTIONS) {
                        ProtectedRoute(
                            isSignedIn = syncState.isSignedIn,
                            checkSession = viewModel::getActiveSession,
                            onUnauthenticated = { redirectToLogin(MuvantaRoutes.COLLECTIONS) },
                        ) {
                            CollectionsScreen(
                                stats = stats,
                                customCollections = customCollections,
                                onOpenTier = { tierId ->
                                    viewModel.openDiscoverWithTier(tierId)
                                    navigateTopLevel(MuvantaRoutes.DISCOVER)
                                },
                                onOpenCustomCollection = { colId ->
                                    navController.navigate(MuvantaRoutes.collectionDetail(colId))
                                },
                                onCreateCollection = viewModel::createCollection,
                                onDuplicateCollection = viewModel::duplicateCollection,
                                onRenameCollection = viewModel::renameCollection,
                                onDeleteCollection = viewModel::deleteCollection,
                            )
                        }
                    }

                    composable(MuvantaRoutes.PROFILE) {
                        ProtectedRoute(
                            isSignedIn = syncState.isSignedIn,
                            checkSession = viewModel::getActiveSession,
                            onUnauthenticated = { redirectToLogin(MuvantaRoutes.PROFILE) },
                        ) {
                            ProfileScreen(
                                profile = profile,
                                stats = stats,
                                syncState = syncState,
                                authBusy = authBusy,
                                authError = authError,
                                authNotice = authNotice,
                                signOutPendingWarning = signOutPendingWarning,
                                cachedPosterCount = cachedPosterCount,
                                importPreview = importPreview,
                                canUndoBackup = canUndoBackup,
                                onSaveProfile = viewModel::updateProfile,
                                onOpenStatistics = { navController.navigate(MuvantaRoutes.STATISTICS) },
                                onSignIn = viewModel::signInWithSupabase,
                                onSignUp = viewModel::signUpWithSupabase,
                                onClearAuthFeedback = viewModel::clearAuthFeedback,
                                onOpenSignInPage = { navController.navigate(MuvantaRoutes.SIGN_IN) },
                                onOpenSignUpPage = { navController.navigate(MuvantaRoutes.SIGN_UP) },
                                onSyncNow = { viewModel.syncNow(manual = true) },
                                onResolveConflict = viewModel::resolveSyncConflict,
                                onRequestSignOut = viewModel::requestSignOut,
                                onDismissSignOutWarning = viewModel::dismissSignOutWarning,
                                onSyncThenSignOut = viewModel::syncThenSignOut,
                                onConfirmSignOutDiscard = viewModel::confirmSignOut,
                                onClearArtworkCache = viewModel::clearArtworkCache,
                                onExportJsonClick = { exportJsonLauncher.launch("muvanta-watchlist-backup.json") },
                                onExportCsvClick = { exportCsvLauncher.launch("muvanta-watchlist.csv") },
                                onImportJsonClick = { importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                                onConfirmImportMerge = { viewModel.confirmJsonImport(replaceAll = false) },
                                onConfirmImportReplace = { viewModel.confirmJsonImport(replaceAll = true) },
                                onCancelImport = viewModel::cancelJsonImport,
                                onUndoBackupAction = viewModel::undoLastBackupOrClear,
                                onClearAllProgress = viewModel::clearAllLocalProgress,
                            )
                        }
                    }

                    composable(MuvantaRoutes.SIGN_IN) {
                        AuthScreen(
                            isSignUp = false,
                            syncState = syncState,
                            authBusy = authBusy,
                            authError = authError,
                            authNotice = authNotice,
                            prefilledEmail = prefilledSignInEmail,
                            onSubmitSignIn = viewModel::signInWithSupabase,
                            onSubmitSignUp = viewModel::signUpWithSupabase,
                            onSwitchMode = {
                                viewModel.clearAuthFeedback()
                                navController.navigate(MuvantaRoutes.SIGN_UP) {
                                    popUpTo(MuvantaRoutes.SIGN_IN) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onClearFeedback = viewModel::clearAuthFeedback,
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navigateTopLevel(MuvantaRoutes.HOME)
                                }
                            },
                        )
                    }

                    composable(MuvantaRoutes.SIGN_UP) {
                        AuthScreen(
                            isSignUp = true,
                            syncState = syncState,
                            authBusy = authBusy,
                            authError = authError,
                            authNotice = authNotice,
                            onSubmitSignIn = viewModel::signInWithSupabase,
                            onSubmitSignUp = viewModel::signUpWithSupabase,
                            onSwitchMode = {
                                viewModel.clearAuthFeedback()
                                navController.navigate(MuvantaRoutes.SIGN_IN) {
                                    popUpTo(MuvantaRoutes.SIGN_UP) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onClearFeedback = viewModel::clearAuthFeedback,
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navigateTopLevel(MuvantaRoutes.HOME)
                                }
                            },
                        )
                    }

                    composable(MuvantaRoutes.STATISTICS) {
                        ProtectedRoute(
                            isSignedIn = syncState.isSignedIn,
                            checkSession = viewModel::getActiveSession,
                            onUnauthenticated = { redirectToLogin(MuvantaRoutes.STATISTICS) },
                        ) {
                            StatisticsScreen(
                                stats = stats,
                                onBack = { navController.popBackStack() },
                                onOpenTier = { tierId ->
                                    viewModel.openDiscoverWithTier(tierId)
                                    navigateTopLevel(MuvantaRoutes.DISCOVER)
                                },
                            )
                        }
                    }

                    composable(
                        route = MuvantaRoutes.COLLECTION_DETAIL,
                        arguments = listOf(navArgument("collectionId") { type = NavType.LongType }),
                    ) { entry ->
                        ProtectedRoute(
                            isSignedIn = syncState.isSignedIn,
                            checkSession = viewModel::getActiveSession,
                            onUnauthenticated = { redirectToLogin(MuvantaRoutes.COLLECTION_DETAIL) },
                        ) {
                            val collectionId = entry.arguments?.getLong("collectionId") ?: -1L
                            val collection = customCollections.firstOrNull { it.id == collectionId }
                            val items = remember(collection, enrichedById) {
                                collection?.itemIds?.mapNotNull { enrichedById[it] }.orEmpty()
                            }
                            CustomCollectionDetailScreen(
                                collection = collection,
                                items = items,
                                onBack = { navController.popBackStack() },
                                onOpenTitle = { id -> navController.navigate(MuvantaRoutes.detail(id)) },
                                onToggleWatched = viewModel::toggleQuickWatched,
                                onRequestMetadata = { item -> viewModel.requestMetadata(item) },
                                onExploreCatalog = { navigateTopLevel(MuvantaRoutes.DISCOVER) },
                            )
                        }
                    }

                    composable(
                        route = MuvantaRoutes.DETAIL,
                        arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                    ) { entry ->
                        val itemId = entry.arguments?.getString("itemId").orEmpty()
                        val enriched = enrichedById[itemId]
                        val similar = remember(itemId, enrichedById) {
                            viewModel.getSimilarTitles(itemId)
                        }
                        val history by remember(itemId) {
                            viewModel.observeViewingHistory(itemId)
                        }.collectAsStateWithLifecycle(initialValue = emptyList())
                        val episodes by remember(itemId) {
                            viewModel.observeSeriesEpisodes(itemId)
                        }.collectAsStateWithLifecycle(initialValue = emptyList())

                        TitleDetailScreen(
                            enriched = enriched,
                            similarTitles = similar,
                            customCollections = customCollections,
                            viewingHistory = history,
                            seriesEpisodes = episodes,
                            onBack = { navController.popBackStack() },
                            onOpenTitle = { id -> navController.navigate(MuvantaRoutes.detail(id)) },
                            onToggleWatched = viewModel::toggleQuickWatched,
                            onSetStatus = { status -> viewModel.setWatchStatus(itemId, status) },
                            onToggleFavorite = { viewModel.toggleFavorite(itemId) },
                            onLogRewatch = { viewModel.logRewatch(itemId) },
                            onDeleteViewingLog = viewModel::deleteViewingLog,
                            onSetRating = { rating -> viewModel.setRating(itemId, rating) },
                            onSetReview = { review -> viewModel.setReview(itemId, review) },
                            onSetNotes = { notes -> viewModel.setNotes(itemId, notes) },
                            onSetTags = { tags -> viewModel.setTags(itemId, tags) },
                            onSetCustomPoster = { url -> viewModel.setCustomPoster(itemId, url) },
                            onEnsureMetadata = { item -> viewModel.requestMetadata(item, forceRefresh = false) },
                            onForceRefreshMetadata = { item -> viewModel.requestMetadata(item, forceRefresh = true) },
                            onEnsureEpisodes = { item, force -> viewModel.ensureSeriesEpisodes(item, force) },
                            onToggleEpisode = { s, e, w -> viewModel.toggleEpisodeWatched(itemId, s, e, w) },
                            onSetSeasonWatched = { s, eps, w -> viewModel.setSeasonWatched(itemId, s, eps, w) },
                            onToggleInCollection = viewModel::toggleItemInCollection,
                            onCreateCollection = viewModel::createCollection,
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectedRoute(
    isSignedIn: Boolean,
    checkSession: suspend () -> SupabaseSession?,
    onUnauthenticated: () -> Unit,
    content: @Composable () -> Unit,
) {
    var verified by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isSignedIn) {
        val activeSession = checkSession()
        if (activeSession == null) {
            verified = false
            onUnauthenticated()
        } else {
            verified = true
        }
    }

    when (verified) {
        true -> content()
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBg),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = WarmBrass)
            }
        }
    }
}
