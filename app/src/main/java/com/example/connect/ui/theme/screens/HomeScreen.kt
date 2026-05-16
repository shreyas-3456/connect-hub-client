package com.example.connect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connect.data.model.ConnectionStatus
import com.example.connect.ui.theme.*
import com.example.connect.viewmodel.ConnectUiState

@Composable
fun HomeScreen(
    uiState: ConnectUiState,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal900)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            text       = "Connect",
            color      = TextPrimary,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(top = 16.dp)
        )

        // ── Connection status pill ────────────────────────────────────────
        StatusPill(status = uiState.connectionStatus)

        // ── Device info card ──────────────────────────────────────────────
        if (uiState.deviceId != null || uiState.tunnelUrl != null) {
            DeviceInfoCard(
                deviceId  = uiState.deviceId,
                tunnelUrl = uiState.tunnelUrl
            )
        }

        // ── Error message ─────────────────────────────────────────────────
        if (uiState.errorMessage != null) {
            ErrorBanner(message = uiState.errorMessage)
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Disconnect button ─────────────────────────────────────────────
        if (uiState.connectionStatus != ConnectionStatus.DISCONNECTED) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RedError
                )
            ) {
                Text(
                    text       = "Disconnect",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White
                )
            }
        }
    }
}

// ── Status Pill ───────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(status: ConnectionStatus) {
    val (dotColor, label) = when (status) {
        ConnectionStatus.CONNECTED    -> ElectricCyan to "Connected"
        ConnectionStatus.CONNECTING   -> AmberWarning to "Connecting…"
        ConnectionStatus.ERROR        -> RedError     to "Error"
        ConnectionStatus.DISCONNECTED -> Charcoal600  to "Disconnected"
    }

    // Pulsing animation only when connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = if (status == ConnectionStatus.CONNECTED) 0.3f else 1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Charcoal800)
            .border(1.dp, Charcoal600, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor.copy(alpha = if (status == ConnectionStatus.CONNECTED) alpha else 1f))
        )
        Text(
            text     = label,
            color    = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Device Info Card ──────────────────────────────────────────────────────────

@Composable
private fun DeviceInfoCard(
    deviceId:  String?,
    tunnelUrl: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal800)
            .border(1.dp, Charcoal600, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (deviceId != null) {
            InfoRow(label = "Device ID", value = deviceId)
        }
        if (tunnelUrl != null) {
            InfoRow(label = "Tunnel URL", value = tunnelUrl)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text     = label,
            color    = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text       = value,
            color      = ElectricCyan,
            fontSize   = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 18.sp
        )
    }
}

// ── Error Banner ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(RedError.copy(alpha = 0.12f))
            .border(1.dp, RedError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text("⚠", color = RedError, fontSize = 16.sp)
        Text(
            text     = message,
            color    = RedError,
            fontSize = 14.sp
        )
    }
}