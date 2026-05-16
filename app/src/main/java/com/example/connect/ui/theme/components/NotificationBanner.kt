package com.example.connect.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.connect.model.DeviceNotification
import com.example.connect.model.NotificationLevel
import com.example.connect.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun NotificationBanner(
    notification: DeviceNotification?,
    onDismiss: () -> Unit
) {
    // Auto-dismiss after 3 seconds
    LaunchedEffect(notification?.id) {
        if (notification != null) {
            delay(3000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = notification != null,
        enter   = slideInVertically(
            initialOffsetY = { -it },
            animationSpec  = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit    = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ) + fadeOut(animationSpec = tween(250))
    ) {
        notification ?: return@AnimatedVisibility

        val accentColor = when (notification.level) {
            NotificationLevel.INFO  -> ElectricCyan
            NotificationLevel.WARN  -> AmberWarning
            NotificationLevel.ERROR -> RedError
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Charcoal800)
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        ) {
            // Coloured left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Row(
                modifier              = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = notification.title,
                        color      = TextPrimary,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (notification.body.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text      = notification.body,
                            color     = TextSecondary,
                            fontSize  = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                IconButton(
                    onClick  = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint               = TextSecondary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}