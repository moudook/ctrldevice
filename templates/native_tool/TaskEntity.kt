package com.ctrldevice.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a long-running task that persists across app restarts.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val instruction: String,
    val status: String, // "RUNNING", "PAUSED", "COMPLETED"
    val createdAt: Long,
    val lastUpdated: Long,
    
    // JSON blob of the current context variables
    val contextJson: String 
)

@Entity(tableName = "logs")
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val taskId: String,
    val timestamp: Long,
    val actionName: String, // e.g., "CLICK"
    val details: String,    // e.g., "Clicked 'Compose' button"
    val screenshotPath: String?
)
