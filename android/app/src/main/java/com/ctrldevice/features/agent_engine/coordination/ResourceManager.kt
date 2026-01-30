package com.ctrldevice.features.agent_engine.coordination

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

typealias AgentId = String

sealed class Resource {
    data class App(val packageName: String) : Resource()
    data class Screen(val isExclusive: Boolean = true) : Resource() {
        // Logic Compression: All Screen resource requests contend for the same physical display
        // regardless of the 'isExclusive' flag.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }
    }
    data class Network(val priority: Int) : Resource()
    data class Storage(val path: String) : Resource()
}

data class ResourceLease(
    val resource: Resource,
    val owner: AgentId,
    val acquiredAt: Instant,
    val expiresAt: Instant,
    val priority: Int
)

data class ResourceRequest(
    val resource: Resource,
    val requester: AgentId,
    val priority: Int
)

class ResourceManager(
    private val context: Context,
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val leases = ConcurrentHashMap<Resource, ResourceLease>()
    // private val waitQueue = PriorityQueue<ResourceRequest>(compareBy { -it.priority }) // Replaced with resource-specific queues
    private val waitQueues = ConcurrentHashMap<Resource, PriorityQueue<ResourceRequest>>()
    // Optimization: Use Channel for safe signaling instead of raw Continuations to avoid race conditions
    private val waitChannels = ConcurrentHashMap<AgentId, kotlinx.coroutines.channels.Channel<ResourceLease>>()

    // Optimization: Global mutex to ensure atomic check-then-act for resource state
    private val stateMutex = Mutex()

    // Q19: Keep Screen Awake
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Suppress("DEPRECATION") // valid for service-based screen control where Activity flags aren't available
    private val wakeLock: PowerManager.WakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "CtrlDevice:AgentActive"
    )

    suspend fun acquire(
        resource: Resource,
        requester: AgentId,
        priority: Int = 5,
        timeout: Duration = 30.seconds
    ): ResourceLease? {
        val now = Clock.System.now()

        // Logic Compression: Atomic State Transition in one block
        val immediateLease = stateMutex.withLock {
            val lease = leases[resource]

            // Simplified Decision Logic (K-Map reduction)
            // Available OR OwnedBySelf -> Grant
            // HigherPriority -> Preempt
            // Else -> Wait
            if (lease == null || lease.owner == requester) {
                grantLease(resource, requester, priority, timeout, now)
            } else if (priority > lease.priority) {
                preemptResource(lease, requester, priority, timeout)
            } else {
                // Enqueue
                waitChannels.computeIfAbsent(requester) { kotlinx.coroutines.channels.Channel(1) }
                waitQueues.computeIfAbsent(resource) {
                    PriorityQueue(compareBy { -it.priority })
                }.add(ResourceRequest(resource, requester, priority))
                null
            }
        }

        if (immediateLease != null) return immediateLease

        return withTimeoutOrNull(timeout) {
            waitChannels[requester]?.receive()
        }
    }

    private fun grantLease(
        resource: Resource,
        owner: AgentId,
        priority: Int,
        timeout: Duration,
        now: Instant
    ): ResourceLease {
        val lease = ResourceLease(resource, owner, now, now + timeout, priority)
        leases[resource] = lease

        // Activate WakeLock if acquiring Screen
        if (resource is Resource.Screen) {
            updateWakeLock(true)
        }

        return lease
    }

    private suspend fun preemptResource(
        currentLease: ResourceLease,
        newOwner: AgentId,
        newPriority: Int,
        timeout: Duration
    ): ResourceLease? {
        // Notify current owner to pause
        messageBus.send(
            Message.ResourcePreempted(
                from = "ResourceManager",
                to = currentLease.owner,
                timestamp = Clock.System.now(),
                resource = currentLease.resource,
                reason = "Higher priority task: $newOwner"
            )
        )

        // Save current owner's state
        stateManager.checkpoint(currentLease.owner)

        // Force release (Internal call, we are already holding the lock in acquire flow)
        // BUT wait, preemptResource is called from acquire which holds the lock.
        // We shouldn't call release() because release() might try to take the lock again if we aren't careful.
        // Let's implement an internal release logic that assumes lock is held.
        releaseInternal(currentLease.resource, currentLease.owner)

        // Grant to new owner
        return grantLease(currentLease.resource, newOwner, newPriority, timeout, Clock.System.now())
    }

    // Removed explicit waitForResource as it is now handled in acquire

    suspend fun release(resource: Resource, owner: AgentId) {
        stateMutex.withLock {
            releaseInternal(resource, owner)
        }
    }

    private fun releaseInternal(resource: Resource, owner: AgentId) {
        val lease = leases[resource]
        if (lease?.owner == owner) {
            leases.remove(resource)

            // Release WakeLock if releasing Screen
            if (resource is Resource.Screen) {
                updateWakeLock(false)
            }

            // Process waitQueue to notify next waiter
            val queue = waitQueues[resource]
            if (queue != null && queue.isNotEmpty()) {
                val nextRequest = queue.poll()
                if (nextRequest != null) {
                     val channel = waitChannels[nextRequest.requester]
                     if (channel != null) {
                         val newLease = grantLease(
                            resource = nextRequest.resource,
                            owner = nextRequest.requester,
                            priority = nextRequest.priority,
                            timeout = 30.seconds,
                            now = Clock.System.now()
                        )
                        // Send safely
                        channel.trySend(newLease)
                     }
                }
            }
        }
    }

    private fun updateWakeLock(acquire: Boolean) {
        synchronized(wakeLock) {
            if (acquire) {
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(10 * 60 * 1000L /*10 minutes max*/)
                }
            } else {
                // Check if any other agent still holds the screen?
                // For now, if *this* lease is released, we check if map has other Screen keys?
                // But map key IS Resource.Screen. It's unique.
                // So if we removed it from leases, no one holds the screen.
                if (leases.keys.none { it is Resource.Screen }) {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            }
        }
    }

    fun getCurrentLeases(): List<ResourceLease> = leases.values.toList()
}
