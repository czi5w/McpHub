package me.rerere.rikkahub.ui.components.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ai.assistance.operit.api.speech.SpeechService
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState

/**
 * Shared effect to monitor loading state and stop voice recognition when AI starts responding
 */
@Composable
private fun MonitorLoadingStateEffect(
    loading: Boolean,
    isListening: Boolean,
    onStopListening: () -> Unit,
    tag: String
) {
    LaunchedEffect(loading) {
        if (loading && isListening) {
            Log.d(tag, "AI is responding, stopping voice recognition")
            onStopListening()
        }
    }
}

/**
 * Manual voice recognition manager
 * Click to start recording → User speaks → Click again to stop
 */
@Composable
fun VoiceInputButtonWithSpeechService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context,
    settings: Settings
) {
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = Manifest.permission.RECORD_AUDIO

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要麦克风权限才能语音输入", Toast.LENGTH_SHORT).show()
        }
    }

    var isListening by remember { mutableStateOf(false) }
    var autoSendJob by remember { mutableStateOf<Job?>(null) }
    var lastVoiceInputText by remember { mutableStateOf("") }
    var lastUpdateTime by remember { mutableStateOf(0L) }
    
    // Log state changes for debugging
    LaunchedEffect(isListening, state.loading) {
        Log.d("VoiceAutoSend", "State changed: isListening=$isListening, state.loading=${state.loading}")
    }
    
    // Monitor loading state changes - stop voice recognition when AI starts responding
    MonitorLoadingStateEffect(
        loading = state.loading,
        isListening = isListening,
        onStopListening = {
            Log.d("VoiceAutoSend", "MonitorLoadingStateEffect: Stopping voice recognition due to AI responding")
            isListening = false
            coroutineScope.launch {
                try {
                    speechService.stopRecognition()
                } catch (e: Exception) {
                    Log.e("VoiceAutoSend", "Error stopping recognition when AI starts responding", e)
                }
            }
        },
        tag = "VoiceAutoSend"
    )

    // Collect recognition results
    LaunchedEffect(speechService) {
        Log.d("VoiceAutoSend", "Starting recognition result collection")
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
                lastVoiceInputText = result.text
                
                Log.d("VoiceAutoSend", "Recognition result: text='${result.text}', isFinal=${result.isFinal}")
                
                // Update last update time when recognition result (partial or final) with text content is received
                if (result.text.isNotBlank()) {
                    lastUpdateTime = System.currentTimeMillis()
                    
                    // Cancel previous timer
                    autoSendJob?.cancel()
                    
                    // Start new 2-second timer (triggers 2 seconds after last update)
                    // Use coroutineScope to ensure timer survives even if collection stops
                    autoSendJob = coroutineScope.launch {
                        delay(2000)
                        Log.d("VoiceAutoSend", "2 seconds elapsed since last update. isEmpty=${state.isEmpty()}, isListening=$isListening")
                        // After 2 seconds, if input field has content, trigger auto-send
                        if (!state.isEmpty()) {
                            Log.d("VoiceAutoSend", "Triggering auto-send, clearing isListening state")
                            state.shouldTriggerAutoSend = true
                        } else {
                            Log.d("VoiceAutoSend", "Auto-send NOT triggered: input is empty")
                        }
                    }
                }
                
                // Log when final result is received
                if (result.isFinal && result.text.isNotBlank()) {
                    Log.d("VoiceAutoSend", "Final result received")
                }
            }
        }
        launch {
            speechService.recognitionErrorFlow.collect { error ->
                if (error.message.isNotBlank()) {
                    Toast.makeText(context, "识别错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    isListening = false
                }
            }
        }
    }
    
    // Monitor text changes - cancel auto-send timer if user manually edits text
    LaunchedEffect(state.textContent.text.toString()) {
        val currentText = state.textContent.text.toString()
        if (!isListening && currentText != lastVoiceInputText) {
            autoSendJob?.cancel()
            autoSendJob = null
        }
    }

    VoiceInputButton(
        isListening = isListening,
        isEnabled = !state.loading, // Disable voice input when AI is responding
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
            Log.d("VoiceAutoSend", "User clicked start button")
            
            // Check if a model is selected before starting voice recognition
            val currentModel = settings.getCurrentChatModel()
            if (currentModel == null) {
                Log.w("VoiceAutoSend", "Cannot start voice recognition: no model selected")
                Toast.makeText(context, "请先选择一个模型", Toast.LENGTH_LONG).show()
                return@VoiceInputButton
            }
            
            isListening = true
            coroutineScope.launch {
                try {
                    // Ensure service is initialized before starting
                    if (!speechService.isInitialized.value) {
                        Log.d("VoiceAutoSend", "Speech service not initialized, initializing now")
                        val initialized = speechService.initialize()
                        if (!initialized) {
                            isListening = false
                            Toast.makeText(context, "语音识别服务初始化失败", Toast.LENGTH_SHORT).show()
                            Log.e("VoiceAutoSend", "Speech service initialization failed")
                            return@launch
                        }
                        Log.d("VoiceAutoSend", "Speech service initialized successfully")
                    }
                    
                    Log.d("VoiceAutoSend", "Starting speech recognition with model: ${currentModel.displayName}")
                    speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                    Log.d("VoiceAutoSend", "Speech recognition started successfully")
                } catch (e: Exception) {
                    isListening = false
                    Log.e("VoiceAutoSend", "开始识别失败: ${e.message}", e)
                    Toast.makeText(context, "开始识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onStopListening = {
            Log.d("VoiceAutoSend", "User clicked stop button. Current autoSendJob state: ${if (autoSendJob?.isActive == true) "ACTIVE" else "INACTIVE/NULL"}")
            isListening = false
            coroutineScope.launch {
                try {
                    speechService.stopRecognition()
                    Log.d("VoiceAutoSend", "Speech recognition stopped successfully")
                } catch (e: Exception) {
                    Log.e("VoiceAutoSend", "停止识别失败: ${e.message}", e)
                    Toast.makeText(context, "停止识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onRequestPermission = {
            launcher.launch(audioPermission)
        }
    )
}

/**
 * Voice input button - version for Service context (does not use ActivityResultLauncher)
 */
@Composable
fun VoiceInputButtonForService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context,
    settings: Settings
) {
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = Manifest.permission.RECORD_AUDIO

    var isListening by remember { mutableStateOf(false) }
    var autoSendJob by remember { mutableStateOf<Job?>(null) }
    var lastVoiceInputText by remember { mutableStateOf("") }
    var lastUpdateTime by remember { mutableStateOf(0L) }
    
    // Log permission status on composition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        Log.d("VoiceInputService", "VoiceInputButtonForService composed, permission granted: $hasPermission")
    }
    
    // Log state changes for debugging
    LaunchedEffect(isListening, state.loading) {
        Log.d("VoiceInputService", "State changed: isListening=$isListening, state.loading=${state.loading}")
    }
    
    // Monitor loading state changes - stop voice recognition when AI starts responding
    MonitorLoadingStateEffect(
        loading = state.loading,
        isListening = isListening,
        onStopListening = {
            Log.d("VoiceInputService", "MonitorLoadingStateEffect: Stopping voice recognition due to AI responding")
            isListening = false
            coroutineScope.launch {
                try {
                    speechService.stopRecognition()
                } catch (e: Exception) {
                    Log.e("VoiceInputService", "Error stopping recognition when AI starts responding", e)
                }
            }
        },
        tag = "VoiceInputService"
    )

    // Collect recognition results
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                Log.d("VoiceInputService", "Received recognition result: text='${result.text}', isFinal=${result.isFinal}")
                state.textContent.edit { replace(0, length, result.text) }
                lastVoiceInputText = result.text
                
                // Update last update time when recognition result (partial or final) with text content is received
                if (result.text.isNotBlank()) {
                    lastUpdateTime = System.currentTimeMillis()
                    
                    // Cancel previous timer
                    autoSendJob?.cancel()
                    
                    // Start new 2-second timer (triggers 2 seconds after last update)
                    // Use coroutineScope to ensure timer survives even if collection stops
                    autoSendJob = coroutineScope.launch {
                        delay(2000)
                        Log.d("VoiceInputService", "2 seconds elapsed since last update. isEmpty=${state.isEmpty()}, isListening=$isListening")
                        // After 2 seconds, if input field has content, trigger auto-send
                        if (!state.isEmpty()) {
                            Log.d("VoiceInputService", "Triggering auto-send")
                            state.shouldTriggerAutoSend = true
                        } else {
                            Log.d("VoiceInputService", "Auto-send NOT triggered: input is empty")
                        }
                    }
                }
                
                // Log when final result is received
                if (result.isFinal && result.text.isNotBlank()) {
                    Log.d("VoiceInputService", "Final result received")
                }
            }
        }
        launch {
            speechService.recognitionErrorFlow.collect { error ->
                if (error.message.isNotBlank()) {
                    Log.e("VoiceInputService", "Recognition error: code=${error.code}, message=${error.message}")
                    Toast.makeText(context, "识别错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    isListening = false
                }
            }
        }
        launch {
            speechService.recognitionStateFlow.collect { state ->
                Log.d("VoiceInputService", "Recognition state changed: $state")
            }
        }
    }
    
    // Monitor text changes - cancel auto-send timer if user manually edits text
    LaunchedEffect(state.textContent.text.toString()) {
        val currentText = state.textContent.text.toString()
        if (!isListening && currentText != lastVoiceInputText) {
            Log.d("VoiceInputService", "Text changed manually, cancelling auto-send timer")
            autoSendJob?.cancel()
            autoSendJob = null
        }
    }

    VoiceInputButton(
        isListening = isListening,
        isEnabled = !state.loading, // Disable voice input when AI is responding
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
            Log.d("VoiceInputService", "onStartListening called")
            val hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
            Log.d("VoiceInputService", "Permission check before starting: $hasPermission")
            
            if (!hasPermission) {
                Log.w("VoiceInputService", "Permission not granted, cannot start recognition")
                Toast.makeText(context, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                return@VoiceInputButton
            }
            
            // Check if a model is selected before starting voice recognition
            val currentModel = settings.getCurrentChatModel()
            if (currentModel == null) {
                Log.w("VoiceInputService", "Cannot start voice recognition: no model selected")
                Toast.makeText(context, "请先选择一个模型", Toast.LENGTH_LONG).show()
                return@VoiceInputButton
            }
            
            isListening = true
            coroutineScope.launch {
                try {
                    // Ensure service is initialized before starting
                    Log.d("VoiceInputService", "Checking if speechService is initialized: ${speechService.isInitialized.value}")
                    if (!speechService.isInitialized.value) {
                        Log.d("VoiceInputService", "Speech service not initialized, initializing now")
                        val initialized = speechService.initialize()
                        Log.d("VoiceInputService", "Speech service initialization result: $initialized")
                        if (!initialized) {
                            isListening = false
                            Toast.makeText(context, "语音识别服务初始化失败", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                    
                    Log.d("VoiceInputService", "Calling speechService.startRecognition with model: ${currentModel.displayName}")
                    val started = speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                    Log.d("VoiceInputService", "speechService.startRecognition returned: $started")
                    if (!started) {
                        isListening = false
                        Toast.makeText(context, "启动语音识别失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("VoiceInputService", "Exception starting recognition", e)
                    isListening = false
                    Toast.makeText(context, "开始识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onStopListening = {
            Log.d("VoiceInputService", "onStopListening called")
            isListening = false
            coroutineScope.launch {
                try {
                    Log.d("VoiceInputService", "Calling speechService.stopRecognition")
                    val stopped = speechService.stopRecognition()
                    Log.d("VoiceInputService", "speechService.stopRecognition returned: $stopped")
                } catch (e: Exception) {
                    Log.e("VoiceInputService", "Exception stopping recognition", e)
                    Toast.makeText(context, "停止识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onRequestPermission = {
            Log.d("VoiceInputService", "onRequestPermission called - opening app settings")
            // In Service context, open app settings page for user to manually grant permission
            try {
                val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "请在设置中授予麦克风权限", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("VoiceInputService", "Failed to open settings", e)
                Toast.makeText(context, "无法打开设置页面", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

/**
 * Voice input button UI component
 */
@Composable
private fun VoiceInputButton(
    isListening: Boolean,
    isEnabled: Boolean,
    hasPermission: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (!isEnabled) MaterialTheme.colorScheme.surfaceVariant
                else if (isListening) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primary
            )
            .clickable(enabled = isEnabled) {
                if (hasPermission) {
                    if (isListening) {
                        onStopListening()
                    } else {
                        onStartListening()
                    }
                } else {
                    onRequestPermission()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "停止语音" else "语音输入",
            tint = if (!isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                   else MaterialTheme.colorScheme.onPrimary
        )
    }
}
