package com.deepseek.dshmobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.dshmobile.database.SessionEntity
import com.deepseek.dshmobile.repository.SessionRepository
import com.deepseek.dshmobile.service.DshEngineManager
import com.deepseek.dshmobile.ui.screens.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUIState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val engineManager: DshEngineManager
) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionRepository
        .getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val messageStates = mutableMapOf<String, MutableStateFlow<ChatUIState>>()

    fun createSession(name: String) {
        viewModelScope.launch {
            sessionRepository.createSession(name)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
        }
    }

    fun getMessages(sessionId: String): StateFlow<List<ChatMessage>> {
        return sessionRepository.getMessages(sessionId)
            .map { entities ->
                entities.map { entity ->
                    ChatMessage(
                        id = entity.messageId,
                        role = entity.role,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun sendMessage(sessionId: String, content: String) {
        viewModelScope.launch {
            // Save user message
            sessionRepository.saveMessage(sessionId, "user", content)

            _isLoading.value = true
            try {
                // Call engine to get response
                val response = engineManager.sendMessage(content, sessionId)
                // Parse response and save
                sessionRepository.saveMessage(sessionId, "assistant", response)
            } catch (e: Exception) {
                sessionRepository.saveMessage(
                    sessionId,
                    "assistant",
                    "Sorry, request failed: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }
}
