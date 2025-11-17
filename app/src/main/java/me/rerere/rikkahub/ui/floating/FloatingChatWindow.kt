package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minimize2
import com.composables.icons.lucide.X
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.components.ai.ChatInput
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.chat.AssistantBackground
import me.rerere.rikkahub.ui.pages.chat.ChatList
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.ui.hooks.rememberChatInputState
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FloatingChatWindow(
    conversationId: String,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(conversationId)
        }
    )
    
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    val inputState = rememberChatInputState()
    
    Surface(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                FloatingWindowHeader(
                    title = conversation?.title ?: context.getString(me.rerere.rikkahub.R.string.floating_window_new_chat),
                    onMinimize = onMinimize,
                    onClose = onClose
                )
            },
            bottomBar = {
                conversation?.let { conv ->
                    ChatInput(
                        state = inputState,
                        settings = settings,
                        conversation = conv,
                        mcpManager = vm.mcpManager,
                        onCancelClick = {
                            loadingJob?.cancel()
                        },
                        enableSearch = enableWebSearch,
                        onToggleSearch = {
                            vm.updateSettings(settings.copy(enableWebSearch = !enableWebSearch))
                        },
                        onSendClick = {
                            if (currentChatModel == null) {
                                toaster.show("Please select a model first", type = ToastType.Error)
                                return@ChatInput
                            }
                            if (inputState.isEditing()) {
                                vm.handleMessageEdit(
                                    parts = inputState.getContents(),
                                    messageId = inputState.editingMessage!!,
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
                                    messageId = inputState.editingMessage!!,
                                )
                            } else {
                                vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            }
                            inputState.clearInput()
                        },
                        onUpdateChatModel = {
                            vm.setChatModel(assistant = settings.getCurrentAssistant(), model = it)
                        },
                        onUpdateAssistant = {
                            vm.updateSettings(
                                settings.copy(
                                    assistants = settings.assistants.map { assistant ->
                                        if (assistant.id == it.id) {
                                            it
                                        } else {
                                            assistant
                                        }
                                    }
                                )
                            )
                        },
                        onUpdateSearchService = { index ->
                            vm.updateSettings(
                                settings.copy(
                                    searchServiceSelected = index
                                )
                            )
                        },
                        onClearContext = {
                            vm.handleMessageTruncate()
                        },
                    )
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AssistantBackground(settings)
                
                conversation?.let { conv ->
                    ChatList(
                        innerPadding = innerPadding,
                        conversation = conv,
                        state = chatListState,
                        loading = loadingJob != null,
                        previewMode = false,
                        settings = settings,
                        onRegenerate = {
                            vm.regenerateAtMessage(it)
                        },
                        onEdit = {
                            inputState.editingMessage = it.id
                            inputState.setContents(it.parts)
                        },
                        onForkMessage = {
                            // Fork not available in floating mode
                        },
                        onDelete = {
                            vm.deleteMessage(it)
                        },
                        onUpdateMessage = { newNode ->
                            vm.updateConversation(
                                conv.copy(
                                    messageNodes = conv.messageNodes.map { node ->
                                        if (node.id == newNode.id) {
                                            newNode
                                        } else {
                                            node
                                        }
                                    }
                                ))
                            vm.saveConversationAsync()
                        },
                        onClickSuggestion = { suggestion ->
                            inputState.editingMessage = null
                            inputState.setMessageText(suggestion)
                        },
                        onTranslate = { message, locale ->
                            vm.translateMessage(message, locale)
                        },
                        onClearTranslation = { message ->
                            vm.clearTranslationField(message.id)
                        },
                        onJumpToMessage = { index ->
                            scope.launch {
                                chatListState.animateScrollToItem(index)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingWindowHeader(
    title: String,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onMinimize,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Lucide.Minimize2,
                    contentDescription = "Minimize",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = "Close",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
