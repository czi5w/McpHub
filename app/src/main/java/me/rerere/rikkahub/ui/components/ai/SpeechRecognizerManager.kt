package me.rerere.rikkahub.ui.components.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import me.rerere.rikkahub.ui.hooks.ChatInputState

/**
 * 语音交互按钮 - 支持自动发送和语音响应
 * 点击开始录音 → 用户说话 → 3秒无输入自动发送 → 播放AI语音响应
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
    var isInteractionMode by remember { mutableStateOf(false) }
    var autoSendJob by remember { mutableStateOf<Job?>(null) }
    var lastRecognizedText by remember { mutableStateOf("") }

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
                
                // 语音交互模式：检测到新输入时重置3秒计时器
                if (isInteractionMode && result.text.isNotBlank() && result.text != lastRecognizedText) {
                    lastRecognizedText = result.text
                    
                    // 取消之前的自动发送任务
                    autoSendJob?.cancel()
                    
                    // 启动新的3秒倒计时
                    autoSendJob = launch {
                        delay(3000) // 等待3秒
                        if (isInteractionMode && result.text.isNotBlank()) {
                            // 停止录音
                            isListening = false
                            isInteractionMode = false
                            try {
                                speechService.stopRecognition()
                            } catch (e: Exception) {
                                Log.e("VoiceInteraction", "停止识别失败", e)
                            }
                            
                            // 自动发送消息
                            onAutoSend?.invoke()
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
                    isInteractionMode = false
                    autoSendJob?.cancel()
                }
            }
        }
    }

    VoiceInputButton(
        isListening = isListening,
        isInteractionMode = isInteractionMode,
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
            isListening = true
            isInteractionMode = true
            state.voiceInteractionMode = true
            lastRecognizedText = ""
            autoSendJob?.cancel()
            coroutineScope.launch {
                try {
                    speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                } catch (e: Exception) {
                    isListening = false
                    isInteractionMode = false
                    state.voiceInteractionMode = false
                    Toast.makeText(context, "开始识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onStopListening = {
            isListening = false
            isInteractionMode = false
            state.voiceInteractionMode = false
            autoSendJob?.cancel()
            coroutineScope.launch {
                try {
                    speechService.stopRecognition()
                } catch (e: Exception) {
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
 * 语音输入按钮 - 用于Service context的版本（不使用ActivityResultLauncher）
 * 支持语音交互模式
 */
@Composable
fun VoiceInputButtonForService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context,
    onAutoSend: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val audioPermission = Manifest.permission.RECORD_AUDIO

    var isListening by remember { mutableStateOf(false) }
    var isInteractionMode by remember { mutableStateOf(false) }
    var autoSendJob by remember { mutableStateOf<Job?>(null) }
    var lastRecognizedText by remember { mutableStateOf("") }
    
    // Log permission status on composition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        Log.d("VoiceInputService", "VoiceInputButtonForService composed, permission granted: $hasPermission")
    }

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                Log.d("VoiceInputService", "Received recognition result: text='${result.text}', isFinal=${result.isFinal}")
                state.textContent.edit { replace(0, length, result.text) }
                
                // 语音交互模式：检测到新输入时重置3秒计时器
                if (isInteractionMode && result.text.isNotBlank() && result.text != lastRecognizedText) {
                    lastRecognizedText = result.text
                    Log.d("VoiceInputService", "New text detected, resetting 3s timer")
                    
                    // 取消之前的自动发送任务
                    autoSendJob?.cancel()
                    
                    // 启动新的3秒倒计时
                    autoSendJob = launch {
                        delay(3000) // 等待3秒
                        if (isInteractionMode && result.text.isNotBlank()) {
                            Log.d("VoiceInputService", "3s timeout reached, auto-sending message")
                            // 停止录音
                            isListening = false
                            isInteractionMode = false
                            try {
                                speechService.stopRecognition()
                            } catch (e: Exception) {
                                Log.e("VoiceInputService", "停止识别失败", e)
                            }
                            
                            // 自动发送消息
                            onAutoSend?.invoke()
                        }
                    }
                }
            }
        }
        launch {
            speechService.recognitionErrorFlow.collect { error ->
                if (error.message.isNotBlank()) {
                    Log.e("VoiceInputService", "Recognition error: code=${error.code}, message=${error.message}")
                    Toast.makeText(context, "识别错误: ${error.message}", Toast.LENGTH_SHORT).show()
                    isListening = false
                    isInteractionMode = false
                    autoSendJob?.cancel()
                }
            }
        }
        launch {
            speechService.recognitionStateFlow.collect { state ->
                Log.d("VoiceInputService", "Recognition state changed: $state")
            }
        }
    }

    VoiceInputButton(
        isListening = isListening,
        isInteractionMode = isInteractionMode,
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
            
            isListening = true
            isInteractionMode = true
            state.voiceInteractionMode = true
            lastRecognizedText = ""
            autoSendJob?.cancel()
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
                            isInteractionMode = false
                            state.voiceInteractionMode = false
                            Toast.makeText(context, "语音识别服务初始化失败", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }
                    
                    Log.d("VoiceInputService", "Calling speechService.startRecognition")
                    val started = speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                    Log.d("VoiceInputService", "speechService.startRecognition returned: $started")
                    if (!started) {
                        isListening = false
                        isInteractionMode = false
                        state.voiceInteractionMode = false
                        Toast.makeText(context, "启动语音识别失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("VoiceInputService", "Exception starting recognition", e)
                    isListening = false
                    isInteractionMode = false
                    state.voiceInteractionMode = false
                    Toast.makeText(context, "开始识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onStopListening = {
            Log.d("VoiceInputService", "onStopListening called")
            isListening = false
            isInteractionMode = false
            state.voiceInteractionMode = false
            autoSendJob?.cancel()
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
            // 在Service context中，打开应用设置页面让用户手动授权
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
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
 * 语音输入按钮UI组件
 */
@Composable
private fun VoiceInputButton(
    isListening: Boolean,
    isInteractionMode: Boolean = false,
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
                when {
                    isInteractionMode -> MaterialTheme.colorScheme.tertiaryContainer // 交互模式 - 紫色/特殊色
                    isListening -> MaterialTheme.colorScheme.secondaryContainer // 录音中 - 次要色
                    else -> MaterialTheme.colorScheme.primary // 空闲 - 主色
                }
            )
            .clickable {
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
            tint = when {
                isInteractionMode -> MaterialTheme.colorScheme.onTertiaryContainer
                isListening -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onPrimary
            }
        )
    }
}
