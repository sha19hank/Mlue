package com.mlue.app.ui.screens

import androidx.compose.foundation.background
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
    darkMode: Boolean
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
                body = "Mlue is designed around the quiet power of repetition, not the pressure of perfection. There are no harsh penalties or aggressive streaks. It simply exists to gently reflect your effort over time, helping you build consistency without the anxiety of failure."
            )

            EditorialSection(
                title = "Habits",
                body = "Habits form the foundation of your routines. Small, repeatable actions often matter far more than bursts of intense effort. You can create habits, set optional reminders, and mark them complete each day to watch patterns emerge."
            )

            EditorialSection(
                title = "Goals",
                body = "Goals exist to give your habits direction and context, rather than acting as a finish line. By grouping smaller habits together—like \"Sleep Better\" or \"Move More\"—you can track your broader progress without getting lost in the details."
            )

            EditorialSection(
                title = "Insights",
                body = "Insights become more meaningful as your routines develop. They are meant to be gentle observations, revealing the natural rhythms and trends in how you show up. They are a mirror for reflection, never a judgment on your performance."
            )

            EditorialSection(
                title = "Journal",
                body = "Journaling is an entirely optional, private space. It provides a soft landing spot for daily reflection, helping you add emotional context and meaning to the habits you complete each day."
            )

            EditorialSection(
                title = "Focus Mode",
                body = "Sometimes, seeing everything at once can be overwhelming. Focus Mode intentionally reduces noise, gently prioritizing the habits that need your attention today so you can stay present."
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
