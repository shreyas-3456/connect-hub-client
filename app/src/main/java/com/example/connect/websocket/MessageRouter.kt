package com.example.connect.websocket

import android.os.Build
import com.example.connect.data.model.SignalEnvelope
import com.example.connect.websocket.WebSocketManager
import com.example.connect.manager.NotificationManager
import com.example.connect.manager.FileTransferManager

import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MessageRouter(
    private val webSocketManager: WebSocketManager,
    private val fileTransferManager: FileTransferManager,
    private val notificationManager: NotificationManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        private const val TAG = "MessageRouter"

        // Message type constants — must match server protocol exactly
        const val TYPE_FILE_META  = "file_meta"
        const val TYPE_FILE_CHUNK = "file_chunk"
        const val TYPE_FILE_ACK   = "file_ack"
        const val TYPE_NOTIFICATION = "notification"
        const val TYPE_PING       = "ping"
        const val TYPE_PONG       = "pong"
    }

    /**
     * Start collecting from [WebSocketManager.incomingMessages] and
     * dispatching each envelope to the appropriate handler.
     * Call this once after all dependencies are wired up.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun start() {
        scope.launch {
            webSocketManager.incomingMessages.collect { envelope ->
                route(envelope)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun route(envelope: SignalEnvelope) {
        Log.d(TAG, "Routing message type=${envelope.type} from=${envelope.from}")
        when (envelope.type) {
            TYPE_FILE_META        -> fileTransferManager.onFileMeta(envelope)
            TYPE_FILE_CHUNK       -> fileTransferManager.onFileChunk(envelope)
            TYPE_FILE_ACK         -> fileTransferManager.onFileAck(envelope)
            TYPE_NOTIFICATION     -> notificationManager.onNotification(envelope)
            TYPE_PONG             -> Log.d(TAG, "Pong received from ${envelope.from}")
            TYPE_PING             -> Log.d(TAG, "Ping received from ${envelope.from} — no action needed")
            else                  -> Log.w(TAG, "Unknown message type: '${envelope.type}' — ignored")
        }
    }
}