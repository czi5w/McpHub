package me.rerere.rikkahub.ui.components.floatingwindow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.X
import com.dokar.sonner.ToastType
import kotlinx.coroutines.flow.collectLatest
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import me.rerere.rikkahub.ui.pages.chat.ChatList
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

@Composable
fun FloatingChatDialog(
    conversationId: Uuid,
    onClose: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(conversationId.toString())
        }
    )
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Handle Error
    LaunchedEffect(Unit) {
        vm.errorFlow.collectLatest { error ->
            toaster.show(error.message ?: "Error", type = ToastType.Error)
        }
    }

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val inputState = rememberChatInputState()
    val listState = rememberLazyListState()

    Card(
        modifier = modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            TopAppBar(
                title = {
                    Text(
                        text = conversation.title.ifEmpty { "Chat" },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Lucide.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Close"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Chat list - with weight to fill available space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ChatList(
                    innerPadding = PaddingValues(0.dp),
                    conversation = conversation,
                    state = listState,
                    loading = loadingJob != null,
                    previewMode = false,
                    settings = setting,
                    onRegenerate = { message ->
                        vm.regenerateAtMessage(message)
                    },
                    onDelete = { message ->
                        vm.deleteMessage(message)
                    },
                    onEdit = { message ->
                        inputState.editingMessage = message.id
                        inputState.setContents(message.parts)
                    },
                    onForkMessage = {},
                    onUpdateMessage = {},
                    onClickSuggestion = { suggestion ->
                        inputState.setMessageText(suggestion)
                    },
                    onTranslate = null,
                    onClearTranslation = {},
                    onJumpToMessage = {}
                )
            }

            // Chat input
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                ChatInput(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    state = inputState,
                    settings = setting,
                    conversation = conversation,
                    mcpManager = vm.mcpManager,
                    showVoiceButton = false, // Disable voice button in floating window
                    onCancelClick = {
                        loadingJob?.cancel()
                    },
                    enableSearch = false,
                    onToggleSearch = {},
                    onSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!
                            )
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                        }
                        inputState.clearInput()
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = { model ->
                        vm.setChatModel(assistant = setting.getCurrentAssistant(), model = model)
                    },
                    onUpdateAssistant = { assistant ->
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { a ->
                                    if (a.id == assistant.id) {
                                        assistant
                                    } else {
                                        a
                                    }
                                }
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onClearContext = {
                        vm.handleMessageTruncate()
                    }
                )
            }
        }
    }
}
