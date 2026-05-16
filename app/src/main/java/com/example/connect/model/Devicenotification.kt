package com.example.connect.model

data class DeviceNotification(
    val id: String,
    val title: String,
    val body: String,
    val level: NotificationLevel,
    val timestamp: Long,
    val isRead: Boolean = false,
    // If set, tapping this notification navigates to this route
    val targetRoute: String? = null,
    // Links this notification to a live FileTransfer for progress display
    val transferId: String? = null
)