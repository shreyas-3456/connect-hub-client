package com.example.connect.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connect.data.model.ConnectionStatus
import com.example.connect.data.model.FileTransfer
import com.example.connect.model.TransferDirection
import com.example.connect.model.TransferStatus
import com.example.connect.ui.theme.*
import com.example.connect.viewmodel.ConnectUiState

@Composable
fun FilesScreen(
    uiState: ConnectUiState,
    onSendFile: (Uri, String) -> Unit
) {
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onSendFile(uri, mime)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Charcoal900)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Title ─────────────────────────────────────────────────────────
        Text(
            text       = "Files",
            color      = TextPrimary,
            fontSize   = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(top = 16.dp)
        )

        // ── Send File button ──────────────────────────────────────────────
        Button(
            onClick  = { filePicker.launch("*/*") },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan
            ),
            enabled = uiState.connectionStatus ==
                    ConnectionStatus.CONNECTED
        ) {
            Icon(
                imageVector        = Icons.Filled.MailOutline,
                contentDescription = "Send file",
                tint               = Charcoal900,
                modifier           = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Send File",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Charcoal900
            )
        }

        // ── Transfer list ─────────────────────────────────────────────────
        if (uiState.transfers.isEmpty()) {
            EmptyTransfers()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.transfers, key = { it.id }) { transfer ->
                    TransferCard(
                        transfer = transfer,
                        progress = uiState.activeTransferProgress[transfer.id] ?: 0f
                    )
                }
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyTransfers() {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No transfers yet", color = TextSecondary, fontSize = 16.sp)
            Text(
                text     = "Send a file to get started",
                color    = TextSecondary.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

// ── TransferCard ──────────────────────────────────────────────────────────────

@Composable
fun TransferCard(
    transfer: FileTransfer,
    progress: Float
) {
    val statusColor = when (transfer.status) {
        TransferStatus.IN_PROGRESS -> ElectricCyan
        TransferStatus.COMPLETED   -> Color(0xFF00C853)   // green
        TransferStatus.FAILED      -> RedError
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal800)
            .border(1.dp, Charcoal600, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Top row: icon + filename + status chip ────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (transfer.direction == TransferDirection.UPLOAD)
                    Icons.Filled.MailOutline else Icons.Filled.Add,
                contentDescription = null,
                tint               = statusColor,
                modifier           = Modifier.size(20.dp)
            )
            Text(
                text      = transfer.fileName,
                color     = TextPrimary,
                fontSize  = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.weight(1f)
            )
            StatusChip(status = transfer.status, color = statusColor)
        }

        // ── File size + chunk progress ────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text     = formatFileSize(transfer.fileSize),
                color    = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text     = "${transfer.sentChunks} / ${transfer.totalChunks} chunks",
                color    = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Progress bar (only while in progress) ─────────────────────────
        if (transfer.status == TransferStatus.IN_PROGRESS) {
            LinearProgressIndicator(
                progress          = { progress },
                modifier          = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color             = ElectricCyan,
                trackColor        = Charcoal700
            )
        }
    }
}

// ── Status chip ───────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: TransferStatus, color: Color) {
    val label = when (status) {
        TransferStatus.IN_PROGRESS -> "In Progress"
        TransferStatus.COMPLETED   -> "Done"
        TransferStatus.FAILED      -> "Failed"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}