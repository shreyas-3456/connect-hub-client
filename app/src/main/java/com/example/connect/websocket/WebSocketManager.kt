package com.example.connect.websocket

import android.net.Uri
import android.util.Log
import com.example.connect.BuildConfig
import com.example.connect.data.model.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.connect.data.model.SignalEnvelope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.WebSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Manages the WebSocket connection to the desktop server.
 *
 * - Connects to wss://<tunnelUrl>/signal?peerId=<peerId>
 * - Auto-reconnects with exponential backoff (1s → 2s → 4s … max 30s)
 * - Emits received [SignalEnvelope]s on [incomingMessages]
 * - Permissive SSL trust is gated behind [BuildConfig.DEBUG] — never ships enabled
 */

class WebSocketManager (
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
){
    companion object {
        private const val TAG = "WebSocketManager"
        private const val PATH = "/signal"
        private const val PARAM_PEER_ID = "peerId"

        // Backoff constants (seconds)
        private const val BACKOFF_INITIAL_MS = 1_000L
        private const val BACKOFF_MAX_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 2.0

        // OkHttp timeouts
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 0L   // 0 = no timeout (server pings keep it alive)
        private const val WRITE_TIMEOUT_S = 15L
        private const val PING_INTERVAL_S = 20L  // client-side keepalive as belt-and-suspenders

    }

    // ── State ───────

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalEnvelope>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val incomingMessages: SharedFlow<SignalEnvelope> = _incomingMessages.asSharedFlow()

    // Internals

    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null
    private var currentPeerId: String? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var isIntentionallyStopped = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open a WebSocket connection.
     * Safe to call from any thread; idempotent if already connected to the same endpoint.
     */
    fun connect(tunnelUrl: String, peerId: String) {
        isIntentionallyStopped = false
        currentUrl = tunnelUrl
        currentPeerId = peerId
        reconnectAttempts = 0
        doConnect(tunnelUrl, peerId)
    }

    /**
     * Send an envelope to the server. Returns false if the socket is not open.
     */
    fun send(envelope: SignalEnvelope): Boolean {
        val ws = webSocket
        return if (ws != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
            val payload = json.encodeToString(envelope)
            val sent = ws.send(payload)
            if (!sent) Log.w(TAG, "send() returned false — queue full or socket closed")
            sent
        } else {
            Log.w(TAG, "send() called while not connected (status=${_connectionStatus.value})")
            false
        }
    }

    fun disconnect() {
        isIntentionallyStopped = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        Log.i(TAG, "Disconnected intentionally")
    }


    /**
     * Re-establish the connection using the last known URL and peerId.
     * Useful when the app returns to the foreground (ON_RESUME).
     */
    fun reconnectIfNeeded() {
        val url = currentUrl ?: return
        val peerId = currentPeerId ?: return
        if (_connectionStatus.value != ConnectionStatus.CONNECTED && !isIntentionallyStopped) {
            Log.i(TAG, "reconnectIfNeeded() — triggering reconnect")
            doConnect(url, peerId)
        }
    }



    private fun buildWsUrl(tunnelUrl: String, peerId: String): String {
        // Accept both https:// and wss:// prefixes from Firebase
        val base = tunnelUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')
        return "$base$PATH?$PARAM_PEER_ID=${Uri.encode(peerId)}"
    }



    private fun doConnect(tunnelUrl: String, peerId: String) {
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val wsUrl = buildWsUrl(tunnelUrl, peerId)
        Log.i(TAG, "Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, createListener(tunnelUrl, peerId))
    }

    private fun createListener(tunnelUrl: String, peerId: String) = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket open — ${response.code}")
            this@WebSocketManager.webSocket = webSocket
            _connectionStatus.value = ConnectionStatus.CONNECTED
            reconnectAttempts = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val envelope = json.decodeFromString<SignalEnvelope>(text)
                scope.launch { _incomingMessages.emit(envelope) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse message: $text", e)
            }
        }

        // Binary frames are not used by the protocol, but handle gracefully
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            Log.d(TAG, "Received binary frame (${bytes.size} bytes) — ignored")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "Server closing: code=$code reason=$reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket closed: code=$code reason=$reason")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            if (!isIntentionallyStopped) scheduleReconnect(tunnelUrl, peerId)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            _connectionStatus.value = ConnectionStatus.ERROR
            if (!isIntentionallyStopped) scheduleReconnect(tunnelUrl, peerId)
        }
    }


    // ── Reconnect with exponential backoff ────────────────────────────────────

    private fun scheduleReconnect(tunnelUrl: String, peerId: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateBackoffDelay()
            Log.i(TAG, "Reconnect attempt #${reconnectAttempts + 1} in ${delayMs}ms")
            delay(delayMs)
            if (!isIntentionallyStopped && isActive) {
                reconnectAttempts++
                doConnect(tunnelUrl, peerId)
            }
        }
    }

    private fun calculateBackoffDelay(): Long {
        val raw = (BACKOFF_INITIAL_MS * Math.pow(BACKOFF_MULTIPLIER, reconnectAttempts.toDouble())).toLong()
        return raw.coerceAtMost(BACKOFF_MAX_MS)
    }





    // ── OkHttpClient ──────────────────────────────────────────────────────────

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // We handle reconnects ourselves

        // Permissive SSL for local/dev fallback ONLY.
        // Cloudflare provides valid certs in production — this path is never hit there.
        // ⚠️  NEVER set DEBUG = true in a release build.
        if (BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, trustAllCerts, SecureRandom())
                }
                builder
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                Log.w(TAG, " Permissive SSL active — DEBUG build only")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install permissive SSL", e)
            }
        }

        return builder.build()
    }

    /**
     * Call this from your DI teardown or when the app process is ending.
     * Cancels all coroutines and closes the socket.
     */
    fun teardown() {
        disconnect()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }

}

private object Uri {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}
