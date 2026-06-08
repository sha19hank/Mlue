package com.mlue.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.mlue.app.ui.screens.AddHabitScreen
import com.mlue.app.ui.screens.CalendarScreen
import com.mlue.app.ui.screens.HomeScreen
import com.mlue.app.ui.screens.JournalScreen
import com.mlue.app.ui.screens.SettingsScreen
import com.mlue.app.ui.screens.StatsScreen
import com.mlue.app.ui.theme.MlueTheme
import com.mlue.app.viewmodel.HabitViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.navigation.NavDestination.Companion.hierarchy
import com.mlue.app.ui.theme.AppMotion
import androidx.compose.ui.text.style.TextOverflow

class MainActivity : ComponentActivity() {
    private val viewModel: HabitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val cachedTheme = viewModel.getCachedTheme()
            val darkMode by viewModel.darkModeEnabled.collectAsState(initial = cachedTheme ?: systemDark)
            
            MlueTheme(darkTheme = darkMode) {
                AppNavHost(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: HabitViewModel) {
    val navController: NavHostController = rememberNavController()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cachedTheme = viewModel.getCachedTheme()
    val darkMode by viewModel.darkModeEnabled.collectAsState(initial = cachedTheme ?: systemDark)

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rawRoute = navBackStackEntry?.destination?.route ?: ""
    val currentRoute = rawRoute.substringBefore("?")

    val bottomBarRoutes = remember { setOf("today", "calendar", "journal", "insights", "settings") }
    val showBottomBar = currentRoute in bottomBarRoutes
    val journalDialogRequest = rememberSaveable { mutableStateOf(false) }
    val goalDialogRequest = rememberSaveable { mutableStateOf(false) }

    // Tracks whether the journal editor overlay is open — used to hide the global FAB
    // so the editor's own Save/Close actions are the only action surface.
    var journalEditorOpen by remember { mutableStateOf(false) }
    // FAB hides when journal editor is open — editor's own Save/Close are the action surface
    val showFab = currentRoute == "today" ||
        (currentRoute == "journal" && !journalEditorOpen) ||
        currentRoute == "insights"
    val fabInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFabPressed by fabInteractionSource.collectIsPressedAsState()
    // FAB scroll-awareness: hoisted state written by HomeScreen callback
    var isFabScrolledDown by remember { mutableStateOf(false) }
    val fabAlpha by animateFloatAsState(
        // Fades to 0.38f (not invisible) — quiet, not aggressive
        targetValue = if (isFabScrolledDown && currentRoute == "today") 0.38f else 1f,
        animationSpec = AppMotion.floatTween(AppMotion.durationMedium),
        label = "fabAlpha"
    )

    // Root overlay Box — dock and FAB float ABOVE content, Scaffold reserves NO space for them
    Box(modifier = Modifier.fillMaxSize()) {

        // Background gradient fills the whole root
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (darkMode) {
                            listOf(
                                androidx.compose.material3.MaterialTheme.colorScheme.background,
                                // Explicit static token — no surfaceVariant alpha derivation
                                com.mlue.app.ui.theme.DarkSurfaceVariant,
                                androidx.compose.material3.MaterialTheme.colorScheme.background
                            )
                        } else {
                            listOf(
                                androidx.compose.ui.graphics.Color(0xFFF7F3ED),
                                androidx.compose.ui.graphics.Color(0xFFF5F1EA),
                                androidx.compose.ui.graphics.Color(0xFFF3EEE7)
                            )
                        }
                    )
                )
        )

        // Scaffold owns ONLY screen content — no bottomBar, no FAB, no layout reservation
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "today",
                // Only top padding from Scaffold (status bar insets) — no bottom padding
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                enterTransition = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.durationLong)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.97f,
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.durationLong)
                    )
                },
                exitTransition = {
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.exitDuration)
                    )
                },
                popEnterTransition = {
                    androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.durationMedium)
                    )
                },
                popExitTransition = {
                    androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.exitDuration)
                    ) + androidx.compose.animation.scaleOut(
                        targetScale = 0.97f,
                        animationSpec = androidx.compose.animation.core.tween(AppMotion.exitDuration)
                    )
                }
            ) {
                composable("today") {
                    HomeScreen(
                        navController = navController,
                        viewModel = viewModel,
                        onScrollStateChange = { scrollingDown -> isFabScrolledDown = scrollingDown }
                    )
                }
                composable("calendar") { CalendarScreen(viewModel = viewModel) }
                composable("journal") {
                    JournalScreen(
                        viewModel = viewModel,
                        openDialogRequest = journalDialogRequest.value,
                        onDialogRequestConsumed = { journalDialogRequest.value = false },
                        onEditorStateChange = { open -> journalEditorOpen = open }
                    )
                }
                composable(
                    route = "insights?goalId={goalId}",
                    arguments = listOf(navArgument("goalId") { type = NavType.LongType; defaultValue = -1L })
                ) { backStackEntry ->
                    val goalIdArg = backStackEntry.arguments?.getLong("goalId", -1L) ?: -1L
                    val goalId = if (goalIdArg != -1L) goalIdArg else null
                    StatsScreen(
                        navController = navController,
                        viewModel = viewModel,
                        highlightGoalId = goalId,
                        onHighlightConsumed = {
                            backStackEntry.arguments?.remove("goalId")
                        },
                        openDialogRequest = goalDialogRequest.value,
                        onDialogRequestConsumed = { goalDialogRequest.value = false }
                    )
                }
                composable("settings") { SettingsScreen(navController = navController, viewModel = viewModel) }
                composable("how_works") { com.mlue.app.ui.screens.HowMlueWorksScreen(navController = navController, darkMode = darkMode) }
                composable("privacy_policy") { com.mlue.app.ui.screens.LegalDocumentScreen(navController = navController, title = "Privacy Policy", assetFileName = "PRIVACY_POLICY.md") }
                composable("terms_of_use") { com.mlue.app.ui.screens.LegalDocumentScreen(navController = navController, title = "Terms of Use", assetFileName = "TERMS_OF_USE.md") }
                composable("oss_licenses") { com.mlue.app.ui.screens.OpenSourceLicensesScreen(navController = navController) }
                composable("version_info") { com.mlue.app.ui.screens.VersionInfoScreen(navController = navController) }
                composable(
                    route = "add?habitId={habitId}&goalId={goalId}&prefillTitle={prefillTitle}",
                    arguments = listOf(
                        navArgument("habitId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("goalId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("prefillTitle") { type = NavType.StringType; nullable = true }
                    )
                ) { backStackEntry ->
                    val habitId = backStackEntry.arguments?.getLong("habitId").takeIf { it != -1L }
                    val goalId = backStackEntry.arguments?.getLong("goalId").takeIf { it != -1L }
                    val prefillTitle = backStackEntry.arguments?.getString("prefillTitle")
                    AddHabitScreen(navController = navController, viewModel = viewModel, habitId = habitId, prefilledGoalId = goalId, prefillTitle = prefillTitle)
                }
            }
        }

        // FAB — overlaid above content, offset so it clears the dock comfortably
        if (showFab) {
            val isJournal = currentRoute == "journal"
            val isInsights = currentRoute == "insights"
            FloatingActionButton(
                onClick = {
                    when {
                        isJournal -> journalDialogRequest.value = true
                        isInsights -> goalDialogRequest.value = true
                        else -> navController.navigate("add")
                    }
                },
                interactionSource = fabInteractionSource,
                containerColor = if (darkMode)
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                else if (isFabPressed)
                    androidx.compose.ui.graphics.Color(0xFF7A3E2B)
                else
                    androidx.compose.ui.graphics.Color(0xFFA46B3C),
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = if (darkMode) 6.dp else 4.dp,
                    pressedElevation = if (darkMode) 12.dp else 6.dp
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // FAB sits 108dp from bottom: dock(68) + dock-bottom-pad(20) + gap(20)
                    .offset(x = (-24).dp, y = (-108).dp)
                    // Subtle fade when user scrolls into content — quiet, not aggressive
                    .graphicsLayer { alpha = fabAlpha }
                    .then(
                        if (!darkMode) Modifier.shadow(
                            elevation = if (isFabPressed) 6.dp else 4.dp,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            spotColor = androidx.compose.ui.graphics.Color(0xFF2A140A).copy(alpha = 0.4f),
                            ambientColor = androidx.compose.ui.graphics.Color(0xFF2A140A).copy(alpha = 0.2f)
                        ) else Modifier
                    )
            ) {
                Icon(
                    imageVector = if (isJournal) Icons.Filled.EditNote else Icons.Filled.Add,
                    contentDescription = if (isJournal) "New journal entry" else if (isInsights) "Create goal" else "Add habit"
                )
            }
        }

        // Floating dock — overlaid at bottom, no Scaffold layout reservation
        if (showBottomBar) {
            BottomNavBar(
                navController = navController,
                darkMode = darkMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Onboarding overlay removed in Sprint 3H -> Ambient Onboarding
    }
}

@Composable
private fun BottomNavBar(
    navController: NavHostController,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            BottomNavItem("today", "Today", Icons.Filled.CheckCircle),
            BottomNavItem("calendar", "Calendar", Icons.Filled.DateRange),
            BottomNavItem("journal", "Journal", Icons.Filled.EditNote),
            BottomNavItem("insights", "Insights", Icons.AutoMirrored.Filled.ShowChart),
            BottomNavItem("settings", "Settings", Icons.Filled.Settings)
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val dockShape = RoundedCornerShape(36.dp)
    // Dark: slightly less opaque (0.88 vs 0.92) — lets background depth breathe through
    // Light: warm cream surface, same as before
    val dockColor = if (darkMode)
        androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    else
        androidx.compose.ui.graphics.Color(0xFFFFFDF9)

    // Hairline top-edge tint: single-pixel highlight at the top of the dock
    // In dark mode: very soft white line reads as "glass edge" — felt, not seen
    // In light mode: warm hairline echoes the topology border language
    val edgeHighlightColor = if (darkMode)
        androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)
    else
        androidx.compose.ui.graphics.Color(0xFFA08070).copy(alpha = 0.10f)

    // Single floating dock — no outer wrapper Surface
    // shadow() must be applied BEFORE clip() so it renders outside the clipped boundary
    NavigationBar(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp, top = 0.dp)
            .fillMaxWidth()
            .height(72.dp)
            .then(
                if (!darkMode) Modifier.shadow(
                    elevation = 10.dp,
                    shape = dockShape,
                    spotColor = androidx.compose.ui.graphics.Color(0xFF2A140A).copy(alpha = 0.10f),
                    ambientColor = androidx.compose.ui.graphics.Color(0xFF2A140A).copy(alpha = 0.05f)
                ) else Modifier.shadow(
                    elevation = 8.dp,
                    shape = dockShape,
                    spotColor = androidx.compose.ui.graphics.Color(0xFF000000).copy(alpha = 0.30f),
                    ambientColor = androidx.compose.ui.graphics.Color(0xFF000000).copy(alpha = 0.15f)
                )
            )
            .clip(dockShape)
            // Glass edge: single-pixel top highlight drawn after clip so it renders inside the shape
            .drawWithContent {
                drawContent()
                drawLine(
                    color = edgeHighlightColor,
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = 1.5f
                )
            },
        containerColor = dockColor,
        tonalElevation = 0.dp,
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) {
        items.forEach { item ->
            val selected = currentDestination?.hierarchy?.any {
                it.route?.substringBefore("?") == item.route
            } == true
            NavigationBarItem(
                modifier = if (item == items.first()) Modifier.padding(start = 4.dp)
                           else if (item == items.last()) Modifier.padding(end = 4.dp)
                           else Modifier,
                selected = selected,
                onClick = {
                    // Guard: do nothing if already on this tab — prevents re-entrant
                    // popUpTo + restoreState cycle that can deadlock the back stack
                    if (selected) return@NavigationBarItem

                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    // Fixed 24.dp box prevents icon compression on narrow screens
                    androidx.compose.foundation.layout.Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(24.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            item.icon,
                            contentDescription = item.label
                        )
                    }
                },
                alwaysShowLabel = false,
                label = {
                    Text(
                        item.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Clip
                    )
                },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = if (darkMode)
                        androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        androidx.compose.ui.graphics.Color(0xFF7A3E2B),
                    unselectedIconColor = if (darkMode)
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        androidx.compose.ui.graphics.Color(0xFF9A8F88),
                    selectedTextColor = if (darkMode)
                        androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        androidx.compose.ui.graphics.Color(0xFF7A3E2B),
                    unselectedTextColor = if (darkMode)
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        androidx.compose.ui.graphics.Color(0xFF9A8F88),
                    // WS6: Slightly reduced capsule opacity — edge items clip less when indicator
                    // color is slightly inset from the full-width pill Material3 renders by default.
                    indicatorColor = if (darkMode)
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    else
                        androidx.compose.ui.graphics.Color(0xFFEDE8E0)
                )
            )
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
