package me.rerere.rikkahub.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.ui.floating.FloatingButton
import me.rerere.rikkahub.ui.floating.FloatingChatWindow
import me.rerere.rikkahub.ui.floating.FloatingMenuWindow
import me.rerere.rikkahub.ui.floating.FloatingSettingsWindow
import me.rerere.rikkahub.ui.floating.FloatingWindowState
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlin.uuid.Uuid

class FloatingWindowService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry 
        get() = savedStateRegistryController.savedStateRegistry
    
    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    
    private var windowState by mutableStateOf(FloatingWindowState.BUTTON)
    private var conversationId by mutableStateOf<String?>(null)
    
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        showFloatingButton()
        
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_CHAT -> {
                conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: Uuid.random().toString()
                showChatWindow()
            }
            ACTION_SHOW_SETTINGS -> showSettingsWindow()
            ACTION_SHOW_BUTTON -> showFloatingButton()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }
    
    private fun showFloatingButton() {
        removeCurrentView()
        windowState = FloatingWindowState.BUTTON
        
        val params = createWindowLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        )
        
        floatingView = createComposeView {
            RikkahubTheme {
                FloatingButton(
                    onMenuClick = { showMenuWindow() },
                    onDragStart = { },
                    onDragEnd = { x, y -> 
                        // Update button position
                        params.x = x.toInt()
                        params.y = y.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                )
            }
        }
        
        setupLifecycleOwner(floatingView!!)
        windowManager.addView(floatingView, params)
    }
    
    private fun showMenuWindow() {
        removeCurrentView()
        windowState = FloatingWindowState.MENU
        
        val params = createWindowLayoutParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        )
        
        floatingView = createComposeView {
            RikkahubTheme {
                FloatingMenuWindow(
                    onChatClick = {
                        conversationId = Uuid.random().toString()
                        showChatWindow()
                    },
                    onSettingsClick = { showSettingsWindow() },
                    onCloseClick = { showFloatingButton() }
                )
            }
        }
        
        setupLifecycleOwner(floatingView!!)
        windowManager.addView(floatingView, params)
    }
    
    private fun showChatWindow() {
        removeCurrentView()
        windowState = FloatingWindowState.CHAT
        
        val params = createWindowLayoutParams(
            width = (resources.displayMetrics.widthPixels * 0.9).toInt(),
            height = (resources.displayMetrics.heightPixels * 0.7).toInt(),
            gravity = Gravity.CENTER,
            focusable = true
        )
        
        floatingView = createComposeView {
            RikkahubTheme {
                FloatingChatWindow(
                    conversationId = conversationId ?: Uuid.random().toString(),
                    onMinimize = { showFloatingButton() },
                    onClose = { 
                        stopSelf()
                    }
                )
            }
        }
        
        setupLifecycleOwner(floatingView!!)
        windowManager.addView(floatingView, params)
    }
    
    private fun showSettingsWindow() {
        removeCurrentView()
        windowState = FloatingWindowState.SETTINGS
        
        val params = createWindowLayoutParams(
            width = (resources.displayMetrics.widthPixels * 0.9).toInt(),
            height = (resources.displayMetrics.heightPixels * 0.7).toInt(),
            gravity = Gravity.CENTER,
            focusable = true
        )
        
        floatingView = createComposeView {
            RikkahubTheme {
                FloatingSettingsWindow(
                    onMinimize = { showFloatingButton() },
                    onClose = { 
                        stopSelf()
                    }
                )
            }
        }
        
        setupLifecycleOwner(floatingView!!)
        windowManager.addView(floatingView, params)
    }
    
    private fun removeCurrentView() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
        }
        floatingView = null
    }
    
    private fun createWindowLayoutParams(
        width: Int,
        height: Int,
        gravity: Int,
        focusable: Boolean = false
    ): WindowManager.LayoutParams {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        
        return WindowManager.LayoutParams(
            width,
            height,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = 0
            y = 0
        }
    }
    
    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(this).apply {
            setContent(content)
        }
    }
    
    private fun setupLifecycleOwner(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Window",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for floating window service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle(getString(R.string.floating_window_notification_title))
        .setContentText(getString(R.string.floating_window_notification_text))
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, RouteActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()
    
    override fun onDestroy() {
        removeCurrentView()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
    
    companion object {
        private const val CHANNEL_ID = "floating_window_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_SHOW_CHAT = "me.rerere.rikkahub.SHOW_CHAT"
        const val ACTION_SHOW_SETTINGS = "me.rerere.rikkahub.SHOW_SETTINGS"
        const val ACTION_SHOW_BUTTON = "me.rerere.rikkahub.SHOW_BUTTON"
        const val ACTION_STOP = "me.rerere.rikkahub.STOP"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        
        fun startService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
