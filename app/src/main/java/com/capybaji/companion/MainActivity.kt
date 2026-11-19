package com.capybaji.companion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private fun loadMqttSettings(ctx: Context): MqttSettings {
    val prefs = ctx.getSharedPreferences("capybaji_prefs", Context.MODE_PRIVATE)
    val def = MqttSettings()
    return MqttSettings(
        host = prefs.getString("host", def.host) ?: def.host,
        port = prefs.getInt("port", def.port),
        useWss = prefs.getBoolean("useWss", def.useWss),
        wsPath = prefs.getString("wsPath", def.wsPath) ?: def.wsPath,
        allowInsecureTls = prefs.getBoolean("allowInsecureTls", def.allowInsecureTls),
        username = prefs.getString("username", def.username) ?: def.username,
        password = prefs.getString("password", def.password) ?: def.password,
        senderName = prefs.getString("senderName", def.senderName) ?: def.senderName
    )
}

private fun saveMqttSettings(ctx: Context, s: MqttSettings) {
    ctx.getSharedPreferences("capybaji_prefs", Context.MODE_PRIVATE).edit()
        .putString("host", s.host.trim())
        .putInt("port", s.port)
        .putBoolean("useWss", s.useWss)
        .putString("wsPath", s.wsPath.trim())
        .putBoolean("allowInsecureTls", s.allowInsecureTls)
        .putString("username", s.username.trim())
        .putString("password", s.password)
        .putString("senderName", s.senderName)
        .apply()
}

class MainActivity : ComponentActivity() {
    private val mqttClient = MqttBadgeClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CapyBajiScreen(mqttClient)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapyBajiScreen(mqttClient: MqttBadgeClient) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(loadMqttSettings(context)) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var connectTrigger by remember { mutableIntStateOf(0) }

    var selectedMood by remember { mutableStateOf(CapyMood.CHILL) }
    var noteText by remember { mutableStateOf("") }
    var senderName by remember { mutableStateOf(settings.senderName) }
    var isConnected by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("正在连接 ${settings.protocolSummary}...") }
    var badgeStatus by remember { mutableStateOf<DeviceStatusPayload?>(null) }
    var lastSentMsgId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectTrigger) {
        isConnected = false
        statusText = "正在连接 ${settings.protocolSummary}..."
        mqttClient.connect(
            settings = settings,
            onConnected = {
                isConnected = true
                statusText = "已连接 ${settings.protocolSummary} · 订阅 ${settings.inboxTopic}"
            },
            onStatusReceived = { status ->
                badgeStatus = status
            },
            onError = { err ->
                isConnected = false
                statusText = "连接失败 (${settings.protocolSummary}): $err"
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡皮吧唧 · 心情遥控端", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showSettingsDialog = true }) {
                        Text("MQTT配置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    badgeStatus?.let { st ->
                        val isRead = (lastSentMsgId != null && st.readMsgId == lastSentMsgId)
                        Text(
                            "电量: ${st.batteryPct}% (${st.voltageMv}mV) · 状态: ${st.powerState}" +
                                if (isRead) " · 对方已查看小纸条" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text("1. 选择要传给吧唧的卡皮巴拉心情", fontWeight = FontWeight.SemiBold)

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(220.dp)
            ) {
                items(CapyMood.entries) { mood ->
                    val active = mood == selectedMood
                    FilledTonalButton(
                        onClick = { selectedMood = mood },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column {
                            Text(mood.labelZh, fontWeight = FontWeight.Bold)
                            Text(mood.hint, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Text("2. 附带一段小纸条文字（吧唧按 BOOT 或触屏查看）", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = senderName,
                onValueChange = { senderName = it },
                label = { Text("署名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("输入想对 TA 说的话...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    mqttClient.publishMoodAndNote(selectedMood, noteText, senderName) { ok, info ->
                        if (ok) {
                            lastSentMsgId = info
                            statusText = "已投递到 ${mqttClient.inboxTopic}"
                        } else {
                            statusText = "发送失败: $info"
                        }
                    }
                },
                enabled = isConnected,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("同步心情与小纸条到卡皮吧唧")
            }
        }
    }

    if (showSettingsDialog) {
        var hostInput by remember(showSettingsDialog) { mutableStateOf(settings.host) }
        var portInput by remember(showSettingsDialog) { mutableStateOf(settings.port.toString()) }
        var useWssInput by remember(showSettingsDialog) { mutableStateOf(settings.useWss) }
        var wsPathInput by remember(showSettingsDialog) { mutableStateOf(settings.wsPath) }
        var allowInsecureInput by remember(showSettingsDialog) { mutableStateOf(settings.allowInsecureTls) }
        var userInput by remember(showSettingsDialog) { mutableStateOf(settings.username) }
        var passInput by remember(showSettingsDialog) { mutableStateOf(settings.password) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("EMQX 服务器配置", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("服务器 IP 或域名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("连接协议与端口选择", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !useWssInput,
                            onClick = {
                                useWssInput = false
                                portInput = "1883"
                            },
                            label = { Text("1883 TCP") }
                        )
                        FilterChip(
                            selected = useWssInput,
                            onClick = {
                                useWssInput = true
                                portInput = "8084"
                            },
                            label = { Text("8084 WSS") }
                        )
                    }

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("端口号 (1883 或 8084)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (useWssInput) {
                        OutlinedTextField(
                            value = wsPathInput,
                            onValueChange = { wsPathInput = it },
                            label = { Text("WSS 路径 (EMQX 默认 /mqtt)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("跳过 SSL 校验(测自签名8084)", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = allowInsecureInput,
                                onCheckedChange = { allowInsecureInput = it }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("EMQX Username (组收件箱)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("EMQX Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedPort = portInput.toIntOrNull() ?: if (useWssInput) 8084 else 1883
                    val updated = MqttSettings(
                        host = hostInput.trim().ifEmpty { settings.host },
                        port = parsedPort,
                        useWss = useWssInput,
                        wsPath = wsPathInput.trim().ifEmpty { "/mqtt" },
                        allowInsecureTls = allowInsecureInput,
                        username = userInput.trim(),
                        password = passInput,
                        senderName = senderName
                    )
                    settings = updated
                    saveMqttSettings(context, updated)
                    showSettingsDialog = false
                    connectTrigger++
                }) {
                    Text("保存并重连")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
