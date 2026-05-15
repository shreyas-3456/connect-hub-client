package com.example.connect.manager

import android.util.Log
import com.example.connect.data.model.SignalEnvelope
import com.example.connect.model.DeviceNotification
import com.example.connect.model.NotificationLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.collections.map

class NotificationManager {

    companion object {
        private const val TAG = "NotificationManager"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Full notification feed (newest first) — shown in NotificationsScreen
    private val _notifications = MutableStateFlow<List<DeviceNotification>>(emptyList())
    val notifications: StateFlow<List<DeviceNotification>> = _notifications.asStateFlow()

    // Unread badge count
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    // Latest notification for the slide-in banner (null when dismissed)
    private val _bannerNotification = MutableStateFlow<DeviceNotification?>(null)
    val bannerNotification: StateFlow<DeviceNotification?> = _bannerNotification.asStateFlow()

    /**
     * Called by MessageRouter when a "notification" envelope arrives.
     */
    fun onNotification(envelope: SignalEnvelope) {
        try {
            val payloadObj = json.parseToJsonElement(envelope.payload).jsonObject

            val title = payloadObj["title"]?.jsonPrimitive?.content ?: "Notification"
            val body  = payloadObj["body"]?.jsonPrimitive?.content  ?: ""
            val levelRaw = payloadObj["level"]?.jsonPrimitive?.content ?: "info"

            val level = when (levelRaw.lowercase()) {
                "warn"  -> NotificationLevel.WARN
                "error" -> NotificationLevel.ERROR
                else    -> NotificationLevel.INFO
            }

            val notification = DeviceNotification(
                id        = UUID.randomUUID().toString(),
                title     = title,
                body      = body,
                level     = level,
                timestamp = System.currentTimeMillis(),
                isRead    = false
            )

            // Prepend so newest is first
            _notifications.value = listOf(notification) + _notifications.value
            _unreadCount.value   = _unreadCount.value + 1
            _bannerNotification.value = notification

            Log.i(TAG, "Notification received [$level] $title")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse notification payload: ${envelope.payload}", e)
        }
    }

    /**
     * Call when the user opens the Notifications tab — clears the badge.
     */
    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value   = 0
    }

    /**
     * Call when the banner auto-dismisses or user swipes it away.
     */
    fun dismissBanner() {
        _bannerNotification.value = null
    }

    /**
     * Clear all notifications (e.g. on disconnect).
     */
    fun clear() {
        _notifications.value      = emptyList()
        _unreadCount.value        = 0
        _bannerNotification.value = null
    }
}