package com.mlue.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowMlueWorksScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") darkMode: Boolean
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How Mlue Works") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {

            EditorialSection(
                title = "Why Mlue exists",
                body = "Most habit apps feel like they're judging you.\n\nMlue doesn't. It was built on a simpler belief — that showing up, even imperfectly, is enough. No streaks to protect. No scores to chase. Just a quiet, honest record of how you're spending your days."
            )

            EditorialSection(
                title = "Habits",
                body = "A habit is something small you want to do regularly.\n\nCreate one, give it a name, and optionally set a reminder. Each day, mark it done when you're ready. Over time, patterns emerge on their own — no pressure required."
            )

            EditorialSection(
                title = "Goals",
                body = "Goals give your habits a shared direction.\n\nIf you're working on sleeping better or moving more, a goal lets you group related habits and see them as a whole. Think of it less as a finish line, and more as a loose intention."
            )

            EditorialSection(
                title = "Insights",
                body = "Insights appear once your habits have history to draw from.\n\nThey surface quietly — a best day, a natural rhythm, a streak you didn't notice. They're observations, not grades. Meant to inform, never to shame."
            )

            EditorialSection(
                title = "Journal",
                body = "Completely optional. Entirely private.\n\nThe journal gives you a small space to note how today felt. A sentence is enough. It adds texture to the data, and sometimes, writing it down is the point."
            )

            EditorialSection(
                title = "Focus Mode",
                body = "Some days, the full list is too much.\n\nFocus Mode narrows your view to what genuinely matters today. It reduces noise without hiding anything, so you can stay present without feeling behind."
            )

            EditorialSection(
                title = "Your data stays with you",
                body = "Mlue works entirely offline.\n\nYour habits, journal, and history live only on your device. There is no account to create, no server to sync with, and nothing being collected in the background. When you close the app, nothing leaves."
            )

            EditorialSection(
                title = "Privacy, by design",
                body = "We don't track how you use the app. We don't know your name, your habits, or your schedule.\n\nThat's not an accident — it's the point. Mlue was designed to be a tool you trust, not a platform that studies you."
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun EditorialSection(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 26.sp
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )
    }
}
