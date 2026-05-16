package com.example.connect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connect.model.DeviceNotification
import com.example.connect.model.NotificationLevel
import com.example.connect.ui.theme.*
import com.example.connect.viewmodel.ConnectUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationsScreen(
    uiState: ConnectUiState,
    onMarkRead: () -> Unit,
    onNotificationTapped: (DeviceNotification) -> Unit
) {
    // Clear badge as soon as screen is entered
    LaunchedEffect(Unit) {
        onMarkRead()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal900)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Title + unread badge ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "Notifications",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            if (uiState.unreadNotificationCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ElectricCyan)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.unreadNotificationCount.toString(),
                        color = Charcoal900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── List or empty state ───────────────────────────────────────────
        if (uiState.notifications.isEmpty()) {
            EmptyNotifications()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        progress = notification.transferId
                            ?.let { uiState.activeTransferProgress[it] },
                        onClick = { onNotificationTapped(notification) }
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyNotifications() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No notifications", color = TextSecondary, fontSize = 16.sp)
            Text(
                text     = "Messages from your desktop will appear here",
                color    = TextSecondary.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

// ── Notification row ──────────────────────────────────────────────────────────

    @Composable
    private fun NotificationRow(
        notification: DeviceNotification,
        progress:     Float?,          // null = not a file transfer notification
        onClick:      () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Charcoal800)
                .border(1.dp, Charcoal600, RoundedCornerShape(12.dp))
                .then(
                    if (notification.targetRoute != null)
                        Modifier.clickable(onClick = onClick)
                    else Modifier
                )
        ) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(color = ElectricCyan))

            Column(
                modifier            = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // existing title row + body unchanged ...

                // ── Progress bar (only for file-transfer notifications) ────────
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress    = { progress },
                            modifier    = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color       = ElectricCyan,
                            trackColor  = Charcoal600,
                        )
                        Text(
                            text     = if (progress >= 1f) "Complete"
                            else "${(progress * 100).toInt()}%",
                            color    = if (progress >= 1f) ElectricCyan else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // ── "Tap to view" hint ────────────────────────────────────────
                if (notification.targetRoute != null) {
                    Text(
                        text     = "Tap to view files →",
                        color    = ElectricCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
// ── Level chip ────────────────────────────────────────────────────────────────

@Composable
private fun LevelChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text       = label,
            color      = color,
            fontSize   = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Timestamp formatter ───────────────────────────────────────────────────────

private fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}