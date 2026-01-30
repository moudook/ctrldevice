package com.ctrldevice.ui.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.ctrldevice.features.agent_engine.coordination.TaskGraph
import com.ctrldevice.features.agent_engine.coordination.TaskId

class TaskGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var snapshot: TaskGraph.GraphSnapshot? = null

    // Paints
    private val nodePaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val strokePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val edgePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 3f
        isAntiAlias = true
    }

    // Layout
    private val nodePositions = mutableMapOf<TaskId, RectF>()
    private val nodeWidth = 400f
    private val nodeHeight = 100f
    private val verticalGap = 150f
    private val horizontalGap = 50f

    fun updateGraph(snapshot: TaskGraph.GraphSnapshot) {
        this.snapshot = snapshot
        calculateLayout()
        invalidate()
    }

    private fun calculateLayout() {
        if (snapshot == null) return
        nodePositions.clear()

        val nodes = snapshot!!.nodes.values.toList()
        if (nodes.isEmpty()) return

        // Simple vertical layout for now
        // TODO: Improve to handle parallel branches properly by analyzing edges

        var currentY = 100f
        val centerX = width / 2f

        nodes.forEach { node ->
            val left = centerX - (nodeWidth / 2)
            val top = currentY
            nodePositions[node.id] = RectF(left, top, left + nodeWidth, top + nodeHeight)
            currentY += nodeHeight + verticalGap
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (snapshot == null) return

        // Draw Edges
        snapshot!!.edges.forEach { (fromId, edges) ->
            val fromRect = nodePositions[fromId] ?: return@forEach
            edges.forEach { edge ->
                val toRect = nodePositions[edge.to] ?: return@forEach

                // Draw line from bottom of 'from' to top of 'to'
                val startX = fromRect.centerX()
                val startY = fromRect.bottom
                val endX = toRect.centerX()
                val endY = toRect.top

                canvas.drawLine(startX, startY, endX, endY, edgePaint)

                // Draw arrow head (simple circle for now)
                canvas.drawCircle(endX, endY, 5f, edgePaint)
            }
        }

        // Draw Nodes
        snapshot!!.nodes.forEach { (id, node) ->
            val rect = nodePositions[id] ?: return@forEach

            // Color based on status
            nodePaint.color = when {
                id in snapshot!!.failed -> Color.parseColor("#FFCDD2") // Red
                id in snapshot!!.completed -> Color.parseColor("#C8E6C9") // Green
                else -> Color.parseColor("#E3F2FD") // Blue/Waiting
            }

            canvas.drawRoundRect(rect, 16f, 16f, nodePaint)
            canvas.drawRoundRect(rect, 16f, 16f, strokePaint)

            // Draw text (truncate if too long)
            val text = if (node.description.length > 20) node.description.take(17) + "..." else node.description
            canvas.drawText(text, rect.centerX(), rect.centerY() + 10f, textPaint)
        }
    }
}
