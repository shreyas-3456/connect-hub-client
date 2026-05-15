package com.example.connect.manager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.android.identity.util.UUID
import com.example.connect.data.model.FileTransfer
import com.example.connect.data.model.SignalEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.example.connect.model.TransferDirection
import com.example.connect.model.TransferStatus
import java.io.InputStream
import android.util.Base64
import androidx.annotation.RequiresApi
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


class FileTransferManager(
    private val context: Context,
    private val sendEnvelope: (SignalEnvelope) -> Unit,
    private val selfPeerId: String,
    private val remotePeerId: String,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
){
    companion object {
        private const val TAG = "FileTransferManager"
        private const val CHUNK_SIZE = 64 * 1024  // 64 KB
        private const val DOWNLOADS_SUBFOLDER = "Connect"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // All transfers (both directions) — shown in FilesScreen
    private val _transfers = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transfers: StateFlow<List<FileTransfer>> = _transfers.asStateFlow()

    // transferId -> 0f..1f progress
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()
    // In-flight receive buffers: transferId -> (totalChunks, sortedMap of index -> bytes)
    private val receiveBuffers = mutableMapOf<String, ReceiveBuffer>()





    // ── Send ───────────────

    /**
     * Call after the user picks a file via ActivityResultContracts.GetContent().
     * Reads, chunks, and sends the file over WebSocket.
     */



    fun sendFile(fileUri: Uri, mimeType: String) {
        scope.launch {
            try {
                val cr = context.contentResolver
                val fileName  = resolveFileName(fileUri) ?: "file_${System.currentTimeMillis()}"
                val fileSize  = resolveFileSize(fileUri)
                val transferId = UUID.randomUUID().toString()
                val stream    = cr.openInputStream(fileUri) ?: run {
                    Log.e(TAG, "Cannot open input stream for $fileUri")
                    return@launch
                }

                val totalChunks = calculateTotalChunks(fileSize)

                // Register transfer
                addTransfer(FileTransfer(
                    id = transferId,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    direction = TransferDirection.UPLOAD,
                    status = TransferStatus.IN_PROGRESS,
                    totalChunks = totalChunks,
                    sentChunks = 0
                ))

                // Send file_meta
                val metaPayload = """{"name":"$fileName","size":$fileSize,"mimeType":"$mimeType","totalChunks":$totalChunks,"transferId":"$transferId"}"""
                sendEnvelope(buildEnvelope("file_meta", metaPayload))

                // Send file_chunk messages
                stream.use { sendChunks(it, transferId, totalChunks) }

            } catch (e: Exception) {
                Log.e(TAG, "sendFile failed", e)
            }
        }
    }

    fun onFileAck(envelope: SignalEnvelope) {
        try {
            val obj        = json.parseToJsonElement(envelope.payload).jsonObject
            val transferId = obj["transferId"]!!.jsonPrimitive.content
            val status     = obj["status"]!!.jsonPrimitive.content

            val newStatus = if (status == "ok") TransferStatus.COMPLETED else TransferStatus.FAILED
            updateTransferStatus(transferId, newStatus)

            Log.i(TAG, "file_ack for $transferId: $status")

        } catch (e: Exception) {
            Log.e(TAG, "onFileAck parse error", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun assembleAndSave(transferId: String, buffer: ReceiveBuffer) {
        scope.launch {
            try {
                // Defensive: assemble in index order regardless of arrival order
                val assembled = buffer.chunks.values.fold(ByteArray(0)) { acc, chunk -> acc + chunk }

                val transfer = _transfers.value.find { it.id == transferId } ?: return@launch
                saveToDownloads(transfer.fileName, transfer.mimeType, assembled)

                receiveBuffers.remove(transferId)
                updateTransferStatus(transferId, TransferStatus.COMPLETED)
                updateProgress(transferId, buffer.totalChunks, buffer.totalChunks)

                // Send ack back
                val ackPayload = """{"transferId":"$transferId","status":"ok"}"""
                sendEnvelope(buildEnvelope("file_ack", ackPayload))

                Log.i(TAG, "File assembled and saved: ${transfer.fileName}")

            } catch (e: Exception) {
                Log.e(TAG, "assembleAndSave failed for $transferId", e)
                val ackPayload = """{"transferId":"$transferId","status":"error"}"""
                sendEnvelope(buildEnvelope("file_ack", ackPayload))
                updateTransferStatus(transferId, TransferStatus.FAILED)
            }
        }
    }



    private fun sendChunks(stream: InputStream, transferId: String, totalChunks: Int) {
        val buffer = ByteArray(CHUNK_SIZE)
        var index  = 0
        var bytesRead: Int

        while (stream.read(buffer).also { bytesRead = it } != -1) {
            val chunk    = buffer.copyOf(bytesRead)
            val encoded  = Base64.encodeToString(chunk, Base64.NO_WRAP)
            val isLast   = (index == totalChunks - 1)

            val chunkPayload = """{"transferId":"$transferId","index":$index,"data":"$encoded","last":$isLast}"""
            sendEnvelope(buildEnvelope("file_chunk", chunkPayload))

            updateProgress(transferId, index + 1, totalChunks)
            updateSentChunks(transferId, index + 1)
            index++
        }
    }


    private fun buildEnvelope(type: String, payload: String) = SignalEnvelope(
        from    = selfPeerId,
        to      = remotePeerId,
        type    = type,
        payload = payload
    )

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }


    private fun resolveFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx != -1) return cursor.getLong(idx)
            }
        }
        return 0L
    }

    private fun calculateTotalChunks(fileSize: Long): Int =
        ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)

    private fun addTransfer(transfer: FileTransfer) {
        _transfers.value = listOf(transfer) + _transfers.value
    }

    private fun updateTransferStatus(transferId: String, status: TransferStatus) {
        _transfers.value = _transfers.value.map {
            if (it.id == transferId) it.copy(status = status) else it
        }
    }

    private fun updateSentChunks(transferId: String, sent: Int) {
        _transfers.value = _transfers.value.map {
            if (it.id == transferId) it.copy(sentChunks = sent.toLong()) else it
        }
    }

    private fun updateProgress(transferId: String, done: Int, total: Int) {
        val fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
        _progress.value = _progress.value + (transferId to fraction)
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOADS_SUBFOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri    = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("MediaStore insert returned null")

        resolver.openOutputStream(itemUri)?.use { it.write(bytes) }

        contentValues.clear()
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, contentValues, null, null)
    }

    // ── Receive ────────────────────────────────────────────────────────────────

    fun onFileMeta(envelope: SignalEnvelope) {
        try {
            val obj         = json.parseToJsonElement(envelope.payload).jsonObject
            val transferId  = obj["transferId"]!!.jsonPrimitive.content
            val name        = obj["name"]!!.jsonPrimitive.content
            val size        = obj["size"]!!.jsonPrimitive.content.toLong()
            val mimeType    = obj["mimeType"]!!.jsonPrimitive.content
            val totalChunks = obj["totalChunks"]!!.jsonPrimitive.content.toInt()

            // Register the incoming transfer in the UI list
            addTransfer(
                FileTransfer(
                    id          = transferId,
                    fileName    = name,
                    fileSize    = size,
                    mimeType    = mimeType,
                    direction   = TransferDirection.DOWNLOAD,
                    status      = TransferStatus.IN_PROGRESS,
                    totalChunks = totalChunks,
                    sentChunks  = 0
                )
            )

            // Allocate an empty buffer ready to receive chunks
            receiveBuffers[transferId] = ReceiveBuffer(
                totalChunks = totalChunks,
                chunks      = java.util.TreeMap()
            )

            updateProgress(transferId, 0, totalChunks)
            Log.i(TAG, "Receiving '$name' ($totalChunks chunks) transferId=$transferId")

        } catch (e: Exception) {
            Log.e(TAG, "onFileMeta parse error", e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    fun onFileChunk(envelope: SignalEnvelope) {
        try {
            val obj        = json.parseToJsonElement(envelope.payload).jsonObject
            val transferId = obj["transferId"]!!.jsonPrimitive.content
            val index      = obj["index"]!!.jsonPrimitive.content.toInt()
            val data       = obj["data"]!!.jsonPrimitive.content
            val isLast     = obj["last"]!!.jsonPrimitive.content.toBooleanStrict()

            val buffer = receiveBuffers[transferId] ?: run {
                Log.w(TAG, "onFileChunk: no buffer for transferId=$transferId — meta missing?")
                return
            }

            // Decode base64 chunk and store it at the correct index
            buffer.chunks[index] = Base64.decode(data, Base64.NO_WRAP)
            updateProgress(transferId, buffer.chunks.size, buffer.totalChunks)
            updateSentChunks(transferId, buffer.chunks.size)

            Log.d(TAG, "Chunk $index/${buffer.totalChunks - 1} received for $transferId")

            // When the last chunk arrives AND all chunks are present, assemble
            if (isLast && buffer.chunks.size == buffer.totalChunks) {
                Log.i(TAG, "All chunks received for $transferId — assembling")
                assembleAndSave(transferId, buffer)
            }

        } catch (e: Exception) {
            Log.e(TAG, "onFileChunk parse error", e)
        }
    }

    private data class ReceiveBuffer(
        val totalChunks: Int,
        val chunks: java.util.TreeMap<Int, ByteArray>
    )


}