package com.example.connect.model

data class DeviceNotification(
    val id: String,
    val title: String,
    val body: String,
    val level: NotificationLevel,
    val timestamp: Long,
    val isRead: Boolean = false
)