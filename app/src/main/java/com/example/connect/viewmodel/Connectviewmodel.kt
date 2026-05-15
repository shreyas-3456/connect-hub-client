package com.example.connect.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.connect.data.firebase.FirebaseRepository
import com.example.connect.data.model.ConnectionStatus
import com.example.connect.data.model.FileTransfer
import com.example.connect.manager.FileTransferManager
import com.example.connect.manager.NotificationManager
import com.example.connect.model.DeviceNotification
import com.example.connect.websocket.MessageRouter
import com.example.connect.websocket.WebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── UI State ──────────────────────────────────────────────────────────────────

data class ConnectUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val deviceId: String? = null,
    val tunnelUrl: String? = null,
    val transfers: List<FileTransfer> = emptyList(),
    val notifications: List<DeviceNotification> = emptyList(),
    val activeTransferProgress: Map<String, Float> = emptyMap(),
    val unreadNotificationCount: Int = 0,
    val bannerNotification: DeviceNotification? = null,
    val errorMessage: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "connect_prefs"
        private const val KEY_PEER_ID = "peer_id"
    }

    // ── Peer identity (stable across launches) ────────────────────────────────

    val selfPeerId: String by lazy {
        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.getString(KEY_PEER_ID, null) ?: run {
            val newId = "phone-${java.util.UUID.randomUUID()}"
            prefs.edit().putString(KEY_PEER_ID, newId).apply()
            newId
        }
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private val _deviceId  = MutableStateFlow<String?>(null)
    private val _tunnelUrl = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)

    // ── Managers ──────────────────────────────────────────────────────────────

    val webSocketManager = WebSocketManager(viewModelScope)

    val notificationManager = NotificationManager()

    // FileTransferManager is created once we know the remote peerId (deviceId).
    // Held in a StateFlow so the UI can react if it becomes available.
    private val _fileTransferManager = MutableStateFlow<FileTransferManager?>(null)

    private var messageRouter: MessageRouter? = null

    private val firebaseRepository = FirebaseRepository()

    // ── Merged UI state ───────────────────────────────────────────────────────

    val uiState: StateFlow<ConnectUiState> = combine(
        webSocketManager.connectionStatus,
        _deviceId,
        _tunnelUrl,
        notificationManager.notifications,
        notificationManager.unreadCount,
        notificationManager.bannerNotification,
        _errorMessage
    ) { values ->
        // combine with 7 flows gives us an Array<Any?>
        @Suppress("UNCHECKED_CAST")
        ConnectUiState(
            connectionStatus       = values[0] as ConnectionStatus,
            deviceId               = values[1] as String?,
            tunnelUrl              = values[2] as String?,
            notifications          = values[3] as List<DeviceNotification>,
            unreadNotificationCount = values[4] as Int,
            bannerNotification     = values[5] as DeviceNotification?,
            errorMessage           = values[6] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectUiState()
    )

    // Transfer state is collected separately and merged into uiState via
    // a second combine once a FileTransferManager exists.
    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    private val _progress  = MutableStateFlow<Map<String, Float>>(emptyMap())

    val uiStateWithTransfers: StateFlow<ConnectUiState> = combine(
        uiState,
        _transfers,
        _progress
    ) { base, transfers, progress ->
        base.copy(
            transfers             = transfers,
            activeTransferProgress = progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectUiState()
    )

    // ── Public actions ────────────────────────────────────────────────────────

    /**
     * Called after QR scan returns a deviceId.
     * Looks up Firebase for the tunnel URL then connects.
     */
    fun onDeviceScanned(deviceId: String) {
        _deviceId.value = deviceId
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val url = firebaseRepository.getDeviceUrl(deviceId)
                if (url == null) {
                    _errorMessage.value = "Device '$deviceId' not found or offline."
                    return@launch
                }
                _tunnelUrl.value = url
                connectToDevice(deviceId, url)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to reach Firebase: ${e.message}"
            }
        }
    }

    /**
     * Call from Lifecycle.Event.ON_RESUME to silently reconnect if needed.
     */
    fun reconnectIfNeeded() {
        webSocketManager.reconnectIfNeeded()
    }

    /**
     * Disconnect and reset all state.
     */
    fun disconnect() {
        webSocketManager.disconnect()
        _fileTransferManager.value = null
        messageRouter = null
        _deviceId.value = null
        _tunnelUrl.value = null
        _transfers.value = emptyList()
        _progress.value  = emptyMap()
        notificationManager.clear()
    }

    /**
     * Send a file to the currently connected desktop.
     */
    fun sendFile(fileUri: Uri, mimeType: String) {
        _fileTransferManager.value?.sendFile(fileUri, mimeType)
            ?: run { _errorMessage.value = "Not connected — cannot send file." }
    }

    /**
     * Call when the user opens the Notifications tab.
     */
    fun markNotificationsRead() = notificationManager.markAllRead()

    /**
     * Call when the slide-in banner auto-dismisses or is swiped.
     */
    fun dismissBanner() = notificationManager.dismissBanner()

    fun clearError() { _errorMessage.value = null }

    // ── Internal wiring ───────────────────────────────────────────────────────

    private fun connectToDevice(deviceId: String, tunnelUrl: String) {
        // Build FileTransferManager now that we have the remote peerId
        val ftm = FileTransferManager(
            context       = getApplication(),
            sendEnvelope  = { envelope -> webSocketManager.send(envelope) },
            selfPeerId    = selfPeerId,
            remotePeerId  = deviceId
        )
        _fileTransferManager.value = ftm

        // Collect transfer state into our shared flows
        viewModelScope.launch {
            ftm.transfers.collect { _transfers.value = it }
        }
        viewModelScope.launch {
            ftm.progress.collect { _progress.value = it }
        }

        // Wire up the router
        val router = MessageRouter(
            webSocketManager  = webSocketManager,
            fileTransferManager = ftm,
            notificationManager = notificationManager,
            scope             = viewModelScope
        )
        messageRouter = router
        router.start()

        // Connect the socket
        webSocketManager.connect(tunnelUrl, selfPeerId)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        webSocketManager.teardown()
    }
}