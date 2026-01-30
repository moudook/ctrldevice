package com.ctrldevice.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ctrldevice.R
import com.ctrldevice.features.agent_engine.coordination.Message

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    private val messages = mutableListOf<Message>()

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_message, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
        private val senderText: TextView = itemView.findViewById(R.id.senderText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(msg: Message) {
            val timestamp = msg.timestamp.toString().substringAfter("T").substringBefore(".")
            timestampText.text = timestamp
            senderText.text = msg.from

            when (msg) {
                is Message.DataAvailable -> {
                    val data = msg.data.toString()
                    messageText.text = data

                    if (msg.key == "error") {
                        senderText.setTextColor(Color.RED)
                    } else if (msg.key == "thought") {
                        senderText.setTextColor(Color.parseColor("#9C27B0")) // Purple
                    } else if (msg.key == "action") {
                        senderText.setTextColor(Color.parseColor("#2E7D32")) // Green
                    } else {
                        senderText.setTextColor(Color.parseColor("#2196F3")) // Blue
                    }
                }
                is Message.TaskComplete -> {
                    messageText.text = "Task Completed: ${msg.result}"
                    senderText.setTextColor(Color.parseColor("#388E3C")) // Green
                }
                is Message.ErrorOccurred -> {
                    messageText.text = "ERROR: ${msg.error.message}"
                    senderText.setTextColor(Color.RED)
                }
                is Message.ResourcePreempted -> {
                    messageText.text = "Preempted: ${msg.reason}"
                    senderText.setTextColor(Color.parseColor("#FF9800")) // Orange
                }
                else -> {
                    messageText.text = msg.toString()
                    senderText.setTextColor(Color.GRAY)
                }
            }
        }
    }
}
