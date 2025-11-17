package me.rerere.rikkahub.ui.components.floatingwindow

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Boxes
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Hammer
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SunMoon
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.X
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.setting.SettingTTSPage
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.ColorMode
import org.koin.androidx.compose.koinViewModel

// Sealed class for settings navigation
sealed class SettingsScreen {
    data object Main : SettingsScreen()
    data object Display : SettingsScreen()
    data object Models : SettingsScreen()
    data object Providers : SettingsScreen()
    data object Search : SettingsScreen()
    data object TTS : SettingsScreen()
}

@Composable
fun FloatingSettingsDialog(
    onClose: () -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<SettingsScreen>(SettingsScreen.Main) }
    
    val onNavigateBack = {
        currentScreen = SettingsScreen.Main
    }
    
    val vm: SettingVM = koinViewModel()
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()

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
                        text = when (currentScreen) {
                            SettingsScreen.Main -> stringResource(R.string.settings)
                            SettingsScreen.Display -> stringResource(R.string.setting_page_display_setting)
                            SettingsScreen.Models -> stringResource(R.string.setting_page_default_model)
                            SettingsScreen.Providers -> stringResource(R.string.setting_page_providers)
                            SettingsScreen.Search -> stringResource(R.string.setting_page_search_service)
                            SettingsScreen.TTS -> stringResource(R.string.setting_page_tts_service)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentScreen == SettingsScreen.Main) {
                            onClose()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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

            // Content based on current screen
            when (currentScreen) {
                SettingsScreen.Main -> SettingsMainScreen(
                    settings = settings,
                    onNavigateToDisplay = { currentScreen = SettingsScreen.Display },
                    onNavigateToModels = { currentScreen = SettingsScreen.Models },
                    onNavigateToProviders = { currentScreen = SettingsScreen.Providers },
                    onNavigateToSearch = { currentScreen = SettingsScreen.Search },
                    onNavigateToTTS = { currentScreen = SettingsScreen.TTS },
                    onOpenFullSettings = onOpenFullSettings
                )
                SettingsScreen.Display -> SettingDisplayPage()
                SettingsScreen.Models -> SettingModelPage()
                SettingsScreen.Providers -> SettingProviderPage()
                SettingsScreen.Search -> SettingSearchPage()
                SettingsScreen.TTS -> SettingTTSPage()
            }
        }
    }
}

@Composable
private fun SettingsMainScreen(
    settings: me.rerere.rikkahub.data.datastore.Settings,
    onNavigateToDisplay: () -> Unit,
    onNavigateToModels: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToTTS: () -> Unit,
    onOpenFullSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.setting_page_general_settings),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item("colorMode") {
            var colorMode by rememberColorMode()
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_page_color_mode))
                },
                leadingContent = {
                    Icon(Lucide.SunMoon, null)
                },
                trailingContent = {
                    Select(
                        options = ColorMode.entries,
                        selectedOption = colorMode,
                        onOptionSelected = {
                            colorMode = it
                        },
                        optionToString = {
                            when (it) {
                                ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                            }
                        },
                        modifier = Modifier.width(150.dp)
                    )
                }
            )
        }

        item {
            Text(
                text = stringResource(R.string.setting_page_model_and_services),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_page_display_setting)) },
                supportingContent = { Text(stringResource(R.string.setting_page_display_setting_desc)) },
                leadingContent = { Icon(Lucide.Monitor, "Display Setting") },
                trailingContent = { Icon(Lucide.ChevronRight, null) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToDisplay() }
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                leadingContent = { Icon(Lucide.Hammer, stringResource(R.string.setting_page_default_model)) },
                trailingContent = { Icon(Lucide.ChevronRight, null) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToModels() }
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                leadingContent = { Icon(Lucide.Boxes, "Models") },
                trailingContent = { Icon(Lucide.ChevronRight, null) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToProviders() }
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                leadingContent = { Icon(Lucide.Search, "Search") },
                trailingContent = { Icon(Lucide.ChevronRight, null) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToSearch() }
            )
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                leadingContent = { Icon(Lucide.Volume2, "TTS") },
                trailingContent = { Icon(Lucide.ChevronRight, null) },
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToTTS() }
            )
        }

        item {
            Text(
                text = stringResource(R.string.setting_page_more_settings),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            androidx.compose.material3.TextButton(
                onClick = onOpenFullSettings,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Text(text = stringResource(R.string.setting_page_open_full_settings))
            }
        }
    }
}
