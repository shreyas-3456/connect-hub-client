package com.example.connect.manager

import android.content.Context
import android.util.Log
import com.example.connect.data.model.SignalEnvelope
import com.example.connect.model.DeviceNotification
import com.example.connect.model.NotificationLevel
import com.example.connect.notifications.SystemNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

class NotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "NotificationManager"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _notifications = MutableStateFlow<List<DeviceNotification>>(emptyList())
    val notifications: StateFlow<List<DeviceNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _bannerNotification = MutableStateFlow<DeviceNotification?>(null)
    val bannerNotification: StateFlow<DeviceNotification?> = _bannerNotification.asStateFlow()

    // ── Public: add any notification directly ─────────────────────────────

    fun addNotification(
        title: String,
        body:  String,
        level: NotificationLevel = NotificationLevel.INFO
    ) {
        val notification = DeviceNotification(
            id        = UUID.randomUUID().toString(),
            title     = title,
            body      = body,
            level     = level,
            timestamp = System.currentTimeMillis(),
            isRead    = false
        )
        _notifications.value      = listOf(notification) + _notifications.value
        _unreadCount.value        = _unreadCount.value + 1
        _bannerNotification.value = notification
        Log.i(TAG, "Notification added [$level] $title")
    }

    fun addFileTransferNotification(
        transferId: String,
        fileName:   String,
        senderName: String,          // the peerId / device name of the sender
        targetRoute: String          // Routes.FILES
    ) {
        val notification = DeviceNotification(
            id          = UUID.randomUUID().toString(),
            title       = "Incoming file from $senderName",
            body        = "\"$fileName\" is being received",
            level       = NotificationLevel.INFO,
            timestamp   = System.currentTimeMillis(),
            isRead      = false,
            targetRoute = targetRoute,
            transferId  = transferId
        )
        _notifications.value      = listOf(notification) + _notifications.value
        _unreadCount.value        = _unreadCount.value + 1
        _bannerNotification.value = notification

        SystemNotificationHelper.postFileNotification(
            context    = context,
            notifId    = transferId.hashCode(),
            senderName = senderName,
            fileName   = fileName
        )
    }

    // ── Called by MessageRouter for "notification" envelopes ──────────────

    fun onNotification(envelope: SignalEnvelope) {
        try {
            val payloadObj = json.parseToJsonElement(envelope.payload).jsonObject
            val title    = payloadObj["title"]?.jsonPrimitive?.content ?: "Notification"
            val body     = payloadObj["body"]?.jsonPrimitive?.content  ?: ""
            val levelRaw = payloadObj["level"]?.jsonPrimitive?.content ?: "info"

            val level = when (levelRaw.lowercase()) {
                "warn"  -> NotificationLevel.WARN
                "error" -> NotificationLevel.ERROR
                else    -> NotificationLevel.INFO
            }

            addNotification(title, body, level)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse notification payload: ${envelope.payload}", e)
        }
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value   = 0
    }

    fun dismissBanner() {
        _bannerNotification.value = null
    }

    fun clear() {
        _notifications.value      = emptyList()
        _unreadCount.value        = 0
        _bannerNotification.value = null
    }
}