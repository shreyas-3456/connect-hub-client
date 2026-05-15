package com.example.connect.model

enum class TransferDirection {
    UPLOAD,    // phone → desktop
    DOWNLOAD   // desktop → phone
}

enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

