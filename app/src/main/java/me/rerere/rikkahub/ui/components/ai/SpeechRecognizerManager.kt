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

// 自动发送延迟时间（毫秒）
private const val AUTO_SEND_DELAY_MS = 2000L

/**
 * 手动控制的语音识别管理器
 * 点击开始录音 → 用户说话 → 再次点击停止
 * 支持自动发送：2秒无新内容时自动发送
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
                
                // 如果正在监听且有新内容，重置自动发送定时器
                if (isListening && result.text.isNotBlank() && result.text != lastRecognizedText) {
                    lastRecognizedText = result.text
                    
                    // 取消之前的自动发送任务
                    autoSendJob?.cancel()
                    
                    // 启动新的自动发送定时器（2秒后）
                    autoSendJob = launch {
                        delay(AUTO_SEND_DELAY_MS)
                        
                        // 检查条件：监听中、有内容、不在加载状态
                        if (isListening && !state.isEmpty() && !state.loading && onAutoSend != null) {
                            // 停止语音识别
                            try {
                                speechService.stopRecognition()
                                isListening = false
                            } catch (e: Exception) {
                                // 忽略停止识别的错误
                            }
                            
                            // 触发自动发送
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
                }
            }
        }
    }
    
    // 当组件销毁时，取消自动发送任务
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
                        // 重置自动发送状态
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
                        // 手动停止识别，取消自动发送并重置状态
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
