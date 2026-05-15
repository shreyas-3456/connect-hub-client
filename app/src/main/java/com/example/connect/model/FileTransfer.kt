package com.example.connect.data.model

import com.example.connect.model.TransferDirection
import com.example.connect.model.TransferStatus

// No @Serializable here — this is a UI state model, never sent over the wire.
data class FileTransfer(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val totalChunks: Int,
    val sentChunks: Long
) {
    // Nested enums keep related constants grouped with their parent class.
    // Direction.SEND means the phone is uploading to the desktop.
    enum class Direction { SEND, RECEIVE }

    enum class Status { PENDING, IN_PROGRESS, COMPLETE, ERROR }

    // A computed property — no () needed when you read it, just transfer.progress
    // It calculates on the fly from the other fields. Safe division guard: if
    // fileSizeBytes is 0 we return 0 instead of crashing with divide-by-zero.
    val progress: Float
        get() = if (fileSize > 0) sentChunks.toFloat() / fileSize else 0f
}