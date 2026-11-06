package com.artemis.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.agent.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasScreenCapturePermission: Boolean,
    hasShizukuPermission: Boolean,
    isShizukuInstalled: Boolean,
    apiKey: String,
    isServiceRunning: Boolean,
    onSaveApiKey: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onRequestShizuku: () -> Unit,
    onToggleService: () -> Unit
) {
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Artemis Agent",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Autonomous Android AI Automation",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Direct on-device automation with Multimodal VLM perception and Advanced ADB powers.",
                        color = TextLight.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }

            // Permissions Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "System Permissions & Services",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    PermissionItem(
                        title = "1. Floating Overlay (Draw over apps)",
                        isGranted = hasOverlayPermission,
                        actionLabel = "Grant",
                        onAction = onRequestOverlay
                    )

                    PermissionItem(
                        title = "2. Accessibility Service (Gestures & UI)",
                        isGranted = hasAccessibilityPermission,
                        actionLabel = "Enable",
                        onAction = onRequestAccessibility
                    )

                    PermissionItem(
                        title = "3. Screen Capture (MediaProjection)",
                        isGranted = hasScreenCapturePermission,
                        actionLabel = "Initialize",
                        onAction = onRequestScreenCapture
                    )

                    PermissionItem(
                        title = "4. Shizuku (Advanced Mode - ADB Shell)",
                        isGranted = hasShizukuPermission,
                        subtitle = if (!isShizukuInstalled) "Shizuku app not detected (Standard mode only)" else "Wireless ADB on-device without PC",
                        actionLabel = if (isShizukuInstalled) "Grant" else "Info",
                        onAction = onRequestShizuku,
                        optional = true
                    )
                }
            }

            // Gemini API Key Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Gemini VLM API Key",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Get free key from aistudio.google.com (supports Gemini 2.0 Flash)",
                        color = TextLight.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = PrimaryPurple
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onSaveApiKey(apiKeyInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Key", color = Color.Black)
                    }
                }
            }

            // Launch / Stop Floating Agent Button
            val canLaunch = hasOverlayPermission && hasAccessibilityPermission && apiKey.isNotBlank()

            Button(
                onClick = onToggleService,
                enabled = canLaunch || isServiceRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) ErrorRed else SuccessGreen,
                    disabledContainerColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isServiceRunning) "Stop Floating Agent" else "Start Floating Agent",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    isGranted: Boolean,
    subtitle: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
    optional: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isGranted) SuccessGreen else if (optional) WarningYellow else ErrorRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = if (isGranted) SuccessGreen else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextLight.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                )
            }
        }

        if (!isGranted) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = actionLabel, fontSize = 12.sp, color = Color.Black)
            }
        }
    }
}
