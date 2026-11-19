package com.capybaji.companion

import kotlinx.serialization.Serializable

enum class CapyMood(val id: String, val labelZh: String, val hint: String) {
    CHILL("chill", "佛系顶橘", "情绪稳定慢悠悠"),
    HAPPY("happy", "开心贴贴", "眯眼笑出暖腮红"),
    LOVE("love", "心动比心", "冒出粉红小爱心"),
    SLEEPY("sleepy", "困困打盹", "吹鼻涕泡 Zzz"),
    EXCITED("excited", "元气星星", "两眼放光转圈圈"),
    SAD("sad", "委屈巴巴", "求摸头求安慰"),
    ANGRY("angry", "气鼓鼓", "腮帮鼓鼓冒小火"),
    SHOCKED("shocked", "瞳孔地震", "橘子吓得跳起来")
}

@Serializable
data class MoodPayload(
    val type: String = "mood",
    val mood: String,
    val text: String,
    val sender: String,
    val msgId: String,
    val timestamp: Long,
    val brightness: Int = 220
)

@Serializable
data class DeviceStatusPayload(
    val type: String = "status",
    val deviceId: String,
    val batteryPct: Int = 100,
    val voltageMv: Int = 4150,
    val readMsgId: String? = null,
    val wakeReason: String = "IMU_SHAKE",
    val rssi: Int = -55,
    val powerState: String = "SCREEN_OFF"
)

data class MqttSettings(
    val host: String = "broker.emqx.io",
    val port: Int = 1883,
    val useWss: Boolean = false,
    val wsPath: String = "/mqtt",
    val allowInsecureTls: Boolean = false,
    val username: String = "capy_group01",
    val password: String = "",
    val senderName: String = "阿圆"
) {
    val inboxTopic: String get() = "chat/inbox/$username"
    val protocolSummary: String
        get() = if (useWss) "wss://$host:$port$wsPath" else "mqtt://$host:$port"
}
