package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FastForward
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.X
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus

/**
 * Inline TTS controls component that can be embedded directly in UI
 * without using FloatingWindow. Suitable for use in floating window service.
 */
@Composable
fun TTSControlsInline(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val ttsState = LocalTTSState.current
    val playbackState by ttsState.playbackState.collectAsState()
    val isSpeaking by ttsState.isSpeaking.collectAsState()
    var expand by remember { mutableStateOf(false) }

    if (!isSpeaking) {
        return
    }

    Row(
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TTSPlayPauseButton(playbackState = playbackState, ttsState = ttsState)

        IconButton(
            onClick = {
                ttsState.stop()
            }
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = "Stop",
            )
        }

        if (!compact) {
            AnimatedVisibility(expand) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TTSSpeedButton(playbackState, ttsState)

                    TTSFastForwardButton(ttsState = ttsState)
                }
            }

            IconButton(
                onClick = {
                    expand = !expand
                }
            ) {
                Icon(
                    imageVector = if (expand) Lucide.ChevronLeft else Lucide.ChevronRight,
                    contentDescription = "Expand",
                )
            }
        }
    }
}

@Composable
private fun TTSFastForwardButton(ttsState: CustomTtsState) {
    IconButton(
        onClick = {
            ttsState.fastForward(5000)
        }
    ) {
        Icon(
            imageVector = Lucide.FastForward,
            contentDescription = "Fast Forward",
        )
    }
}

@Composable
private fun TTSPlayPauseButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    FilledTonalIconButton(
        onClick = {
            when (playbackState.status) {
                PlaybackStatus.Playing -> {
                    ttsState.pause()
                }

                else -> {
                    ttsState.resume()
                }
            }
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Icon(
            imageVector = if (playbackState.status == PlaybackStatus.Playing) Lucide.Pause else Lucide.Play,
            contentDescription = if (playbackState.status == PlaybackStatus.Playing) "Pause" else "Play",
        )
        if (playbackState.status == PlaybackStatus.Playing || playbackState.status == PlaybackStatus.Buffering || playbackState.status == PlaybackStatus.Paused) {
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.positionMs.toFloat() / playbackState.durationMs
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
            CircularProgressIndicator(
                progress = {
                    if (playbackState.status == PlaybackStatus.Playing) {
                        playbackState.currentChunkIndex.toFloat() / playbackState.totalChunks
                    } else {
                        0f
                    }
                },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(2.dp),
                strokeWidth = 2.dp,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun TTSSpeedButton(
    playbackState: PlaybackState,
    ttsState: CustomTtsState
) {
    TextButton(
        onClick = {
            when (playbackState.speed) {
                0.8f -> {
                    ttsState.setSpeed(1.0f)
                }

                1.0f -> {
                    ttsState.setSpeed(1.2f)
                }

                1.2f -> {
                    ttsState.setSpeed(1.5f)
                }

                1.5f -> {
                    ttsState.setSpeed(0.8f)
                }

                else -> {
                    ttsState.setSpeed(1.0f)
                }
            }
        }
    ) {
        Text(text = "x${"%.1f".format(playbackState.speed)}")
    }
}
