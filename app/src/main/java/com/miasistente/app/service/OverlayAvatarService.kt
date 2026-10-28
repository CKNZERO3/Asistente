
package com.miasistente.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.avatar.AssistantAvatarView

class OverlayAvatarService : Service(), LifecycleOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private lateinit var windowManager: WindowManager
    private var floatView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val ACTION_START =
            "com.miasistente.app.action.START_OVERLAY"
        private const val ACTION_STOP =
            "com.miasistente.app.action.STOP_OVERLAY"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(context)
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(settingsIntent)
                return
            }

            context.startService(
                Intent(context, OverlayAvatarService::class.java)
                    .setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayAvatarService::class.java)
                    .setAction(ACTION_STOP)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !Settings.canDrawOverlays(this)
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (floatView == null) {
                    showOverlay()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun showOverlay() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(160)
        }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )

            setViewTreeLifecycleOwner(this@OverlayAvatarService)
            setViewTreeViewModelStoreOwner(this@OverlayAvatarService)

            setContent {
                AsistenteTema {
                    val currentState by
                        AsistenteService.assistantState.collectAsState()

                    AssistantAvatarView(currentState = currentState)
                }
            }
        }

        params = layoutParams
        floatView = view
        setupTouchListener(view, layoutParams)

        try {
            windowManager.addView(view, layoutParams)
        } catch (exception: Exception) {
            floatView = null
            params = null
            view.disposeComposition()
            stopSelf()
        }
    }

    private fun setupTouchListener(
        view: View,
        layoutParams: WindowManager.LayoutParams
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { touchedView, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x =
                        initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y =
                        initialY + (event.rawY - initialTouchY).toInt()

                    try {
                        windowManager.updateViewLayout(view, layoutParams)
                    } catch (_: IllegalArgumentException) {
                        // The overlay may have been detached.
                    }

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

        floatView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The window was already removed.
            } finally {
                view.disposeComposition()
            }
        }

        floatView = null
        params = null

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        viewModelStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

