package com.miasistente.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.avatar.AssistantAvatarView

class OverlayAvatarService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateController: SavedStateRegistryController

    private val overlayViewModelStore = ViewModelStore()

    private var composeView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = overlayViewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        lifecycleRegistry = LifecycleRegistry(this)
        savedStateController = SavedStateRegistryController.create(this)
        savedStateController.performAttach()
        savedStateController.performRestore(null)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_START, null -> showOverlay()
        }

        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (isViewAdded) return

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Permiso de superposición no concedido",
                Toast.LENGTH_SHORT
            ).show()
            stopSelf()
            return
        }

        val view = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )

            setViewTreeLifecycleOwner(this@OverlayAvatarService)
            setViewTreeViewModelStoreOwner(this@OverlayAvatarService)
            setViewTreeSavedStateRegistryOwner(this@OverlayAvatarService)

            setContent {
                AsistenteTema {
                    AssistantAvatarView()
                }
            }

            setOnTouchListener(createDragListener())
        }

        val params = WindowManager.LayoutParams(
            dpToPx(112),
            dpToPx(112),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(24)
            y = dpToPx(160)
        }

        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            windowManager.addView(view, params)

            composeView = view
            overlayParams = params
            isViewAdded = true
        } catch (exception: WindowManager.BadTokenException) {
            disposeOverlayView(view)
            stopSelf()
        } catch (exception: SecurityException) {
            disposeOverlayView(view)
            stopSelf()
        } catch (exception: IllegalStateException) {
            disposeOverlayView(view)
            stopSelf()
        }
    }

    private fun createDragListener(): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        return View.OnTouchListener { view, event ->
            val params = overlayParams ?: return@OnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    if (isViewAdded && view.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: IllegalArgumentException) {
                            isViewAdded = false
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun removeOverlay() {
        val view = composeView ?: return

        if (isViewAdded) {
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // La vista ya no está adjunta a WindowManager.
            } catch (_: IllegalStateException) {
                // WindowManager ya no puede gestionar esta vista.
            }
        }

        isViewAdded = false
        disposeOverlayView(view)

        composeView = null
        overlayParams = null
    }

    private fun disposeOverlayView(view: ComposeView) {
        view.setOnTouchListener(null)
        view.disposeComposition()
    }

    override fun onDestroy() {
        removeOverlay()

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }

        overlayViewModelStore.clear()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val ACTION_START =
            "com.miasistente.app.action.START_OVERLAY"

        private const val ACTION_STOP =
            "com.miasistente.app.action.STOP_OVERLAY"

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                return
            }

            val intent = Intent(
                context,
                OverlayAvatarService::class.java
            ).setAction(ACTION_START)

            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(
                context,
                OverlayAvatarService::class.java
            ).setAction(ACTION_STOP)

            context.startService(intent)
        }
    }
}
