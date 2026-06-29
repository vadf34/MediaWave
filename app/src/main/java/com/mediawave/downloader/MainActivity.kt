package com.mediawave.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.mediawave.downloader.download.DownloadManager
import com.mediawave.downloader.download.DownloadService
import com.mediawave.downloader.ui.screens.cookies.CookiesScreen
import com.mediawave.downloader.ui.screens.history.HistoryScreen
import com.mediawave.downloader.ui.screens.home.HomeScreen
import com.mediawave.downloader.ui.screens.home.HomeViewModel
import com.mediawave.downloader.ui.screens.profile.ProfileParserScreen
import com.mediawave.downloader.ui.screens.profile.ProfileParserViewModel
import com.mediawave.downloader.ui.screens.settings.SettingsScreen
import com.mediawave.downloader.ui.theme.*
import android.content.Context
import com.mediawave.downloader.R
import com.mediawave.downloader.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Cookies : Screen("cookies")
    object Profile : Screen("profile")
}

data class NavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

// navItems is built lazily in MediaWaveBottomBar using string resources
// to respect the current locale.

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun attachBaseContext(newBase: Context) {
        // Safe: reads from plain SharedPreferences before Hilt is ready
        val langCode = LocaleHelper.getSavedLang(newBase)
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, langCode))
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestAllPermissions()
        downloadManager.init()

        setContent {
            val viewModel: HomeViewModel = hiltViewModel()
            val themeMode by viewModel.settings.themeFlow.collectAsState(initial = 0)

            val isDark = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            MediaWaveTheme(darkTheme = isDark) {
                MediaWaveApp(viewModel = viewModel)
            }
        }


    }

    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    manageStorageLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Запускаємо завантаження у фоні без відкриття додатку
                DownloadService.startWithUrl(this, url)
                // Повертаємо додаток у фон якщо він був запущений з share sheet
                moveTaskToBack(true)
            }
        }
    }
}

@Composable
private fun isSystemInDarkTheme(): Boolean {
    return androidx.compose.foundation.isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaWaveApp(viewModel: HomeViewModel) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val bottomBarRoutes = listOf(Screen.Home.route, Screen.History.route, Screen.Profile.route)
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                MediaWaveBottomBar(
                    currentRoute = currentRoute,
                    navController = navController,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
            enterTransition = {
                fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it / 8 }
            },
            exitTransition = {
                fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it / 8 }
            },
            popEnterTransition = {
                fadeIn(tween(200)) + slideInHorizontally(tween(300)) { -it / 8 }
            },
            popExitTransition = {
                fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 8 }
            },
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(viewModel = viewModel)
            }
            composable(Screen.Profile.route) {
                val profileViewModel: ProfileParserViewModel = hiltViewModel()
                ProfileParserScreen(
                    viewModel = profileViewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onCookiesClick = { navController.navigate(Screen.Cookies.route) },
                )
            }
            composable(Screen.Cookies.route) {
                CookiesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
fun MediaWaveBottomBar(
    currentRoute: String?,
    navController: androidx.navigation.NavHostController,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val localNavItems = listOf(
        NavItem(Screen.Home.route, ctx.getString(R.string.home), Icons.Filled.Home, Icons.Outlined.Home),
        NavItem(Screen.History.route, ctx.getString(R.string.history), Icons.Filled.History, Icons.Outlined.History),
        NavItem(Screen.Profile.route, ctx.getString(R.string.profile_parser_nav), Icons.Filled.ManageSearch, Icons.Outlined.ManageSearch),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        localNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Accent,
                    selectedTextColor = Accent,
                    indicatorColor = Accent.copy(alpha = 0.1f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
