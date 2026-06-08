package com.mlue.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    navController: NavController,
    title: String,
    assetFileName: String
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    var textContent by remember { mutableStateOf<AnnotatedString?>(null) }

    LaunchedEffect(assetFileName) {
        try {
            val rawText = context.assets.open(assetFileName).use { inputStream ->
                InputStreamReader(inputStream).readText()
            }
            textContent = parseSimpleMarkdown(rawText, primaryColor)
        } catch (e: Exception) {
            textContent = AnnotatedString("Failed to load document.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 64.dp)
        ) {
            textContent?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private fun parseSimpleMarkdown(text: String, primaryColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var inBold = false
        
        for (line in lines) {
            if (line.startsWith("### ")) {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontSize = 18.sp
                    )
                ) {
                    append(line.removePrefix("### "))
                }
                append("\n")
            } else if (line.startsWith("## ")) {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontSize = 20.sp
                    )
                ) {
                    append(line.removePrefix("## "))
                }
                append("\n")
            } else if (line.startsWith("# ")) {
                withStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                ) {
                    append(line.removePrefix("# "))
                }
                append("\n")
            } else {
                // Parse bold **text** within the line
                var i = 0
                while (i < line.length) {
                    if (i < line.length - 1 && line[i] == '*' && line[i + 1] == '*') {
                        inBold = !inBold
                        i += 2
                        if (inBold) {
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        } else {
                            pop()
                        }
                    } else {
                        append(line[i].toString())
                        i++
                    }
                }
                append("\n")
            }
        }
    }
}
