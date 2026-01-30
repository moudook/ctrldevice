package com.ctrldevice.service.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.ctrldevice.R
import com.ctrldevice.features.agent_engine.coordination.Message
import com.ctrldevice.features.agent_engine.coordination.MessageBus
import com.ctrldevice.features.agent_engine.safety.AgentGovernor
import com.ctrldevice.service.accessibility.ControllerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var borderView: View? = null // New Border View
    private var params: WindowManager.LayoutParams? = null
    private var borderParams: WindowManager.LayoutParams? = null

    private var statusText: TextView? = null
    private var statusIcon: ImageView? = null
    private var stopButton: Button? = null

    private val messageBus = MessageBus.Instance
    private var scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlay()
        createBorderOverlay() // Initialize Border
        subscribeToEvents()
    }

    private fun createBorderOverlay() {
        val inflater = LayoutInflater.from(this)
        borderView = inflater.inflate(R.layout.layout_overlay_border, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        borderParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // Pass-through
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Cover status bar too
            PixelFormat.TRANSLUCENT
        )

        // Start hidden
        borderView?.visibility = View.GONE

        try {
            windowManager?.addView(borderView, borderParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.layout_overlay_control, null)

        statusText = overlayView?.findViewById(R.id.overlayStatusText)
        statusIcon = overlayView?.findViewById(R.id.overlayStatusIcon)
        stopButton = overlayView?.findViewById(R.id.overlayStopButton)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        params?.gravity = Gravity.TOP or Gravity.START
        params?.x = 0
        params?.y = 100

        setupTouchListener()
        setupStopButton()

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupStopButton() {
        stopButton?.setOnClickListener {
            // Trigger Emergency Stop
            // We can emit to ControllerService which MainActivity listens to,
            // or better, broadcast an intent.
            ControllerService.userInterrupts.tryEmit(true)
            statusText?.text = "STOPPED"
            statusIcon?.setImageResource(android.R.drawable.ic_delete)
            Toast.makeText(this, "Emergency Stop Triggered via Overlay", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTouchListener() {
        overlayView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params?.x = initialX + (event.rawX - initialTouchX).toInt()
                        params?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun subscribeToEvents() {
        // Subscribe to global agent events
        val channel = messageBus.register("OverlayService")
        scope.launch {
            for (msg in channel) {
                when (msg) {
                    is Message.DataAvailable -> {
                        if (msg.key == "status") {
                             statusText?.text = msg.data.toString()
                        } else if (msg.key == "action") {
                             statusText?.text = "Acting..."
                             statusIcon?.setImageResource(android.R.drawable.ic_menu_edit)
                             borderView?.visibility = View.VISIBLE // Show Border
                        } else if (msg.key == "thought") {
                             statusText?.text = "Thinking..."
                             statusIcon?.setImageResource(android.R.drawable.ic_popup_sync)
                             borderView?.visibility = View.VISIBLE // Show Border
                        }
                    }
                    is Message.TaskComplete -> {
                        statusText?.text = "Idle"
                        statusIcon?.setImageResource(android.R.drawable.presence_online)
                        borderView?.visibility = View.GONE // Hide Border
                    }
                    is Message.ErrorOccurred -> {
                        statusText?.text = "Error"
                        statusIcon?.setImageResource(android.R.drawable.stat_notify_error)
                        borderView?.visibility = View.GONE // Hide Border
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        messageBus.unregister("OverlayService")
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
        }
        if (borderView != null) {
            windowManager?.removeView(borderView)
        }
    }
}
