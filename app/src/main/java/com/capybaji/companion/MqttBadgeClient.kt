package com.capybaji.companion

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttWebSocketConfig
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 适配 EMQX ACL 规则 (chat/inbox/${username})，支持动态切换 1883 TCP 与 8084 WSS
 */
class MqttBadgeClient {
    private val json = Json { ignoreUnknownKeys = true }
    private var client: Mqtt3AsyncClient? = null
    private var currentSettings: MqttSettings = MqttSettings()

    val inboxTopic: String get() = currentSettings.inboxTopic

    private fun buildInsecureTrustManagerFactory(): TrustManagerFactory {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        return object : TrustManagerFactory(
            object : javax.net.ssl.TrustManagerFactorySpi() {
                override fun engineInit(ks: java.security.KeyStore?) {}
                override fun engineInit(spec: javax.net.ssl.ManagerFactoryParameters?) {}
                override fun engineGetTrustManagers(): Array<TrustManager> = trustAll
            },
            null,
            "Insecure"
        ) {}
    }

    fun connect(
        settings: MqttSettings,
        onConnected: () -> Unit,
        onStatusReceived: (DeviceStatusPayload) -> Unit,
        onError: (String) -> Unit
    ) {
        disconnect()
        currentSettings = settings

        val builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier("android_capy_" + UUID.randomUUID().toString().take(8))
            .serverHost(settings.host.trim())
            .serverPort(settings.port)
            .automaticReconnectWithDefaultConfig()

        if (settings.useWss) {
            val path = settings.wsPath.trim().ifEmpty { "/mqtt" }
                .let { if (it.startsWith("/")) it else "/$it" }
            val wsConfig = MqttWebSocketConfig.builder()
                .serverPath(path)
                .subprotocol("mqtt")
                .build()
            builder.webSocketConfig(wsConfig)

            if (settings.allowInsecureTls) {
                val sslConfig = MqttClientSslConfig.builder()
                    .trustManagerFactory(buildInsecureTrustManagerFactory())
                    .hostnameVerifier { _, _ -> true }
                    .build()
                builder.sslConfig(sslConfig)
            } else {
                builder.sslWithDefaultConfig()
            }
        }

        val mqttClient = builder.buildAsync()
        this.client = mqttClient

        val connectBuilder = mqttClient.connectWith().cleanSession(true)
        if (settings.username.isNotBlank()) {
            val authBuilder = connectBuilder.simpleAuth().username(settings.username.trim())
            if (settings.password.isNotBlank()) {
                authBuilder.password(settings.password.toByteArray(StandardCharsets.UTF_8))
            }
            authBuilder.applySimpleAuth()
        }

        connectBuilder.send().whenComplete { _, throwable ->
            if (throwable != null) {
                val msg = throwable.cause?.message ?: throwable.message ?: "连接失败"
                onError(msg)
                return@whenComplete
            }
            onConnected()
            subscribeInbox(settings.inboxTopic, onStatusReceived)
        }
    }

    private fun subscribeInbox(topic: String, onStatusReceived: (DeviceStatusPayload) -> Unit) {
        client?.subscribeWith()
            ?.topicFilter(topic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val raw = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                runCatching {
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val type = obj["type"]?.jsonPrimitive?.content
                    if (type == "status") {
                        json.decodeFromString<DeviceStatusPayload>(raw)
                    } else {
                        null
                    }
                }.onSuccess { status ->
                    if (status != null) onStatusReceived(status)
                }
            }
            ?.send()
    }

    fun publishMoodAndNote(
        mood: CapyMood,
        noteText: String,
        senderName: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val payloadObj = MoodPayload(
            type = "mood",
            mood = mood.id,
            text = noteText.trim(),
            sender = senderName.ifBlank { "安卓伙伴" },
            msgId = "msg_" + System.currentTimeMillis(),
            timestamp = System.currentTimeMillis()
        )
        val payloadStr = json.encodeToString(payloadObj)

        client?.publishWith()
            ?.topic(inboxTopic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.retain(true)
            ?.payload(payloadStr.toByteArray(StandardCharsets.UTF_8))
            ?.send()
            ?.whenComplete { _, err ->
                if (err == null) {
                    onComplete(true, payloadObj.msgId)
                } else {
                    onComplete(false, err.cause?.message ?: err.message ?: "发送失败")
                }
            }
    }

    fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
    }
}
