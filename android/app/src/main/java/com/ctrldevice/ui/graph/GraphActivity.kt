package com.ctrldevice.ui.graph

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.ctrldevice.features.agent_engine.coordination.CurrentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GraphActivity : Activity() {

    private lateinit var graphView: TaskGraphView
    private lateinit var statusText: TextView
    private lateinit var closeButton: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatic layout to avoid creating XML for simple activity
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        statusText = TextView(this).apply {
            text = "Live Task Graph"
            textSize = 20f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }
        layout.addView(statusText)

        graphView = TaskGraphView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        layout.addView(graphView)

        closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        layout.addView(closeButton)

        setContentView(layout)

        startLiveUpdate()
    }

    private fun startLiveUpdate() {
        scope.launch {
            while (isRunning) {
                val activeGraph = CurrentSession.activeGraph
                if (activeGraph != null) {
                    graphView.updateGraph(activeGraph.getSnapshot())
                } else {
                    statusText.text = "No Active Session"
                }
                delay(500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}
