package com.example.connect.data.model

import kotlinx.serialization.Serializable

// @Serializable tells the Kotlin serialization library how to
// automatically convert this class to/from JSON strings.
// Every field maps directly to a JSON key.
@Serializable
data class SignalEnvelope(
    val from: String,       // who sent it  e.g. "phone-abc123"
    val to: String,         // who it's for e.g. "my-laptop"
    val type: String,       // "file_meta" | "file_chunk" | "file_ack" | "notification" | "ping" | "pong"
    val payload: String     // raw JSON string — we parse this separately based on type
)

