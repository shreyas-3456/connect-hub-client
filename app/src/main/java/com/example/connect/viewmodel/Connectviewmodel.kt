package com.example.connect.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.connect.data.firebase.DeviceInfo
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
    val errorMessage: String? = null,
    // Device discovery
    val onlineDevices: List<DeviceInfo> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val pendingNavigationRoute: String? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ConnectViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME  = "connect_prefs"
        private const val KEY_PEER_ID = "peer_id"
    }

    val selfPeerId: String by lazy {
        val prefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.getString(KEY_PEER_ID, null) ?: run {
            val newId = "phone-${java.util.UUID.randomUUID()}"
            prefs.edit().putString(KEY_PEER_ID, newId).apply()
            newId
        }
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private val _deviceId        = MutableStateFlow<String?>(null)
    private val _tunnelUrl       = MutableStateFlow<String?>(null)
    private val _errorMessage    = MutableStateFlow<String?>(null)
    private val _onlineDevices   = MutableStateFlow<List<DeviceInfo>>(emptyList())
    private val _isLoadingDevices = MutableStateFlow(false)



    // ── Managers ──────────────────────────────────────────────────────────────

    val webSocketManager = WebSocketManager(viewModelScope)
    val notificationManager = NotificationManager(application)

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
        @Suppress("UNCHECKED_CAST")
        ConnectUiState(
            connectionStatus        = values[0] as ConnectionStatus,
            deviceId                = values[1] as String?,
            tunnelUrl               = values[2] as String?,
            notifications           = values[3] as List<DeviceNotification>,
            unreadNotificationCount = values[4] as Int,
            bannerNotification      = values[5] as DeviceNotification?,
            errorMessage            = values[6] as String?
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectUiState()
    )

    private val _pendingNav = MutableStateFlow<String?>(null)

    // Call this from the notification tap handler in the UI
    fun onNotificationTapped(notification: DeviceNotification) {
        notification.targetRoute?.let { _pendingNav.value = it }
    }

    fun consumePendingNavigation() { _pendingNav.value = null }

    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    private val _progress  = MutableStateFlow<Map<String, Float>>(emptyMap())
    val pendingNavigationRoute: String? = null   // set when a notification is tapped




    val uiStateWithTransfers: StateFlow<ConnectUiState> = combine(
        uiState, _transfers, _progress, _onlineDevices, _isLoadingDevices, _pendingNav,
    ) { arr ->
        val base    = arr[0] as ConnectUiState
        @Suppress("UNCHECKED_CAST")
        base.copy(
            transfers              = arr[1] as List<FileTransfer>,
            activeTransferProgress = arr[2] as Map<String, Float>,
            onlineDevices          = arr[3] as List<DeviceInfo>,
            isLoadingDevices       = arr[4] as Boolean,
            pendingNavigationRoute = arr[5] as String?
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConnectUiState()
    )

    // ── Public actions ────────────────────────────────────────────────────────

    /**
     * Called after QR scan returns a deviceId.
     */
    fun onDeviceScanned(deviceId: String) {
        _deviceId.value    = deviceId
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
     * Called when the user taps a device from the online devices list.
     * Uses the URL already stored in Firebase — no QR scan needed.
     */
    fun connectToDeviceById(device: DeviceInfo) {
        _deviceId.value     = device.deviceId
        _tunnelUrl.value    = device.url
        _errorMessage.value = null
        connectToDevice(device.deviceId, device.url)
    }

    /**
     * Fetches the current list of online devices from Firebase.
     * Call this when the ScanScreen becomes visible.
     */
    fun refreshOnlineDevices() {
        viewModelScope.launch {
            _isLoadingDevices.value = true
            try {
                _onlineDevices.value = firebaseRepository.getOnlineDevices()
            } catch (e: Exception) {
                _errorMessage.value = "Could not load devices: ${e.message}"
            } finally {
                _isLoadingDevices.value = false
            }
        }
    }

    fun reconnectIfNeeded() = webSocketManager.reconnectIfNeeded()

    fun disconnect() {
        webSocketManager.disconnect()
        _fileTransferManager.value = null
        messageRouter              = null
        _deviceId.value            = null
        _tunnelUrl.value           = null
        _transfers.value           = emptyList()
        _progress.value            = emptyMap()
        notificationManager.clear()
    }

    fun sendFile(fileUri: Uri, mimeType: String) {
        _fileTransferManager.value?.sendFile(fileUri, mimeType)
            ?: run { _errorMessage.value = "Not connected — cannot send file." }
    }

    fun markNotificationsRead() = notificationManager.markAllRead()
    fun dismissBanner()         = notificationManager.dismissBanner()
    fun clearError()            { _errorMessage.value = null }

    // ── Internal wiring ───────────────────────────────────────────────────────

    private fun connectToDevice(deviceId: String, tunnelUrl: String) {
        val ftm = FileTransferManager(
            context      = getApplication(),
            sendEnvelope = { envelope -> webSocketManager.send(envelope) },
            selfPeerId   = selfPeerId,
            remotePeerId = deviceId
        )
        _fileTransferManager.value = ftm

        viewModelScope.launch { ftm.transfers.collect { _transfers.value = it } }
        viewModelScope.launch { ftm.progress.collect  { _progress.value  = it } }

        val router = MessageRouter(
            webSocketManager    = webSocketManager,
            fileTransferManager = ftm,
            notificationManager = notificationManager,
            scope               = viewModelScope
        )
        messageRouter = router
        router.start()

        webSocketManager.connect(tunnelUrl, selfPeerId)
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.teardown()
    }
}