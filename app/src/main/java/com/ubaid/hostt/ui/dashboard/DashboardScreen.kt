package com.ubaid.hostt.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ubaid.hostt.MainUiState
import com.ubaid.hostt.R
import com.ubaid.hostt.model.ActivityLogItem
import com.ubaid.hostt.model.ConnectionStatus
import com.ubaid.hostt.model.LogCategory
import com.ubaid.hostt.ui.qr.QrCodeGenerator
import com.ubaid.hostt.ui.theme.AccentCharcoal
import com.ubaid.hostt.ui.theme.BackgroundLight
import com.ubaid.hostt.ui.theme.StatusAmber
import com.ubaid.hostt.ui.theme.StatusAmberBg
import com.ubaid.hostt.ui.theme.StatusGreen
import com.ubaid.hostt.ui.theme.StatusGreenBg
import com.ubaid.hostt.ui.theme.SurfaceCard
import com.ubaid.hostt.ui.theme.SurfaceCardBorder
import com.ubaid.hostt.ui.theme.TextPrimary
import com.ubaid.hostt.ui.theme.TextSecondary
import com.ubaid.hostt.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: MainUiState,
    activityLogs: List<ActivityLogItem>,
    pairingPayloadJson: String,
    onToggleService: () -> Unit
) {
    val context = LocalContext.current
    var showQrSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

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
            // Header
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    // Status Pill
                    val (statusText, statusColor, statusBg) = when (uiState.connectionStatus) {
                        ConnectionStatus.CONNECTED -> Triple(
                            stringResource(R.string.status_connected),
                            StatusGreen,
                            StatusGreenBg
                        )
                        ConnectionStatus.WAITING_FOR_CONTROLLER -> Triple(
                            stringResource(R.string.status_broadcasting),
                            StatusGreen,
                            StatusGreenBg
                        )
                        ConnectionStatus.CONNECTING -> Triple("Connecting", StatusAmber, StatusAmberBg)
                        ConnectionStatus.ERROR -> Triple("Reconnecting", StatusAmber, StatusAmberBg)
                        ConnectionStatus.DISCONNECTED -> Triple(
                            stringResource(R.string.status_disconnected),
                            TextSecondary,
                            BackgroundLight
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusBg)
                            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Hero Pairing Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_pairing_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.host_id_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                                Text(
                                    text = uiState.hostId,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Ubaid Pairing ID", uiState.hostId))
                                        Toast.makeText(context, "Pairing ID copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("copy_id_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy ID",
                                        tint = AccentCharcoal
                                    )
                                }

                                Button(
                                    onClick = { showQrSheet = true },
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("show_qr_sheet_btn"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentCharcoal,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.btn_show_qr), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Telemetry 2x2 Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.telemetry_battery),
                            value = "${uiState.deviceStatus.batteryPercent}%",
                            subtitle = if (uiState.deviceStatus.isCharging) uiState.deviceStatus.powerSource else "Discharging",
                            icon = Icons.Default.BatteryChargingFull,
                            iconTint = if (uiState.deviceStatus.batteryPercent < 20) StatusAmber else StatusGreen
                        )
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.telemetry_network),
                            value = uiState.deviceStatus.networkType,
                            subtitle = uiState.deviceStatus.ipAddress,
                            icon = Icons.Default.Wifi,
                            iconTint = AccentCharcoal
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.telemetry_screen),
                            value = if (uiState.deviceStatus.isScreenOn) "Display On" else "Display Off",
                            subtitle = uiState.deviceName,
                            icon = Icons.Default.Smartphone,
                            iconTint = AccentCharcoal
                        )
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Host Service",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.isServiceRunning) "Running" else "Stopped",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isServiceRunning) StatusGreen else TextSecondary
                                    )
                                    Switch(
                                        checked = uiState.isServiceRunning,
                                        onCheckedChange = { onToggleService() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AccentCharcoal
                                        ),
                                        modifier = Modifier.testTag("service_toggle_switch")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // System Services Active Checklist
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.active_services_header),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ServiceStatusRow(
                            icon = Icons.AutoMirrored.Filled.ScreenShare,
                            name = "Screen Mirroring (WebRTC Video)",
                            isActive = uiState.isScreenCaptureAuthorized
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ServiceStatusRow(
                            icon = Icons.Default.TouchApp,
                            name = "Remote Touch & Control Engine",
                            isActive = uiState.isAccessibilityGranted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ServiceStatusRow(
                            icon = Icons.Default.Notifications,
                            name = "Real-time Notification Forwarding",
                            isActive = uiState.isNotificationGranted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ServiceStatusRow(
                            icon = Icons.Default.Folder,
                            name = "Encrypted File Server Channel",
                            isActive = uiState.isStorageGranted
                        )
                    }
                }
            }

            // Live Activity Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.activity_log_header),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${activityLogs.size} events",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            if (activityLogs.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_log_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
            } else {
                items(activityLogs, key = { it.id }) { item ->
                    ActivityLogCard(item)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheet with Pairing QR Code
    if (showQrSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQrSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pair With Salim",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Scan this QR code from the Salim Controller app on Phone B.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))

                val qrBitmap = remember(pairingPayloadJson) {
                    QrCodeGenerator.generateQrCodeBitmap(pairingPayloadJson, size = 512)
                }

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Pairing QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Pairing ID: ${uiState.hostId}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showQrSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCharcoal,
                        contentColor = Color.White
                    )
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TelemetryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun ServiceStatusRow(
    icon: ImageVector,
    name: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) AccentCharcoal else TextTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isActive) StatusGreenBg else BackgroundLight)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isActive) "Active" else "Inactive",
                color = if (isActive) StatusGreen else TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ActivityLogCard(item: ActivityLogItem) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            val (badgeColor, badgeBg) = when (item.category) {
                LogCategory.TOUCH -> Pair(AccentCharcoal, SurfaceCardBorder)
                LogCategory.WEBRTC -> Pair(StatusGreen, StatusGreenBg)
                LogCategory.NOTIFICATION -> Pair(Color(0xFF8B5CF6), Color(0xFFF3E8FF))
                LogCategory.FILE -> Pair(Color(0xFF3B82F6), Color(0xFFEFF6FF))
                LogCategory.SYSTEM -> Pair(TextSecondary, BackgroundLight)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.category.name,
                    color = badgeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
