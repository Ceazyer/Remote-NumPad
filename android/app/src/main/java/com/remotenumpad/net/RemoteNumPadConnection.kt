package com.remotenumpad.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.remotenumpad.queue.CommandQueueEngine
import com.remotenumpad.queue.CommandStore
import com.remotenumpad.queue.CommandTransport
import com.remotenumpad.queue.QueueSnapshot
import com.remotenumpad.queue.QueueStatus
import com.remotenumpad.queue.QueuedCommand
import com.remotenumpad.queue.EnqueueResult
import com.remotenumpad.queue.SQLiteCommandStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RemoteNumPadConnection(
    context: Context,
    private val onState: (QueueSnapshot, String) -> Unit,
    private val onStorageError: (String) -> Unit,
    private val onConnected: (ConnectionEndpoint) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "remote-numpad-queue").apply { isDaemon = true }
    }
    private val reconnectScheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "remote-numpad-reconnect").apply { isDaemon = true }
    }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val store: CommandStore = SQLiteCommandStore(appContext)

    private var engine: CommandQueueEngine? = null
    private var webSocket: WebSocket? = null
    private var host: String? = null
    private var port: Int = DEFAULT_PORT
    private var connectionMessage: String = "未连接"
    private var generation: Long = 0
    private var reconnectAttempt = 0
    private var started = false
    @Volatile private var disposed = false

    fun start(host: String?, port: Int) {
        worker.execute {
            if (disposed) return@execute
            ensureEngine()
            started = true
            val nextHost = host?.trim()?.takeIf { PrivateIpv4Validator.isAllowed(it) }
            val nextPort = port.takeIf { it in 1..65535 } ?: DEFAULT_PORT
            if (this.host != nextHost || this.port != nextPort) {
                generation++
                webSocket?.cancel()
                webSocket = null
                engine?.onDisconnected()
            }
            this.host = nextHost
            this.port = nextPort

            if (this.host == null) {
                connectionMessage = if (host.isNullOrBlank()) "请设置电脑局域网地址" else "主机地址无效"
                publish()
                return@execute
            }

            connectionMessage = "正在连接电脑"
            connectOnWorker()
        }
    }

    fun enqueue(command: String, onAccepted: () -> Unit = {}) {
        worker.execute {
            if (disposed) return@execute
            val queue = ensureEngine()
            queue.noteClick()
            try {
                if (queue.enqueue(command) == EnqueueResult.ADDED) mainHandler.post(onAccepted)
            } catch (_: Exception) {
                notifyStorageError("queue_write_failed")
                publish()
            }
        }
    }

    fun hasPending(callback: (Boolean) -> Unit) {
        worker.execute {
            val pending = try { store.pending().isNotEmpty() } catch (_: Exception) { true }
            mainHandler.post { if (!disposed) callback(pending) }
        }
    }

    fun resumeAfterReview() = worker.execute { engine?.resumeAfterReview() }

    fun retryFailedCurrent() = worker.execute { engine?.retryFailedCurrent() }

    fun skipUncertainAfterManualCheck() = worker.execute { engine?.skipUncertainAfterManualCheck() }

    fun clearPending() = worker.execute { engine?.clearPending() }

    fun stop() {
        worker.execute {
            if (disposed || !started) return@execute
            started = false
            generation++
            webSocket?.cancel()
            webSocket = null
            engine?.onDisconnected()
            connectionMessage = "后台已暂停连接"
            publish()
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        worker.execute {
            started = false
            generation++
            webSocket?.cancel()
            webSocket = null
            engine?.onDisconnected()
        }
        reconnectScheduler.shutdownNow()
        worker.shutdown()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun ensureEngine(): CommandQueueEngine {
        engine?.let { return it }
        val transport = CommandTransport { clientId, command ->
            val socket = webSocket ?: return@CommandTransport false
            try {
                socket.send(
                    JSONObject()
                        .put("v", PROTOCOL_VERSION)
                        .put("type", "command")
                        .put("clientId", clientId)
                        .put("seq", command.sequence)
                        .put("command", command.command)
                        .toString()
                )
            } catch (_: JSONException) {
                false
            }
        }
        return CommandQueueEngine(store, transport, observer = { snapshot -> publish(snapshot) })
            .also { engine = it }
    }

    private fun connectOnWorker() {
        if (!started || disposed || webSocket != null) return
        val endpoint = host ?: return
        val currentGeneration = ++generation
        val clientId = try {
            store.clientId
        } catch (_: Exception) {
            connectionMessage = "本地队列不可用"
            notifyStorageError("queue_read_failed")
            publish()
            return
        }

        val request = Request.Builder().url("ws://$endpoint:$port/ws").build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val socket = webSocket
                worker.execute {
                    if (!isCurrent(socket, currentGeneration)) {
                        socket.cancel()
                        return@execute
                    }
                    reconnectAttempt = 0
                    connectionMessage = "连接已建立，正在校验服务"
                    val hello = JSONObject()
                        .put("v", PROTOCOL_VERSION)
                        .put("type", "hello")
                        .put("clientId", clientId)
                        .toString()
                    if (!socket.send(hello)) {
                        handleDisconnect(socket, currentGeneration)
                    } else {
                        publish()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                worker.execute { handleIncoming(webSocket, currentGeneration, text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                worker.execute {
                    if (isCurrent(webSocket, currentGeneration)) {
                        engine?.onProtocolError("unexpected_binary_message")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                worker.execute { handleDisconnect(webSocket, currentGeneration) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                worker.execute { handleDisconnect(webSocket, currentGeneration) }
            }
        })
    }

    private fun handleIncoming(socket: WebSocket, socketGeneration: Long, text: String) {
        if (!isCurrent(socket, socketGeneration)) return
        try {
            val message = JSONObject(text)
            if (message.optInt("v", -1) != PROTOCOL_VERSION) {
                engine?.onProtocolError("unsupported_protocol_version")
                connectionMessage = "电脑端协议版本不兼容"
                publish()
                return
            }

            val receivedClientId = message.optString("clientId", "")
            if (receivedClientId != store.clientId) {
                engine?.onProtocolError("client_id_mismatch")
                connectionMessage = "服务端响应校验失败"
                publish()
                return
            }

            when (message.optString("type")) {
                "welcome" -> {
                    val serverId = message.optString("serverInstanceId", "")
                    if (serverId.isBlank()) {
                        engine?.onProtocolError("missing_server_instance_id")
                        connectionMessage = "服务端响应不完整"
                    } else {
                        engine?.onWelcome(serverId)
                        connectionMessage = if (engine?.snapshot()?.status == QueueStatus.REVIEW_REQUIRED) {
                            "已连接，待确认旧输入"
                        } else {
                            "已连接"
                        }
                        val connectedEndpoint = host?.let { ConnectionEndpoint(it, port) }
                        if (connectedEndpoint != null) mainHandler.post { if (!disposed) onConnected(connectedEndpoint) }
                    }
                }
                "ack" -> {
                    val sequenceValue = message.opt("seq")
                    if (sequenceValue !is Number || !message.has("result")) {
                        engine?.onProtocolError("malformed_ack")
                        connectionMessage = "服务端确认消息无效"
                    } else {
                        val error = message.optString("error", "").takeIf { it.isNotBlank() && it != "null" }
                        engine?.onAck(sequenceValue.toLong(), message.optString("result"), error)
                        if (engine?.snapshot()?.status == QueueStatus.SEND_FAILED) {
                            connectionMessage = "键盘注入失败，已暂停"
                        } else if (engine?.snapshot()?.status == QueueStatus.UNCERTAIN) {
                            connectionMessage = "输入结果不确定，请先核对"
                        } else {
                            connectionMessage = "已连接"
                        }
                    }
                }
                "error" -> {
                    val error = message.optString("error", "server_protocol_error")
                    engine?.onProtocolError(error)
                    connectionMessage = "电脑端协议错误，输入已暂停"
                }
                else -> {
                    engine?.onProtocolError("unknown_server_message")
                    connectionMessage = "电脑端响应无法识别"
                }
            }
            publish()
        } catch (_: JSONException) {
            engine?.onProtocolError("invalid_server_json")
            connectionMessage = "电脑端响应无法解析"
            publish()
        }
    }

    private fun handleDisconnect(socket: WebSocket, socketGeneration: Long) {
        if (!isCurrent(socket, socketGeneration)) return
        webSocket = null
        engine?.onDisconnected()
        if (started && !disposed) {
            connectionMessage = "连接断开，正在等待重连"
            publish()
            scheduleReconnect()
        } else {
            connectionMessage = "未连接"
            publish()
        }
    }

    private fun scheduleReconnect() {
        if (!started || disposed || host == null) return
        val delaySeconds = minOf(1L shl reconnectAttempt.coerceAtMost(5), 30L)
        reconnectAttempt++
        reconnectScheduler.schedule({
            worker.execute {
                if (started && !disposed && webSocket == null) {
                    connectionMessage = "正在重新连接电脑"
                    publish()
                    connectOnWorker()
                }
            }
        }, delaySeconds, TimeUnit.SECONDS)
    }

    private fun isCurrent(socket: WebSocket, socketGeneration: Long): Boolean =
        !disposed && started && generation == socketGeneration && webSocket === socket

    private fun publish(snapshot: QueueSnapshot? = engine?.snapshot()) {
        val value = snapshot ?: QueueSnapshot(QueueStatus.OFFLINE, 0, null, 0, 0, 0, 0, null)
        val message = connectionMessage
        mainHandler.post {
            if (!disposed) onState(value, message)
        }
    }

    private fun notifyStorageError(code: String) {
        mainHandler.post {
            if (!disposed) onStorageError(code)
        }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        private const val PROTOCOL_VERSION = 2
    }
}
