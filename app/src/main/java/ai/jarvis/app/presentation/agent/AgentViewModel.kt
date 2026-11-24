package ai.jarvis.app.presentation.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.jarvis.app.core.security.SecurePreferences
import ai.jarvis.app.domain.model.AgentMessage
import ai.jarvis.app.domain.model.SensitiveAction
import ai.jarvis.app.domain.orchestrator.AgentOrchestrator
import ai.jarvis.app.service.voice.VoiceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AgentViewModel(
    private val orchestrator: AgentOrchestrator,
    private val voiceManager: VoiceManager,
    private val securePreferences: SecurePreferences,
    private val backendAuthManager: ai.jarvis.app.data.remote.BackendAuthManager? = null
) : ViewModel() {

    val messages: StateFlow<List<AgentMessage>> = orchestrator.messages
    val isProcessing: StateFlow<Boolean> = orchestrator.isProcessing
    val pendingConfirmation: StateFlow<SensitiveAction?> = orchestrator.pendingConfirmation
    val isListening: StateFlow<Boolean> = voiceManager.isListening
    val recognizedText: StateFlow<String?> = voiceManager.recognizedText
    val rmsLevel: StateFlow<Float> = voiceManager.rmsLevel
    val isSpeaking: StateFlow<Boolean> = voiceManager.isSpeaking
    val voiceError: StateFlow<String?> = voiceManager.voiceError

    val conversations: StateFlow<List<ai.jarvis.app.domain.repository.ConversationInfo>> = orchestrator.conversations
    val activeConversationId: StateFlow<String?> = orchestrator.activeConversationId

    private val _voiceFeedback = MutableStateFlow(securePreferences.isVoiceFeedbackEnabled())
    val voiceFeedback: StateFlow<Boolean> = _voiceFeedback

    val llmProvider: StateFlow<String> = securePreferences.llmProvider
    val llmLabel: StateFlow<String> = securePreferences.llmLabel

    fun setVoiceFeedback(enabled: Boolean) {
        _voiceFeedback.value = enabled
        securePreferences.setVoiceFeedbackEnabled(enabled)
        if (!enabled) voiceManager.stopSpeaking()
    }

    fun newConversation() = orchestrator.newConversation()
    fun openConversation(id: String) = orchestrator.openConversation(id)
    fun deleteConversation(id: String) = orchestrator.deleteConversation(id)

    init {
        // Collect spoken agent responses if voice feedback is enabled
        viewModelScope.launch {
            orchestrator.speechEvents.collect { text ->
                if (securePreferences.isVoiceFeedbackEnabled()) {
                    voiceManager.speak(text)
                }
            }
        }
    }

    val mailSource = securePreferences.mailSource

    /** True when a Gmail session exists; used to show a "sign in" hint for the test and own-Gmail sources. */
    val gmailConnectionState = backendAuthManager?.connectionState

    fun sendPrompt(prompt: String, isVoice: Boolean = false, attachment: String? = null) {
        viewModelScope.launch {
            orchestrator.processUserPrompt(prompt, isVoice, attachment)
        }
    }

    fun clearConversation() {
        orchestrator.clearConversation()
    }

    fun startVoiceInput() {
        voiceManager.startListening { recognizedText ->
            sendPrompt(recognizedText, isVoice = true)
        }
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun stopSpeaking() {
        voiceManager.stopSpeaking()
    }

    fun clearVoiceError() = voiceManager.clearError()

    fun onConfirmAction(approved: Boolean, editedArguments: Map<String, Any?>? = null) {
        orchestrator.confirmPendingAction(approved, editedArguments)
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.shutdown()
    }
}
