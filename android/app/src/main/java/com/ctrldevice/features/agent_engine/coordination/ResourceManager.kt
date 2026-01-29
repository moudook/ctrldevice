package com.ctrldevice.features.agent_engine.coordination

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
    data class Screen(val isExclusive: Boolean = true) : Resource()
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
    private val messageBus: MessageBus,
    private val stateManager: StateManager
) {
    private val leases = ConcurrentHashMap<Resource, ResourceLease>()
    // private val waitQueue = PriorityQueue<ResourceRequest>(compareBy { -it.priority }) // Replaced with resource-specific queues
    private val waitQueues = ConcurrentHashMap<Resource, PriorityQueue<ResourceRequest>>()
    private val waiters = ConcurrentHashMap<AgentId, CancellableContinuation<ResourceLease?>>()

    suspend fun acquire(
        resource: Resource,
        requester: AgentId,
        priority: Int = 5,
        timeout: Duration = 30.seconds
    ): ResourceLease? {
        val now = Clock.System.now()
        val existingLease = leases[resource]

        return when {
            // Case 1: Available
            existingLease == null -> {
                grantLease(resource, requester, priority, timeout, now)
            }

            // Case 2: Held by same agent (Extend)
            existingLease.owner == requester -> {
                grantLease(resource, requester, priority, timeout, now)
            }

            // Case 3: Higher priority (Preempt)
            priority > existingLease.priority -> {
                preemptResource(existingLease, requester, priority, timeout)
            }

            // Case 4: Wait
            else -> {
                waitForResource(resource, requester, priority, timeout)
            }
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

        // Force release
        release(currentLease.resource, currentLease.owner)

        // Grant to new owner
        return acquire(currentLease.resource, newOwner, newPriority, timeout)
    }

    private suspend fun waitForResource(
        resource: Resource,
        requester: AgentId,
        priority: Int,
        timeout: Duration
    ): ResourceLease? = withTimeoutOrNull(timeout) {
        val request = ResourceRequest(resource, requester, priority)

        synchronized(waitQueues) {
            waitQueues.computeIfAbsent(resource) {
                PriorityQueue(compareBy { -it.priority })
            }.add(request)
        }

        suspendCancellableCoroutine { continuation ->
            waiters[requester] = continuation
            continuation.invokeOnCancellation {
                synchronized(waitQueues) {
                    waitQueues[resource]?.remove(request)
                }
                waiters.remove(requester)
            }
        }
    }

    fun release(resource: Resource, owner: AgentId) {
        val lease = leases[resource]
        if (lease?.owner == owner) {
            leases.remove(resource)

            // Process waitQueue to notify next waiter
            var nextRequest: ResourceRequest? = null

            synchronized(waitQueues) {
                val queue = waitQueues[resource]
                if (queue != null && queue.isNotEmpty()) {
                    nextRequest = queue.poll()
                }
            }

            if (nextRequest != null) {
                val continuation = waiters.remove(nextRequest!!.requester)
                if (continuation != null && continuation.isActive) {
                    val newLease = grantLease(
                        resource = nextRequest!!.resource,
                        owner = nextRequest!!.requester,
                        priority = nextRequest!!.priority,
                        timeout = 30.seconds, // Default extension
                        now = Clock.System.now()
                    )
                    continuation.resume(newLease)
                }
            }
        }
    }

    fun getCurrentLeases(): List<ResourceLease> = leases.values.toList()
}
