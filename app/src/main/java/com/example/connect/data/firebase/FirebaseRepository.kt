package com.example.connect.data.firebase

import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.suspendCancellableCoroutine

data class DeviceInfo(
    val deviceId: String,
    val url: String,
    val lastSeen: Long = 0L
)

class FirebaseRepository {

    private val devicesRef = Firebase.database.getReference("devices")

    suspend fun getDeviceUrl(deviceId: String): String? {
        return suspendCancellableCoroutine { continuation ->
            devicesRef
                .child(deviceId)
                .child("url")
                .get()
                .addOnSuccessListener { snapshot ->
                    val url = snapshot.getValue(String::class.java)
                    continuation.resume(url) {}
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

    /**
     * Returns all devices currently marked online=true in Firebase,
     * sorted by lastSeen descending (most recently seen first).
     */
    suspend fun getOnlineDevices(): List<DeviceInfo> {
        return suspendCancellableCoroutine { continuation ->
            devicesRef
                .get()
                .addOnSuccessListener { snapshot ->
                    val devices = mutableListOf<DeviceInfo>()
                    for (child in snapshot.children) {
                        val deviceId = child.key ?: continue
                        val online   = child.child("online").getValue(Boolean::class.java) ?: false
                        val url      = child.child("url").getValue(String::class.java) ?: continue
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        if (online) {
                            devices.add(DeviceInfo(deviceId, url, lastSeen))
                        }
                    }
                    devices.sortByDescending { it.lastSeen }
                    continuation.resume(devices) {}
                }
                .addOnFailureListener { exception ->
                    continuation.cancel(exception)
                }
        }
    }
}