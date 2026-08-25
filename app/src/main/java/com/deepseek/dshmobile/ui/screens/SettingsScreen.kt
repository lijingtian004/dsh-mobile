package com.deepseek.dshmobile.ui.screens

import android.content.Intent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.deepseek.dshmobile.service.DshEngineManager
import com.deepseek.dshmobile.service.DshEngineService
import com.deepseek.dshmobile.ui.EngineWebActivity
import com.deepseek.dshmobile.util.EngineSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var engineRunning by remember { mutableStateOf(DshEngineManager.isRunning) }
    var apiKey by remember { mutableStateOf("") }
    var defaultModel by remember { mutableStateOf("deepseek-chat") }
    val models = listOf("deepseek-chat", "deepseek-coder", "deepseek-reasoner")

    LaunchedEffect(Unit) {
        while (true) {
            engineRunning = DshEngineManager.isRunning
            kotlinx.coroutines.delay(1000)
        }
    }

    fun startEngine() {
        val intent = Intent(context, DshEngineService::class.java).setAction(DshEngineService.ACTION_START)
        context.startForegroundService(intent)
        engineRunning = true
    }

    fun stopEngine() {
        val intent = Intent(context, DshEngineService::class.java).setAction(DshEngineService.ACTION_STOP)
        context.startService(intent)
        engineRunning = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 引擎状态
            item {
                EngineStatusCard(
                    isRunning = engineRunning,
                    onStart = { startEngine() },
                    onStop = { stopEngine() }
                )
            }

            // 自定义 API 接入
            item {
                var baseUrl by remember { mutableStateOf("") }
                var modelId by remember { mutableStateOf("") }
                var savedMsg by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    val cfg = EngineSettings.load(context)
                    apiKey = cfg.apiKey
                    baseUrl = cfg.baseUrl
                    modelId = cfg.modelId
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "自定义 API 接入（OpenAI 兼容）",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = modelId,
                        onValueChange = { modelId = it },
                        label = { Text("模型 ID") },
                        placeholder = { Text("例如 deepseek-chat / gpt-4o") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(onClick = {
                            scope.launch {
                                EngineSettings.save(
                                    context,
                                    EngineSettings.EngineConfig(
                                        apiKey = apiKey,
                                        baseUrl = baseUrl,
                                        modelId = modelId
                                    )
                                )
                                savedMsg = "已保存，重启引擎后生效"
                            }
                        }) {
                            Text("保存")
                        }
                        if (savedMsg.isNotEmpty()) {
                            Text(
                                savedMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        "留空则使用 DeepSeek 官方接入。修改后需停止并重新启动引擎。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // 引擎 Web 控制台（dsh 原生 UI：模型设置 / 插件市场 / 会话）
            item {
                SettingsRow(
                    icon = Icons.Default.Language,
                    title = "引擎控制台 (Web UI)",
                    subtitle = "完整 dsh 功能界面",
                    onClick = {
                        context.startActivity(Intent(context, EngineWebActivity::class.java))
                    }
                )
            }

            // 默认模型
            item {
                Text(
                    "默认模型",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                modelSelector(models, defaultModel) { selected ->
                    defaultModel = selected
                }
            }

            // 其他设置
            item {
                Divider()
            }

            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "DeepSeek Harness Android v0.1.0"
                )
            }

            item {
                SettingsRow(
                    icon = Icons.Default.HelpOutline,
                    title = "帮助与反馈"
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun EngineStatusCard(isRunning: Boolean, onStart: () -> Unit = {}, onStop: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (isRunning) "引擎运行中" else "引擎已停止",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isRunning)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    if (isRunning) "端口: 127.0.0.1:3080" else "点击启动",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRunning)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = { if (isRunning) onStop() else onStart() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "停止" else "启动")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun modelSelector(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    ExposedDropdownMenuBox(
        expanded = false,
        onExpandedChange = {}
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("选择模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = false) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        // ExposedDropdownMenu content would go here
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)

    Card(
        onClick = { onClick?.invoke() },
        interactionSource = interactionSource,
        colors = colors,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
