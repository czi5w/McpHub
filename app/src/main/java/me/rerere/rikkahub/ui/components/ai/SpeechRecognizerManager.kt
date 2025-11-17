package me.rerere.rikkahub.ui.components.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
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

    VoiceInputButton(
        isListening = isListening,
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
            isListening = true
            coroutineScope.launch {
                try {
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

    // 收集识别结果
    LaunchedEffect(speechService) {
        launch {
            speechService.recognitionResultFlow.collect { result ->
                state.textContent.edit { replace(0, length, result.text) }
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

    VoiceInputButton(
        isListening = isListening,
        hasPermission = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED,
        onStartListening = {
            isListening = true
            coroutineScope.launch {
                try {
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
            // 在Service context中，打开应用设置页面让用户手动授权
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Toast.makeText(context, "请在设置中授予麦克风权限", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
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
