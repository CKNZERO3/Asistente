
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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
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
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.miasistente.app.ui.AsistenteTema
import com.miasistente.app.ui.avatar.AssistantAvatarView
import kotlin.math.abs

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

        savedStateController =
            SavedStateRegistryController.create(this).apply {
                performAttach()
                performRestore(null)
            }

        lifecycleRegistry.handleLifecycleEvent(
            Lifecycle.Event.ON_CREATE
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf(startId)
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

            isClickable = true

            setContent {
                val currentState by
                    AsistenteService.assistantState.collectAsState(
                        initial = AsistenteService.assistantState.value
                    )

                AsistenteTema {
                    AssistantAvatarView(
                        currentState = currentState
                    )
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
            windowManager.addView(view, params)

            composeView = view
            overlayParams = params
            isViewAdded = true

            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_START
            )

            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_RESUME
            )
        } catch (_: RuntimeException) {
            disposeFailedView(view)
            stopSelf()
        }
    }

    private fun createDragListener(): View.OnTouchListener {
        val touchSlop =
            ViewConfiguration.get(this).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        return View.OnTouchListener { view, event ->
            val params = overlayParams
                ?: return@OnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y

                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (!isDragging) {
                        isDragging =
                            abs(deltaX) > touchSlop ||
                                abs(deltaY) > touchSlop
                    }

                    if (
                        isDragging &&
                        isViewAdded &&
                        view.isAttachedToWindow
                    ) {
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()

                        try {
                            windowManager.updateViewLayout(
                                view,
                                params
                            )
                        } catch (_: IllegalArgumentException) {
                            isViewAdded = false
                        } catch (_: IllegalStateException) {
                            isViewAdded = false
                        }
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }

                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun disposeFailedView(view: ComposeView) {
        try {
            if (view.parent != null) {
                windowManager.removeViewImmediate(view)
            }
        } catch (_: RuntimeException) {
            // La vista puede haber sido retirada por WindowManager.
        }

        view.setOnTouchListener(null)
        view.disposeComposition()

        if (composeView === view) {
            composeView = null
            overlayParams = null
        }

        isViewAdded = false
    }

    private fun removeOverlay() {
        val view = composeView ?: return

        try {
            if (isViewAdded) {
                windowManager.removeViewImmediate(view)
            }
        } catch (_: RuntimeException) {
            // La ventana puede haber sido retirada previamente.
        } finally {
            isViewAdded = false

            view.setOnTouchListener(null)
            view.disposeComposition()

            composeView = null
            overlayParams = null
        }
    }

    override fun onDestroy() {
        if (::lifecycleRegistry.isInitialized) {
            if (
                lifecycleRegistry.currentState ==
                Lifecycle.State.RESUMED
            ) {
                lifecycleRegistry.handleLifecycleEvent(
                    Lifecycle.Event.ON_PAUSE
                )
            }

            if (
                lifecycleRegistry.currentState ==
                Lifecycle.State.STARTED
            ) {
                lifecycleRegistry.handleLifecycleEvent(
                    Lifecycle.Event.ON_STOP
                )
            }
        }

        removeOverlay()

        if (
            ::lifecycleRegistry.isInitialized &&
            lifecycleRegistry.currentState != Lifecycle.State.DESTROYED
        ) {
            lifecycleRegistry.handleLifecycleEvent(
                Lifecycle.Event.ON_DESTROY
            )
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
            if (!Settings.canDrawOverlays(context)) return

            val intent = Intent(
                context,
                OverlayAvatarService::class.java
            ).setAction(ACTION_START)

            context.applicationContext.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(
                context,
                OverlayAvatarService::class.java
            )

            context.stopService(intent)
        }
    }
}
