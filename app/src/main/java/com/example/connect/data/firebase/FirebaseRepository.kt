package com.example.connect.data.firebase

import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseRepository {

    private val devicesRef = Firebase.database.getReference("devices")

    suspend fun getDeviceUrl(deviceId: String): String {
        return suspendCancellableCoroutine { continuation ->
            devicesRef
                .child(deviceId)
                .child("url")
                .get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.getValue(String::class.java)
                    if (url != null) {
                        continuation.resume(url) {}
                    } else {
                        continuation.cancel(
                            Exception("Device '$deviceId' not found in Firebase")
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.cancel(exception)
                }
        }
    }

    suspend fun isDeviceOnline(deviceId: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            devicesRef
                .child(deviceId)
                .child("online")
                .get()
                .addOnSuccessListener { snapshot ->
                    val online = snapshot.getValue(Boolean::class.java) ?: false
                    continuation.resume(online) {}
                }
                .addOnFailureListener {
                    continuation.resume(false) {}
                }
        }
    }
}