package com.deepseek.dshmobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.deepseek.dshmobile.ui.components.LoadingBubble
import com.deepseek.dshmobile.ui.components.MessageBubble

data class ChatMessage(
    val id: String,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    onSend: (String) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty() || isLoading) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DeepSeek Harness") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* refresh */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.trim().isNotEmpty()) {
                        onSend(inputText.trim())
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                enabled = !isLoading
            )

        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (messages.isEmpty() && !isLoading) {
                item {
                    EmptyChatState()
                }
            }
            items(messages) { message ->
                MessageBubble(
                    content = message.content,
                    isUser = message.role == "user"
                )
            }
            if (isLoading) {
                item {
                    LoadingBubble()
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter message...") },
                enabled = enabled,
                maxLines = 4,
                shape = MaterialTheme.shapes.large
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { if (enabled && text.trim().isNotEmpty()) onSend() },
                modifier = Modifier.size(48.dp),
                containerColor = if (enabled && text.trim().isNotEmpty())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Apps,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "DeepSeek Harness",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start chatting with AI assistant",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
