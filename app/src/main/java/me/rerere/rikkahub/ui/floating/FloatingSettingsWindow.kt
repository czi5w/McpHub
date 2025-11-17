package me.rerere.rikkahub.ui.floating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minimize2
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import org.koin.androidx.compose.koinViewModel

@Composable
fun FloatingSettingsWindow(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    Surface(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            FloatingWindowHeader(
                title = context.getString(R.string.floating_window_quick_settings),
                onMinimize = onMinimize,
                onClose = onClose
            )
            
            // Settings Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                item {
                    Text(
                        text = "Quick Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Display") },
                        supportingContent = { Text("Theme and appearance") },
                        leadingContent = {
                            Icon(
                                imageVector = Lucide.Palette,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Providers") },
                        supportingContent = { Text("Manage AI providers") },
                        leadingContent = {
                            Icon(
                                imageVector = Lucide.Monitor,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Models") },
                        supportingContent = { Text("Configure AI models") },
                        leadingContent = {
                            Icon(
                                imageVector = Lucide.Bot,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
                
                item {
                    ListItem(
                        headlineContent = { Text("Text-to-Speech") },
                        supportingContent = { Text("TTS settings") },
                        leadingContent = {
                            Icon(
                                imageVector = Lucide.Volume2,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
                
                item {
                    Text(
                        text = context.getString(R.string.floating_window_full_settings_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
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
