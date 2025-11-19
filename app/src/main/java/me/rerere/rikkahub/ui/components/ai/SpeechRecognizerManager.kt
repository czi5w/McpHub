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
import androidx.compose.runtime.collectAsState
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
 * 手动控制的语音识别管理器
 * 点击开始录音 → 用户说话 → 再次点击停止
 */
@Composable
fun VoiceInputButtonWithSpeechService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context
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

    // Observe initialization state to restart flow collection when service is initialized
    val isServiceInitialized by speechService.isInitialized.collectAsState()

    // 收集识别结果 - restart when service becomes initialized
    LaunchedEffect(speechService, isServiceInitialized) {
        if (!isServiceInitialized) {
            Log.d("VoiceAutoSend", "Waiting for service initialization...")
            return@LaunchedEffect
        }
        
        Log.d("VoiceAutoSend", "Starting recognition result collection")
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
                lastVoiceInputText = result.text
                
                Log.d("VoiceAutoSend", "Recognition result: text='${result.text}', isFinal=${result.isFinal}")
                
                // 当收到识别结果（包括partial和final）且有文本内容时，更新最后更新时间
                if (result.text.isNotBlank()) {
                    lastUpdateTime = System.currentTimeMillis()
                    
                    // 取消之前的定时器
                    autoSendJob?.cancel()
                    
                    // 启动新的2秒定时器（在收到最后一次更新后2秒触发）
                    autoSendJob = launch {
                        delay(2000)
                        Log.d("VoiceAutoSend", "2 seconds elapsed since last update. isEmpty=${state.isEmpty()}")
                        // 2秒后，如果输入框有内容，触发自动发送
                        if (!state.isEmpty()) {
                            Log.d("VoiceAutoSend", "Triggering auto-send")
                            state.shouldTriggerAutoSend = true
                        }
                    }
                }
                
                // 如果收到最终结果，也记录一下
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
    
    // 监听文本变化，如果用户手动输入（文本与最后的语音输入不同），取消自动发送定时器
    LaunchedEffect(state.textContent.text.toString()) {
        val currentText = state.textContent.text.toString()
        if (!isListening && currentText != lastVoiceInputText) {
            autoSendJob?.cancel()
            autoSendJob = null
        }
    }

    VoiceInputButton(
        isListening = isListening,
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
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
                            return@launch
                        }
                    }
                    
                    speechService.startRecognition(
                        languageCode = "zh-CN",
                        continuousMode = true,
                        partialResults = true
                    )
                } catch (e: Exception) {
                    isListening = false
                    Toast.makeText(context, "开始识别失败", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onStopListening = {
            isListening = false
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
 */
@Composable
fun VoiceInputButtonForService(
    state: ChatInputState,
    speechService: SpeechService,
    context: Context
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

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                Log.d("VoiceInputService", "Received recognition result: text='${result.text}', isFinal=${result.isFinal}")
                state.textContent.edit { replace(0, length, result.text) }
                lastVoiceInputText = result.text
                
                // 当收到识别结果（包括partial和final）且有文本内容时，更新最后更新时间
                if (result.text.isNotBlank()) {
                    lastUpdateTime = System.currentTimeMillis()
                    
                    // 取消之前的定时器
                    autoSendJob?.cancel()
                    
                    // 启动新的2秒定时器（在收到最后一次更新后2秒触发）
                    autoSendJob = launch {
                        delay(2000)
                        Log.d("VoiceInputService", "2 seconds elapsed since last update. isEmpty=${state.isEmpty()}")
                        // 2秒后，如果输入框有内容，触发自动发送
                        if (!state.isEmpty()) {
                            Log.d("VoiceInputService", "Triggering auto-send")
                            state.shouldTriggerAutoSend = true
                        }
                    }
                }
                
                // 如果收到最终结果，也记录一下
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
    
    // 监听文本变化，如果用户手动输入（文本与最后的语音输入不同），取消自动发送定时器
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
                    
                    Log.d("VoiceInputService", "Calling speechService.startRecognition")
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
                if (isListening) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primary
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
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
