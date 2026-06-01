package com.mlue.app.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mlue.app.ui.theme.DarkSurface
import com.mlue.app.ui.theme.LightSurface
import com.mlue.app.viewmodel.HabitViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(navController: NavController, viewModel: HabitViewModel) {
    val systemDark = isSystemInDarkTheme()
    val cachedTheme = viewModel.getCachedTheme()
    val darkMode by viewModel.darkModeEnabled.collectAsState(initial = cachedTheme ?: systemDark)
    val sounds by viewModel.soundsEnabled.collectAsState()
    val focusMode by viewModel.focusModeEnabled.collectAsState()
    val haptics by viewModel.hapticsEnabled.collectAsState()

    val isLightMode = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionLabel("Appearance")
            SettingsCard(isLightMode = isLightMode) {
                SettingsRow(
                    icon = Icons.Outlined.Bedtime,
                    label = "Dark mode",
                    description = "Switch to dark theme",
                    checked = darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsSectionLabel("Behaviour")
            SettingsCard(isLightMode = isLightMode) {
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    label = "Sounds",
                    description = "Completion sounds",
                    checked = sounds,
                    onCheckedChange = { viewModel.setSounds(it) }
                )
                SettingsDivider(isLightMode)
                SettingsRow(
                    icon = Icons.Outlined.TouchApp,
                    label = "Haptic feedback",
                    description = "Subtle vibration on completion",
                    checked = haptics,
                    onCheckedChange = { viewModel.setHaptics(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionLabel("Focus")
            SettingsCard(isLightMode = isLightMode) {
                SettingsRow(
                    icon = Icons.Outlined.CenterFocusStrong,
                    label = "Focus mode",
                    description = "Show only today's scheduled habits",
                    checked = focusMode,
                    onCheckedChange = { viewModel.setFocusMode(it) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            SettingsSectionLabel("About")
            SettingsCard(isLightMode = isLightMode) {
                SettingsClickableRow(
                    icon = Icons.Outlined.Info,
                    label = "How Mlue Works",
                    description = "Understanding habits, goals, and insights",
                    onClick = { navController.navigate("how_works") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    isLightMode: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (isLightMode) com.mlue.app.ui.theme.LightSurfaceElevated else DarkSurface,
        shape = MaterialTheme.shapes.large,
        // Light: warm topology hairline for panel definition
        // Dark: no border — DarkSurface tonal contrast is the separator
        border = if (isLightMode) androidx.compose.foundation.BorderStroke(
            0.5.dp, com.mlue.app.ui.theme.LightTopologyBorder
        ) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsDivider(isLightMode: Boolean) {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isLightMode) 0.7f else 0.3f)
    )
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
