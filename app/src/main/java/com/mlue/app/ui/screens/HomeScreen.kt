package com.mlue.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.luminance
import com.mlue.app.R
import com.mlue.app.ui.components.HabitCard
import com.mlue.app.viewmodel.HabitViewModel
import com.mlue.app.viewmodel.SortOption
import java.time.LocalDate
import java.time.LocalTime

import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    navController: NavController,
    viewModel: HabitViewModel,
    onScrollStateChange: (isScrollingDown: Boolean) -> Unit = {}
) {
    val habits by viewModel.habits.collectAsState()
    val tokenCount by viewModel.tokenCount.collectAsState()
    val stepState by viewModel.stepState.collectAsState()
    val focusMode by viewModel.focusModeEnabled.collectAsState()
    val haptics by viewModel.hapticsEnabled.collectAsState()
    val sounds by viewModel.soundsEnabled.collectAsState()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cachedTheme = viewModel.getCachedTheme()
    val darkMode by viewModel.darkModeEnabled.collectAsState(initial = cachedTheme ?: systemDark)
    val goals by viewModel.goals.collectAsState()
    val goalProgress by viewModel.goalProgress.collectAsState()
    val habitMomentums by viewModel.habitMomentums.collectAsState()
    val adaptiveFocusedHabits by viewModel.adaptiveFocusedHabits.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val hintFirstCompletionShown by viewModel.hintFirstCompletionShown.collectAsState()
    val hintFirstStreakShown by viewModel.hintFirstStreakShown.collectAsState()
    val hintFocusShown by viewModel.hintFocusShown.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val today = LocalDate.now()
    var sortExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var quickNote by rememberSaveable { mutableStateOf("") }

    val interactionFeedback = com.mlue.app.ui.components.rememberHabitInteractionFeedback(
        soundsEnabled = sounds,
        hapticsEnabled = haptics
    )

    // adaptiveFocusedHabits handles both the filter-to-today AND the intelligent sort:
    // incomplete habits first, then by streak. When focus mode is OFF, it mirrors
    // the regular habits list (user's chosen sort order preserved).
    // In focus mode the ViewModel-level flow is used directly; non-focus uses the
    // user's sort-aware habits flow via the legacy displayHabits derivation.
    val displayHabits by remember(habits, focusMode, adaptiveFocusedHabits) {
        derivedStateOf {
            if (focusMode) adaptiveFocusedHabits else habits
        }
    }

    // Hydration guard — becomes true after first non-empty DB emission
    // Avoids the empty-then-pop-in effect on cold launch
    var habitsHaveLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(habits) {
        if (habits.isNotEmpty()) habitsHaveLoaded = true
    }
    // Also mark loaded if DB returned and list is genuinely empty (no habits added yet)
    // We detect this by waiting one frame past initial composition with a small delay
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(600)
        habitsHaveLoaded = true
    }

    val scheduledTodayCount by remember(displayHabits, today) {
        derivedStateOf { displayHabits.count { !it.paused && viewModel.isScheduledToday(it) } }
    }
    val completedTodayCount by remember(displayHabits, today) {
        derivedStateOf { displayHabits.count { it.lastCompletedDate == today && viewModel.isScheduledToday(it) } }
    }

    val carouselGoals by remember(goals, goalProgress) {
        derivedStateOf {
            val active = goals.filter { goal ->
                val progress = goalProgress[goal.goalId]?.overallPercent ?: 0
                progress < 100
            }
            val completed = goals.filter { goal ->
                val progress = goalProgress[goal.goalId]?.overallPercent ?: 0
                progress == 100
            }
            active + completed
        }
    }

    // FAB scroll awareness: threshold=2 items to avoid flickering on tiny scroll
    val isScrollingDown by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 2 }
    }
    LaunchedEffect(isScrollingDown) {
        onScrollStateChange(isScrollingDown)
    }

    var milestoneEvent by remember { mutableStateOf<com.mlue.app.viewmodel.MilestoneEvent?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.milestoneEvents.collect { event ->
            if (haptics) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            milestoneEvent = event
            kotlinx.coroutines.delay(2200)
            if (milestoneEvent == event) {
                milestoneEvent = null
            }
        }
    }

    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    }
    // Hoisted ABOVE LazyColumn — pagerState inside a lazy item is unstable across recompositions
    val goalPagerState = rememberPagerState(pageCount = { carouselGoals.size })

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            if (darkMode) 
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f)
                            else 
                                androidx.compose.ui.graphics.Color(0xFFA08070).copy(alpha = 0.06f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                )
            ) {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        // WS2: Restored seamless identity. Transparent TopAppBar lets the soft 
                        // vertical gradient show through, creating a faint edge-to-edge gloss 
                        // rather than a boxed UI element.
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = "Mlue",
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = dateLabel,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            FilterChip(
                                selected = focusMode,
                                onClick = { viewModel.setFocusMode(!focusMode) },
                                label = {
                                    Text(
                                        "Focus",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                            // Momentum dot — replaces bare star icon
                            // Reads as: quiet energy indicator, not gamified currency
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = tokenCount.toString(),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
                // Subtle editorial divider — separates header from content
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        AnimatedContent(
            targetState = habitsHaveLoaded,
            transitionSpec = {
                fadeIn(animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween(com.mlue.app.ui.theme.AppMotion.durationMedium)) togetherWith
                fadeOut(animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween(com.mlue.app.ui.theme.AppMotion.durationShort))
            },
            label = "hydration"
        ) { loaded ->
            if (!loaded) {
                // Skeleton placeholder — prevents empty-then-pop-in on cold launch
                ShimmerHabitList(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                )
            } else {
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "progress") {
                val isLightMode = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                // Progress card — explicit static container, NOT surfaceVariant (prevents OEM tonal bleed)
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = androidx.compose.material.ripple.rememberRipple(),
                        onClick = { }
                    ),
                    shape = androidx.compose.material3.MaterialTheme.shapes.extraLarge,
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (isLightMode)
                            com.mlue.app.ui.theme.LightSurfaceElevated  // Layer 2: elevated interactive
                        else
                            com.mlue.app.ui.theme.DarkSurface
                    ),
                    elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
                    // 0.75dp: primary interactive surface — highest topology weight in light mode
                    border = if (isLightMode) androidx.compose.foundation.BorderStroke(0.75.dp, com.mlue.app.ui.theme.LightTopologyBorder) else null
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your Progress",
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (habits.isEmpty()) "Start small." else if (scheduledTodayCount == 0) "No habits today" else if (completedTodayCount == scheduledTodayCount) "Perfect Day!" else "Keep Going",
                                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (habits.isEmpty()) "One consistent habit is enough to begin." else "$completedTodayCount of $scheduledTodayCount completed",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Box(contentAlignment = Alignment.Center) {
                            val progress = if (scheduledTodayCount == 0) 0f
                            else completedTodayCount.toFloat() / scheduledTodayCount.toFloat()
                            // AppMotion spec — no inline spring object creation on each recomposition
                            val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = progress,
                                animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween(com.mlue.app.ui.theme.AppMotion.durationMedium),
                                label = "progress"
                            )
                            
                            // Ambient Glow behind progress
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = if (darkMode) 0.15f else 0.0f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            
                            if (habits.isEmpty()) {
                                // Soft idle state for onboarding
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier.size(80.dp),
                                    color = if (isLightMode)
                                        com.mlue.app.ui.theme.LightOutlineVariant.copy(alpha = 0.5f)
                                    else
                                        com.mlue.app.ui.theme.DarkSurfaceVariant.copy(alpha = 0.5f),
                                    strokeWidth = 8.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            } else {
                                // Track ring: explicit static color, NOT surfaceVariant
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier.size(80.dp),
                                    color = if (isLightMode)
                                        com.mlue.app.ui.theme.LightOutlineVariant
                                    else
                                        com.mlue.app.ui.theme.DarkSurfaceVariant,
                                    strokeWidth = 8.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(80.dp),
                                    color = if (!darkMode) androidx.compose.material3.MaterialTheme.colorScheme.secondary else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                    strokeWidth = 8.dp,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (hintFirstCompletionShown == false && completedTodayCount > 0) {
                item(key = "hintCompletion") {
                    com.mlue.app.ui.components.HintChip(
                        text = "You're building consistency.",
                        onDismiss = { viewModel.dismissHintFirstCompletion() }
                    )
                }
            } else if (hintFirstStreakShown == false && displayHabits.any { it.currentStreak > 1 }) {
                item(key = "hintStreak") {
                    com.mlue.app.ui.components.HintChip(
                        text = "Small repetitions become patterns over time.",
                        onDismiss = { viewModel.dismissHintFirstStreak() }
                    )
                }
            } else if (hintFocusShown == false && focusMode) {
                item(key = "hintFocus") {
                    com.mlue.app.ui.components.HintChip(
                        text = "Focus Mode prioritizes your most important habits.",
                        onDismiss = { viewModel.dismissHintFocus() }
                    )
                }
            }

            if (carouselGoals.isNotEmpty()) {
                item(key = "activeGoalPager") {
                    // goalPagerState is hoisted ABOVE the LazyColumn — stable across recompositions
                    HorizontalPager(
                        state = goalPagerState,
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        pageSpacing = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        val goal = carouselGoals[page]
                        val progressDetails = goalProgress[goal.goalId] ?: com.mlue.app.data.GoalProgressDetails(0, emptyList())
                        
                        // Parallax offset from hoisted goalPagerState
                        val pageOffset = (goalPagerState.currentPage - page) + goalPagerState.currentPageOffsetFraction
                        val scale = 1f - (kotlin.math.abs(pageOffset) * 0.1f).coerceIn(0f, 0.1f)
                        val alpha = 1f - (kotlin.math.abs(pageOffset) * 0.4f).coerceIn(0f, 0.4f)

                        val isGoalLightMode = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                        // Dark: deep burnt amber — cinematic focus object (Sprint 3C)
                        // Light: editorial warm maroon — unchanged
                        val goalCardBg = if (isGoalLightMode)
                            com.mlue.app.ui.theme.LightHeroCardBackground
                        else
                            com.mlue.app.ui.theme.DarkGoalCardBg

                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .clickable {
                                    // Safe nav: popUpTo start destination so insights?goalId=X
                                    // never stacks on top of today — prevents Home tab freeze.
                                    navController.navigate("insights?goalId=${goal.goalId}") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                },
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = goalCardBg
                            ),
                            shape = androidx.compose.material3.MaterialTheme.shapes.large,
                            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = if (isGoalLightMode) 0.dp else 0.dp),
                            border = if (isGoalLightMode) null else null
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                val isGoalLightMode2 = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                                // Dark: near-white for clean contrast on deep amber; light: warm off-white
                                val onGoalCard = if (isGoalLightMode2)
                                    com.mlue.app.ui.theme.LightHeroCardOnSurface
                                else
                                    com.mlue.app.ui.theme.DarkGoalCardText
                                // Dark: muted warm gold; light: warm muted subtext
                                val onGoalCardSub = if (isGoalLightMode2)
                                    com.mlue.app.ui.theme.LightHeroCardSubtext
                                else
                                    com.mlue.app.ui.theme.DarkGoalCardSubtext

                                Text(
                                    text = if (progressDetails.overallPercent == 100) "🏆 Completed Goal" else "🎯 Active Goal",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = onGoalCardSub
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = goal.title,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                    color = onGoalCard,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Deadline: ${goal.deadline ?: "None"}",
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = onGoalCardSub
                                    )
                                    Text(
                                        text = "${progressDetails.overallPercent}%",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                        color = onGoalCard
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // AppMotion spec — no inline spring on every recomposition
                                val animatedGoalProgress by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = progressDetails.overallPercent / 100f,
                                    animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween(com.mlue.app.ui.theme.AppMotion.durationMedium),
                                    label = "goalProgress"
                                )
                                // Light: warm cream fill on dark maroon — maximum contrast
                                // Dark: amber fill on deep amber trough — cinematic, on-brand
                                val progressTrackColor = if (isGoalLightMode2)
                                    com.mlue.app.ui.theme.LightHeroCardTrack
                                else
                                    com.mlue.app.ui.theme.DarkGoalCardTrack
                                val progressFillColor = if (isGoalLightMode2)
                                    com.mlue.app.ui.theme.LightHeroCardOnSurface
                                else
                                    com.mlue.app.ui.theme.PrimaryAmber  // amber fill — same family as FAB

                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { animatedGoalProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = progressFillColor,
                                    trackColor = progressTrackColor,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "activeGoalEmpty") {
                    val isLightModeEmpty = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                    Surface(
                        // Explicit static tokens — no Material-derived surfaceVariant fallback
                        color = if (isLightModeEmpty)
                            com.mlue.app.ui.theme.LightSurfaceVariant
                        else
                            com.mlue.app.ui.theme.DarkSurface,
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                        border = if (isLightModeEmpty) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Goals become meaningful once habits repeat.",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    // Same safe nav pattern — prevents raw push onto back stack
                                    navController.navigate("insights") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = if (habits.isEmpty()) {
                                    androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    androidx.compose.material3.ButtonDefaults.buttonColors()
                                },
                                elevation = if (habits.isEmpty()) null else androidx.compose.material3.ButtonDefaults.buttonElevation()
                            ) {
                                Text("Create Goal")
                            }
                        }
                    }
                }
            }

            item(key = "habitsHeader") {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's habits",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        )
                        if (focusMode) {
                            Text(
                                text = "Focus mode active",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!focusMode) {
                        // Box gives DropdownMenu a stable, fixed anchor point.
                        // Without this, DropdownMenu anchors relative to the parent Row,
                        // which can shift during LazyColumn scroll/recomposition.
                        Box {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("By name") },
                                    onClick = {
                                        viewModel.updateSort(SortOption.NAME)
                                        sortExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("By streak") },
                                    onClick = {
                                        viewModel.updateSort(SortOption.STREAK)
                                        sortExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("By last completed") },
                                    onClick = {
                                        viewModel.updateSort(SortOption.LAST_COMPLETED)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "onboarding") {
                val isEmptyLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                androidx.compose.animation.AnimatedVisibility(
                    visible = displayHabits.isEmpty(),
                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                ) {
                    androidx.compose.material3.Surface(
                        color = if (isEmptyLight) com.mlue.app.ui.theme.LightSurface else com.mlue.app.ui.theme.DarkSurface,
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                        border = if (isEmptyLight) androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant) else null,
                        shadowElevation = if (isEmptyLight) 1.dp else 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "✨ Build consistency quietly",
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Start with one habit you can realistically repeat.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = { navController.navigate("add") },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Create First Habit")
                            }
                            
                            @OptIn(ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                val suggestions = listOf("Sleep before 12", "Drink water", "Read 10 pages", "Morning walk")
                                suggestions.forEach { suggestion ->
                                    androidx.compose.material3.FilterChip(
                                        selected = false,
                                        onClick = { 
                                            val uriEncoded = android.net.Uri.encode(suggestion)
                                            navController.navigate("add?prefillTitle=$uriEncoded") 
                                        },
                                        label = { Text(suggestion) },
                                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        border = null,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(displayHabits, key = { it.id }, contentType = { "habit" }) { habit ->
                val goalTitle = remember(habit.goalId, goals) {
                    goals.firstOrNull { it.goalId == habit.goalId }?.title
                }
                HabitCard(
                    habit = habit,
                    today = today,
                    isScheduledToday = viewModel.isScheduledToday(habit),
                    canFreeze = tokenCount > 0 && habit.currentStreak > 0,
                    stepSupported = stepState.supported,
                    stepsToday = stepState.stepsToday,
                    goalTitle = goalTitle,
                    momentum = habitMomentums[habit.id],
                    isFocusMode = focusMode,
                    onEdit = {
                        navController.navigate("add?habitId=${habit.id}") {
                            launchSingleTop = true
                        }
                    },
                    onToggleCompletion = {
                        val wasCompleted = habit.lastCompletedDate == today
                        // Sound and haptics are handled exclusively by the new reliable centralized feedback system
                        // which guarantees native OEM execution, debounce protection, and 0ms main thread blocking.
                        if (!wasCompleted) {
                            interactionFeedback.triggerCompletionFeedback()
                        }
                        viewModel.toggleHabitCompletion(habit)
                    },
                    onDelete = {
                        viewModel.deleteHabit(habit)
                    },
                    onFreeze = {
                        viewModel.tryBuyStreakFreeze(habit) { frozen ->
                            if (!frozen) {
                                scope.launch { snackbarHostState.showSnackbar("Could not freeze streak") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("Streak frozen for 1 missed day!") }
                            }
                        }
                    },
                    onTogglePause = { viewModel.togglePaused(habit) },
                    onEnableReminder = {
                        val initial = habit.reminderTime ?: LocalTime.of(9, 0)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.updateReminder(habit, true, LocalTime.of(hour, minute))
                            },
                            initial.hour,
                            initial.minute,
                            false
                        ).show()
                    },
                    onDisableReminder = { viewModel.updateReminder(habit, false, null) }
                )
            }

            if (!focusMode) {
                item(key = "reflection") {
                    val line = remember(today) {
                        reflectionLines[today.dayOfYear % reflectionLines.size]
                    }
                    Text(
                        text = line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                item(key = "quickJournal") {
                    val isLightQuick = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                    Surface(
                        // Layer 2: elevated interactive surface — visibly above standard LightSurface cards
                        color = if (isLightQuick)
                            com.mlue.app.ui.theme.LightSurfaceElevated
                        else
                            com.mlue.app.ui.theme.DarkSurface,
                        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
                        // 0.75dp: primary interactive surface border weight
                        border = if (isLightQuick)
                            androidx.compose.foundation.BorderStroke(0.75.dp, com.mlue.app.ui.theme.LightTopologyBorder)
                        else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Quick journal entry",
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = quickNote,
                                onValueChange = { quickNote = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    // Day-seeded rotating prompt — same day feels stable,
                                    // different day feels alive. Never random — deterministic.
                                    val prompt = remember(today) {
                                        journalPrompts[today.dayOfYear % journalPrompts.size]
                                    }
                                    Text(prompt)
                                },
                                minLines = 3
                            )
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val text = quickNote.trim()
                                        if (text.isNotEmpty()) {
                                            viewModel.upsertJournalEntry(
                                                title = "Today",
                                                body = text,
                                                date = today,
                                                mood = null
                                            )
                                            quickNote = ""
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            }

            }
            // End of LazyColumn

            AnimatedVisibility(
                visible = milestoneEvent != null,
                enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(com.mlue.app.ui.theme.AppMotion.durationMedium)) + fadeIn(animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween()),
                exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = androidx.compose.animation.core.tween(com.mlue.app.ui.theme.AppMotion.durationMedium)) + fadeOut(animationSpec = com.mlue.app.ui.theme.AppMotion.floatTween()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 24.dp, end = 24.dp)
            ) {
                milestoneEvent?.let { event ->
                    val isLightMode = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
                    val copy = when (event.streak) {
                        3 -> "3 day streak — a strong beginning."
                        7 -> "7 day streak — momentum is building."
                        14 -> "14 days — consistency is becoming a pattern."
                        30 -> "30 days — this rhythm is real now."
                        else -> "${event.streak} days — incredible momentum."
                    }
                    Surface(
                        color = if (isLightMode) com.mlue.app.ui.theme.LightHeroCardBackground else com.mlue.app.ui.theme.DarkSurfaceVariant,
                        shape = androidx.compose.material3.MaterialTheme.shapes.large,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = copy,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = if (isLightMode) com.mlue.app.ui.theme.LightHeroCardOnSurface else com.mlue.app.ui.theme.DarkOnBackground,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            }
        }
        }
    }

}

@Composable
private fun ShimmerHabitList(modifier: Modifier = Modifier) {
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shimmerColor = if (isLight)
        com.mlue.app.ui.theme.LightSurfaceVariant
    else
        com.mlue.app.ui.theme.DarkSurface

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Skeleton progress card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .background(shimmerColor, androidx.compose.material3.MaterialTheme.shapes.extraLarge)
        )
        // Skeleton habit rows
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(shimmerColor, androidx.compose.material3.MaterialTheme.shapes.medium)
                    .then(
                        if (isLight) Modifier.then(
                            Modifier.background(
                                shimmerColor.copy(alpha = 0.7f - it * 0.1f),
                                androidx.compose.material3.MaterialTheme.shapes.medium
                            )
                        ) else Modifier
                    )
            )
        }
    }
}

private val reflectionLines = listOf(
    "small steps compound.",
    "progress is built daily.",
    "quiet consistency matters.",
    "showing up still counts.",
    "momentum starts quietly."
)

/**
 * Rotating daily journal prompts — day-seeded so the same prompt shows all day
 * but shifts the next. Short, open-ended, reflective. Never therapy language.
 * Sprint 3B: Journal Intelligence (Task 7)
 */
private val journalPrompts = listOf(
    "What felt easier today?",
    "What helped you stay on track?",
    "What would you do differently tomorrow?",
    "What are you quietly proud of today?",
    "What interrupted your rhythm today?",
    "What small thing made today better?",
    "What did you notice about your energy?",
    "What could you let go of tonight?",
    "What showed up for you today?",
    "What would a lighter tomorrow look like?"
)
