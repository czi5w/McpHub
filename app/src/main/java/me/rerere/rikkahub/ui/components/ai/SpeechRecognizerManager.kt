package me.rerere.rikkahub.ui.components.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import me.rerere.rikkahub.ui.hooks.ChatInputState

// Auto-send delay time in milliseconds (2 seconds)
private const val AUTO_SEND_DELAY_MS = 2000L

/**
 * Manual voice recognition manager
 * Click to start recording → user speaks → click again to stop
 * Supports auto-send: automatically sends after 2 seconds of no new content
 */
@Composable
fun VoiceInputButtonWithSpeechService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context,
    onAutoSend: (() -> Unit)? = null
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
    var lastRecognizedText by remember { mutableStateOf("") }

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
                
                // If listening and has new content, reset auto-send timer
                if (isListening && result.text.isNotBlank() && result.text != lastRecognizedText) {
                    lastRecognizedText = result.text
                    
                    // Cancel previous auto-send task
                    autoSendJob?.cancel()
                    
                    // Start new auto-send timer (after 2 seconds)
                    autoSendJob = launch {
                        delay(AUTO_SEND_DELAY_MS)
                        
                        // Check conditions: listening, has content, not loading
                        if (isListening && !state.isEmpty() && !state.loading && onAutoSend != null) {
                            // Update state
                            isListening = false
                            
                            // Stop voice recognition
                            try {
                                speechService.stopRecognition()
                            } catch (e: Exception) {
                                // Ignore stop recognition errors
                            }
                            
                            // Trigger auto-send
                            onAutoSend()
                        }
                    }
                }
            }
        }
        launch {
            speechService.recognitionErrorFlow.collect { error ->
                if (error.message.isNotBlank()) {
                    Toast.makeText(context, "识别错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    isListening = false
                    autoSendJob?.cancel()
                    autoSendJob = null
                    lastRecognizedText = ""
                }
            }
        }
    }
    
    // Cancel auto-send task when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            autoSendJob?.cancel()
        }
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (isListening) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primary
            )
            .clickable {
                if (ContextCompat.checkSelfPermission(context, audioPermission)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    isListening = !isListening
                    if (isListening) {
                        // Reset auto-send state
                        lastRecognizedText = ""
                        coroutineScope.launch {
                            try {
                                speechService.startRecognition(
                                    languageCode = "zh-CN",
                                    continuousMode = true,
                                    partialResults = true
                                )
                            } catch (e: Exception) {
                                isListening = false
                                autoSendJob?.cancel()
                                autoSendJob = null
                                lastRecognizedText = ""
                                Toast.makeText(context, "开始识别失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        // Manually stop recognition, cancel auto-send and reset state
                        autoSendJob?.cancel()
                        autoSendJob = null
                        lastRecognizedText = ""
                        coroutineScope.launch {
                            try {
                                speechService.stopRecognition()
                            } catch (e: Exception) {
                                Toast.makeText(context, "停止识别失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    launcher.launch(audioPermission)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "停止语音" else "语音输入",
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
