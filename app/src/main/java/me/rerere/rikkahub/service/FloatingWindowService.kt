package me.rerere.rikkahub.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.floatingwindow.FloatingChatDialog
import me.rerere.rikkahub.ui.components.floatingwindow.FloatingWindowView
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.rememberCustomTtsState
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, OnBackPressedDispatcherOwner, ActivityResultRegistryOwner {
    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()
    private val _onBackPressedDispatcher = OnBackPressedDispatcher()
    private val _activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I : Any?, O : Any?> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            // No-op implementation for service context
            // Activity results don't make sense in a service, but we need to provide the registry
        }
    }
    private var showChatDialog by mutableStateOf(false)
    private var conversationId by mutableStateOf(Uuid.random())
    
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_window_channel"
    }
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
        
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    
    override val viewModelStore: ViewModelStore
        get() = _viewModelStore
    
    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = _onBackPressedDispatcher
    
    override val activityResultRegistry: ActivityResultRegistry
        get() = _activityResultRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service to prevent system from killing it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        if (floatingView == null) {
            showFloatingWindow()
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Chat Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating chat window active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Chat")
            .setContentText("Chat window is active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 50
            y = 50
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeOnBackPressedDispatcherOwner(this@FloatingWindowService)
            setContent {
                val toastState = rememberToasterState()
                val tts = rememberCustomTtsState()
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                
                RikkahubTheme {
                    CompositionLocalProvider(
                        LocalActivityResultRegistryOwner provides this@FloatingWindowService,
                        LocalNavController provides navController,
                        LocalSettings provides settings,
                        LocalHighlighter provides highlighter,
                        LocalToaster provides toastState,
                        LocalTTSState provides tts,
                    ) {
                        Toaster(
                            state = toastState,
                            darkTheme = LocalDarkMode.current,
                            richColors = true,
                            alignment = Alignment.TopCenter,
                            showCloseButton = true,
                        )
                        
                        if (showChatDialog) {
                            FloatingChatDialog(
                                conversationId = conversationId,
                                onClose = { 
                                    showChatDialog = false
                                    updateWindowSize(false)
                                },
                                onSettingsClick = { 
                                    openMainApp("settings")
                                    stopSelf()
                                }
                            )
                        } else {
                            FloatingWindowView(
                                onClose = { stopSelf() },
                                onChatClick = { 
                                    showChatDialog = true
                                    updateWindowSize(true)
                                },
                                onSettingsClick = { 
                                    openMainApp("settings")
                                    stopSelf()
                                }
                            )
                        }
                    }
                }
            }
        }

        windowManager?.addView(floatingView, params)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    
    private fun updateWindowSize(expanded: Boolean) {
        floatingView?.let { view ->
            windowManager?.let { wm ->
                val params = view.layoutParams as WindowManager.LayoutParams
                
                if (expanded) {
                    // Expand to show chat dialog - allow focus for input field
                    val displayMetrics = resources.displayMetrics
                    params.width = (displayMetrics.widthPixels * 0.9).toInt()
                    params.height = (displayMetrics.heightPixels * 0.8).toInt()
                    params.gravity = Gravity.CENTER
                    params.x = 0
                    params.y = 0
                    // Remove FLAG_NOT_FOCUSABLE to allow keyboard input
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                   WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                } else {
                    // Collapse to button - prevent stealing focus
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    params.gravity = Gravity.BOTTOM or Gravity.START
                    params.x = 50
                    params.y = 50
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                
                wm.updateViewLayout(view, params)
            }
        }
    }
    
    private fun openMainApp(destination: String) {
        try {
            val intent = Intent(this, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("destination", destination)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // If starting activity fails, just stop the service
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        floatingView?.let {
            windowManager?.removeView(it)
        }
        floatingView = null
        windowManager = null
        _viewModelStore.clear()
        
        // Stop foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
