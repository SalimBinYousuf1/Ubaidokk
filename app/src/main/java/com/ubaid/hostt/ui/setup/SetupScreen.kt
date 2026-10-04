package com.ubaid.hostt.ui.setup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubaid.hostt.MainUiState
import com.ubaid.hostt.R
import com.ubaid.hostt.ui.qr.QrCodeGenerator
import com.ubaid.hostt.ui.theme.AccentCharcoal
import com.ubaid.hostt.ui.theme.BackgroundLight
import com.ubaid.hostt.ui.theme.StatusGreen
import com.ubaid.hostt.ui.theme.StatusGreenBg
import com.ubaid.hostt.ui.theme.SurfaceCard
import com.ubaid.hostt.ui.theme.SurfaceCardBorder
import com.ubaid.hostt.ui.theme.TextPrimary
import com.ubaid.hostt.ui.theme.TextSecondary
import com.ubaid.hostt.ui.theme.TextTertiary

@Composable
fun SetupScreen(
    uiState: MainUiState,
    pairingPayloadJson: String,
    onRequestScreenCapture: () -> Unit,
    onCompleteSetup: () -> Unit
) {
    val context = LocalContext.current
    val allRequiredGranted = uiState.isScreenCaptureAuthorized &&
            uiState.isAccessibilityGranted &&
            uiState.isNotificationGranted &&
            uiState.isStorageGranted &&
            uiState.isBatteryOptExempt

    Scaffold(
        containerColor = BackgroundLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Step 1: MediaProjection Screen Capture
            item {
                SetupStepCard(
                    stepNumber = "1",
                    title = stringResource(R.string.step_screen_capture_title),
                    description = stringResource(R.string.step_screen_capture_desc),
                    icon = Icons.AutoMirrored.Filled.ScreenShare,
                    isGranted = uiState.isScreenCaptureAuthorized,
                    actionText = stringResource(R.string.btn_authorize_screen),
                    testTag = "step_screen_capture_btn",
                    onAction = onRequestScreenCapture
                )
            }

            // Step 2: Accessibility Service
            item {
                SetupStepCard(
                    stepNumber = "2",
                    title = stringResource(R.string.step_accessibility_title),
                    description = stringResource(R.string.step_accessibility_desc),
                    icon = Icons.Default.TouchApp,
                    isGranted = uiState.isAccessibilityGranted,
                    actionText = stringResource(R.string.btn_open_accessibility),
                    testTag = "step_accessibility_btn",
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Step 3: Notification Access
            item {
                SetupStepCard(
                    stepNumber = "3",
                    title = stringResource(R.string.step_notification_title),
                    description = stringResource(R.string.step_notification_desc),
                    icon = Icons.Default.Notifications,
                    isGranted = uiState.isNotificationGranted,
                    actionText = stringResource(R.string.btn_grant_notifications),
                    testTag = "step_notification_btn",
                    onAction = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Step 4: Storage Access
            item {
                SetupStepCard(
                    stepNumber = "4",
                    title = stringResource(R.string.step_storage_title),
                    description = stringResource(R.string.step_storage_desc),
                    icon = Icons.Default.Folder,
                    isGranted = uiState.isStorageGranted,
                    actionText = stringResource(R.string.btn_grant_storage),
                    testTag = "step_storage_btn",
                    onAction = {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(fallbackIntent)
                        }
                    }
                )
            }

            // Step 5: Battery Optimization Exemption
            item {
                SetupStepCard(
                    stepNumber = "5",
                    title = stringResource(R.string.step_battery_title),
                    description = stringResource(R.string.step_battery_desc),
                    icon = Icons.Default.BatteryChargingFull,
                    isGranted = uiState.isBatteryOptExempt,
                    actionText = stringResource(R.string.btn_disable_battery_opt),
                    testTag = "step_battery_btn",
                    onAction = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // Step 6: Pairing QR Code & Launch
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("step_pairing_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AccentCharcoal),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "6",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.step_pairing_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(R.string.step_pairing_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (allRequiredGranted) {
                            val qrBitmap = remember(pairingPayloadJson) {
                                QrCodeGenerator.generateQrCodeBitmap(pairingPayloadJson, size = 480)
                            }

                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "Pairing QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Pairing ID: ${uiState.hostId}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Ubaid Pairing ID", uiState.hostId))
                                        Toast.makeText(context, "Pairing ID copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_pairing_id_btn"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_copy_pairing_id), fontSize = 13.sp)
                                }

                                Button(
                                    onClick = onCompleteSetup,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("finish_setup_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCharcoal,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Start Host", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Text(
                                text = "Grant permissions 1 through 5 above to reveal the pairing QR code and activate host broadcasting.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionText: String,
    testTag: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_card_$stepNumber"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) StatusGreen else AccentCharcoal),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusGreenBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Granted",
                            color = StatusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag(testTag),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCharcoal,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = actionText, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
