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
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Manages the WebSocket connection to the desktop server.
 *
 * - Connects to wss://<tunnelUrl>/signal?peerId=<peerId>
 * - Auto-reconnects with exponential backoff, retrying up to MAX_RECONNECT_ATTEMPTS
 *   to allow time for Cloudflare DNS propagation on new tunnel URLs
 * - Uses a no-cache DNS resolver so stale negative DNS results don't block retries
 * - Emits received [SignalEnvelope]s on [incomingMessages]
 * - Permissive SSL trust is gated behind [BuildConfig.DEBUG] — never ships enabled
 */
class WebSocketManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        private const val TAG = "WSM"
        private const val PATH = "/signal"
        private const val PARAM_PEER_ID = "peerId"

        // Retry for ~60 s total to allow Cloudflare DNS to propagate
        private const val MAX_RECONNECT_ATTEMPTS = 20
        private const val RECONNECT_INTERVAL_MS  = 3_000L

        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S    = 0L
        private const val WRITE_TIMEOUT_S   = 15L
        private const val PING_INTERVAL_S   = 20L
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<SignalEnvelope>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val incomingMessages: SharedFlow<SignalEnvelope> = _incomingMessages.asSharedFlow()

    // ── Internals ─────────────────────────────────────────────────────────────

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

    fun connect(tunnelUrl: String, peerId: String) {
        if (tunnelUrl.isBlank() || peerId.isBlank()) {
            Log.e(TAG, "connect() aborted — tunnelUrl or peerId is blank")
            return
        }

        isIntentionallyStopped = false
        currentUrl    = tunnelUrl
        currentPeerId = peerId
        reconnectAttempts = 0

        doConnect(tunnelUrl, peerId)
    }

    fun send(envelope: SignalEnvelope): Boolean {
        val ws = webSocket
        return if (ws != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
            val payload = json.encodeToString(envelope)
            val sent = ws.send(payload)
            if (!sent) Log.w(TAG, "send() — ws.send() returned false (queue full or closing)")
            sent
        } else {
            Log.w(TAG, "send() skipped — not connected")
            false
        }
    }

    fun disconnect() {
        isIntentionallyStopped = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun reconnectIfNeeded() {
        val url    = currentUrl    ?: return
        val peerId = currentPeerId ?: return
        if (_connectionStatus.value != ConnectionStatus.CONNECTED && !isIntentionallyStopped) {
            doConnect(url, peerId)
        }
    }

    // ── Internal connection logic ─────────────────────────────────────────────

    private fun buildWsUrl(tunnelUrl: String, peerId: String): String {
        val base = tunnelUrl
            .replace("https://", "wss://")
            .replace("http://",  "ws://")
            .trimEnd('/')
        return "$base$PATH?$PARAM_PEER_ID=${Uri.encode(peerId)}"
    }

    private fun doConnect(tunnelUrl: String, peerId: String) {
        val wsUrl = buildWsUrl(tunnelUrl, peerId)

        _connectionStatus.value = ConnectionStatus.CONNECTING

        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (e: Exception) {
            Log.e(TAG, "doConnect() — malformed URL: $wsUrl", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            scheduleReconnect(tunnelUrl, peerId)
            return
        }

        webSocket = okHttpClient.newWebSocket(request, createListener(tunnelUrl, peerId))
    }

    private fun createListener(tunnelUrl: String, peerId: String) = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            this@WebSocketManager.webSocket = webSocket
            _connectionStatus.value = ConnectionStatus.CONNECTED
            reconnectAttempts = 0
            Log.i(TAG, "Connected (peerId=$peerId)")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val envelope = json.decodeFromString<SignalEnvelope>(text)
                scope.launch { _incomingMessages.emit(envelope) }
            } catch (e: Exception) {
                Log.e(TAG, "JSON parse failed: $text", e)
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // Binary frames not used by this protocol
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            if (!isIntentionallyStopped) scheduleReconnect(tunnelUrl, peerId)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "onFailure() — ${t.javaClass.simpleName}: ${t.message}")
            _connectionStatus.value = ConnectionStatus.ERROR
            if (!isIntentionallyStopped) scheduleReconnect(tunnelUrl, peerId)
        }
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private fun scheduleReconnect(tunnelUrl: String, peerId: String) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "scheduleReconnect() — giving up after $MAX_RECONNECT_ATTEMPTS attempts")
            _connectionStatus.value = ConnectionStatus.ERROR
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Log.i(TAG, "Reconnecting in ${RECONNECT_INTERVAL_MS}ms (attempt ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
            delay(RECONNECT_INTERVAL_MS)
            if (!isIntentionallyStopped && isActive) {
                reconnectAttempts++
                doConnect(tunnelUrl, peerId)
            }
        }
    }

    // ── OkHttpClient ──────────────────────────────────────────────────────────

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_S, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            // Bypass Android's aggressive negative-DNS cache so each retry
            // does a fresh lookup — essential for new Cloudflare tunnel URLs
            // whose DNS hasn't propagated yet.
            .dns(FreshDns)

        if (BuildConfig.DEBUG) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, trustAllCerts, SecureRandom())
                }
                builder
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install debug SSL", e)
            }
        }

        return builder.build()
    }

    fun teardown() {
        disconnect()
        scope.cancel()
        okHttpClient.dispatcher.executorService.shutdown()
    }

    // ── Fresh DNS resolver ────────────────────────────────────────────────────

    /**
     * Clears the JVM's DNS cache before each lookup so that a previously
     * failed resolution (EAI_NODATA) doesn't block subsequent retry attempts
     * while Cloudflare propagates the new tunnel subdomain.
     */
    private object FreshDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // TTL=0 tells the JVM not to cache the result (positive or negative)
            Security.setProperty("networkaddress.cache.ttl",          "0")
            Security.setProperty("networkaddress.cache.negative.ttl", "0")
            return try {
                InetAddress.getAllByName(hostname).toList()
            } catch (e: UnknownHostException) {
                throw e  // let OkHttp handle it; scheduleReconnect will retry
            }
        }
    }
}

private object Uri {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")
}