package com.example.connect.websocket

import android.os.Build
import com.example.connect.data.model.SignalEnvelope
import com.example.connect.manager.FileTransferManager
import com.example.connect.manager.NotificationManager
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MessageRouter(
    private val webSocketManager: WebSocketManager,
    private val fileTransferManager: FileTransferManager,
    private val notificationManager: NotificationManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        private const val TAG = "MessageRouter"

        const val TYPE_FILE_META     = "file_meta"
        const val TYPE_FILE_CHUNK    = "file_chunk"
        const val TYPE_FILE_ACK      = "file_ack"
        const val TYPE_FILE_PROGRESS = "file_progress"
        const val TYPE_NOTIFICATION  = "notification"
        const val TYPE_PING          = "ping"
        const val TYPE_PONG          = "pong"
    }

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
            TYPE_FILE_META -> {
                fileTransferManager.onFileMeta(envelope)
                // Parse just what we need for the notification
                try {
                    val obj      = Json { ignoreUnknownKeys = true }
                        .parseToJsonElement(envelope.payload).jsonObject
                    val name     = obj["name"]!!.jsonPrimitive.content
                    val tid      = obj["transferId"]!!.jsonPrimitive.content
                    val sender   = envelope.from ?: "Desktop"
                    notificationManager.addFileTransferNotification(
                        transferId  = tid,
                        fileName    = name,
                        senderName  = sender,
                        targetRoute = "files"          // matches Routes.FILES
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "file_meta notification parse error", e)
                }
            }
            TYPE_FILE_CHUNK    -> fileTransferManager.onFileChunk(envelope)
            TYPE_FILE_ACK      -> fileTransferManager.onFileAck(envelope)
            TYPE_FILE_PROGRESS -> fileTransferManager.onFileProgress(envelope)
            TYPE_NOTIFICATION  -> notificationManager.onNotification(envelope)
            TYPE_PONG          -> Log.d(TAG, "Pong received from ${envelope.from}")
            TYPE_PING          -> Log.d(TAG, "Ping received from ${envelope.from}")
            else               -> Log.w(TAG, "Unknown message type: '${envelope.type}' — ignored")
        }
    }
}