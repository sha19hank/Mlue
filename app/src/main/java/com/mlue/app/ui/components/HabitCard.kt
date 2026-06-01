package com.mlue.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import com.mlue.app.ui.theme.AppMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mlue.app.data.HabitEntity
import com.mlue.app.viewmodel.HabitMomentum
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitCard(
    habit: HabitEntity,
    today: LocalDate,
    isScheduledToday: Boolean,
    canFreeze: Boolean,
    stepSupported: Boolean,
    stepsToday: Int,
    modifier: Modifier = Modifier,
    goalTitle: String? = null,
    momentum: HabitMomentum? = null,
    isFocusMode: Boolean = false,
    onEdit: () -> Unit,
    onToggleCompletion: () -> Unit,
    onDelete: () -> Unit,
    onFreeze: () -> Unit,
    onTogglePause: () -> Unit,
    onEnableReminder: () -> Unit,
    onDisableReminder: () -> Unit
) {
    val isCompletedToday = habit.lastCompletedDate == today
    val isPaused = habit.paused
    // Focus mode disables expansion — reinforces calm simplification
    var expanded by remember { mutableStateOf(false) }
    val canExpand = !isFocusMode
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val baseTint = if (habit.color != 0) Color(habit.color) else MaterialTheme.colorScheme.surface
    
    val lightThemeTint = when (habit.color) {
        0xFF3B82F6.toInt() -> Color(0xFFD6DEE8)
        0xFF10B981.toInt() -> Color(0xFFDDE5DD)
        0xFFF59E0B.toInt() -> Color(0xFFE8DDD3)
        0xFFEF4444.toInt() -> Color(0xFFD9C7C3)
        0xFF8B5CF6.toInt() -> Color(0xFFDED8E8)
        0xFF14B8A6.toInt() -> Color(0xFFD7E5E2)
        else -> MaterialTheme.colorScheme.surface
    }

    val containerColor by animateColorAsState(
        targetValue = when {
            isLightMode && habit.color != 0 -> lightThemeTint
            isLightMode -> MaterialTheme.colorScheme.surface
            isCompletedToday -> baseTint.copy(alpha = 0.12f)
            habit.color != 0 -> baseTint.copy(alpha = 0.08f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = AppMotion.colorTween(),
        label = "cardColor"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> if (isLightMode) 2.dp else 4.dp
            isCompletedToday -> if (isLightMode) 3.dp else 6.dp
            else -> if (isLightMode) 1.dp else 2.dp
        },
        animationSpec = AppMotion.dpTween(),
        label = "cardElevation"
    )
    // 0.985f: restrained editorial press — tactile but not rubbery
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = AppMotion.pressBounce(),
        label = "cardScale"
    )
    // Checkbox completion pop — tiny, fast, felt more than seen
    val checkboxScale by animateFloatAsState(
        targetValue = if (isCompletedToday) 1.05f else 1f,
        animationSpec = AppMotion.checkboxPop(),
        label = "checkboxScale"
    )
    // Completed cards: secondary metadata quietly dims — feels intentional, not broken
    val metadataAlpha by animateFloatAsState(
        targetValue = if (isCompletedToday) 0.5f else 1f,
        animationSpec = AppMotion.floatTween(AppMotion.durationMedium),
        label = "metadataAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isLightMode) Modifier.shadow(
                    elevation = elevation,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                    spotColor = Color(0xFF2A140A).copy(alpha = 0.10f),
                    ambientColor = Color(0xFF2A140A).copy(alpha = 0.08f)
                ) else Modifier
            )
            .animateContentSize(animationSpec = AppMotion.contentSpring())
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(),
                onClick = { if (canExpand) expanded = !expanded }
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = if (isLightMode) CardDefaults.cardElevation(defaultElevation = 0.dp) else CardDefaults.cardElevation(defaultElevation = elevation),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box {
            if (habit.color != 0) {
                // Fixed height accent bar — fillMaxHeight causes layout thrash on expand
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(44.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            Color(habit.color),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                        )
                )
            }
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Focus mode: hide streak label when zero (reduces noise)
                        val showStreak = !isFocusMode || habit.currentStreak > 0
                        if (showStreak) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Streak: ${habit.currentStreak}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer { alpha = metadataAlpha }
                            )
                        }
                        // Focus mode: goal chip hidden — reduces informational density
                        if (goalTitle != null && !isFocusMode) {
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.SuggestionChip(
                                onClick = { },
                                label = {
                                    Text(
                                        "🎯 $goalTitle",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.graphicsLayer { alpha = metadataAlpha },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                ),
                                colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // ── Momentum dot ───────────────────────────────────────────
                    // 5×5dp dot — quiet enough to be noticed over time, not immediately obvious.
                    // STEADY = no dot (default state, no visual noise).
                    // No pulsing ring — Mlue's visual language depends on quiet discovery.
                    val momentumColor: Color? = when (momentum) {
                        HabitMomentum.STRONG    -> MaterialTheme.colorScheme.primary
                        HabitMomentum.BUILDING  -> MaterialTheme.colorScheme.primaryContainer
                        HabitMomentum.RECOVERING -> MaterialTheme.colorScheme.tertiary
                        HabitMomentum.DORMANT   -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        HabitMomentum.STEADY, null -> null
                    }
                    if (momentumColor != null) {
                        val animatedDotColor by animateColorAsState(
                            targetValue = momentumColor,
                            animationSpec = AppMotion.colorTween(),
                            label = "momentumDot"
                        )
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(5.dp)
                                .background(
                                    color = animatedDotColor,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = checkboxScale
                            scaleY = checkboxScale
                        }
                    ) {
                        Checkbox(
                            checked = isCompletedToday,
                            enabled = !isPaused && isScheduledToday,
                            onCheckedChange = { onToggleCompletion() },
                            modifier = Modifier.semantics {
                                contentDescription = if (isCompletedToday) "Completed" else "Mark complete"
                            }
                        )
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Longest streak: ${habit.longestStreak}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Description — expanded-only, collapsed stays clean
                    if (!habit.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = habit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { alpha = 0.85f }
                        )
                    }
                    // Category chip — muted, expanded-only, distinct from goal chip (no emoji prefix)
                    if (!habit.category.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.material3.SuggestionChip(
                            onClick = { },
                            label = {
                                Text(
                                    habit.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                            ),
                            colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // FlowRow wraps buttons on narrow screens instead of overflowing
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        androidx.compose.material3.FilledTonalButton(onClick = onTogglePause) {
                            Text(text = if (isPaused) "Resume" else "Pause", maxLines = 1)
                        }
                        androidx.compose.material3.FilledTonalButton(onClick = onFreeze, enabled = canFreeze) {
                            Text(text = "Freeze", maxLines = 1)
                        }
                        OutlinedButton(onClick = onEdit) {
                            Text(text = "Edit", maxLines = 1)
                        }
                        androidx.compose.material3.IconButton(onClick = { showDeleteDialog = true }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Habit",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (habit.reminderEnabled) {
                        Text(
                            text = "Reminder: ${habit.reminderTime ?: "Not set"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onDisableReminder) {
                            Text(text = "Disable reminder")
                        }
                    } else {
                        OutlinedButton(onClick = onEnableReminder) {
                            Text(text = "Enable reminder")
                        }
                    }

                    if (habit.stepEnabled && habit.stepGoal != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!stepSupported) {
                            Text(
                                text = "Step tracking not supported",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val progress by animateFloatAsState(
                                targetValue = (stepsToday.toFloat() / habit.stepGoal).coerceIn(0f, 1f),
                                animationSpec = AppMotion.floatTween(AppMotion.durationShort),
                                label = "stepProgress"
                            )
                            Text(
                                text = "Steps: $stepsToday / ${habit.stepGoal}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxSize()
                                ) {}
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(6.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Habit?") },
            text = { Text("Are you sure you want to delete '${habit.name}'? All history and completions will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
