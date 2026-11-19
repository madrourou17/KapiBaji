package com.capybaji.companion

import kotlinx.serialization.Serializable

enum class CapyMood(val id: String, val labelZh: String, val hint: String) {
    NEUTRAL("neutral", "平静待机", "默认眨眼"),
    HAPPY("happy", "开心", "弯眼微笑"),
    LAUGHING("laughing", "大笑", "哈哈大笑"),
    FUNNY("funny", "搞怪", "大小眼整活"),
    SAD("sad", "难过", "委屈低落"),
    ANGRY("angry", "生气", "斜眉炸毛"),
    CRYING("crying", "哭哭", "泪眼汪汪"),
    LOVING("loving", "比心", "心动爱心"),
    EMBARRASSED("embarrassed", "害羞", "脸红羞涩"),
    SURPRISED("surprised", "惊讶", "哇塞圆眼"),
    SHOCKED("shocked", "震惊", "瞳孔地震"),
    THINKING("thinking", "思考", "眼珠转动"),
    WINKING("winking", "眨眼", "俏皮 Wink"),
    COOL("cool", "酷酷", "墨镜大佬"),
    RELAXED("relaxed", "悠闲", "惬意半眯"),
    DELICIOUS("delicious", "馋嘴", "吐舌干饭"),
    KISSY("kissy", "飞吻", "嘟嘴飞吻"),
    CONFIDENT("confident", "自信", "得意挑眉"),
    SLEEPY("sleepy", "困倦", "垂眼打盹"),
    SILLY("silly", "呆萌", "斗鸡眼呆萌"),
    CONFUSED("confused", "疑惑", "满头问号")
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
