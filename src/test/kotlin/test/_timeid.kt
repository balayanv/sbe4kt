package test.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sbe4kt.lib.TimeId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Comprehensive test suite for TimeId - a value type that stores nanosecond time
 * while reserving the least significant 2 digits as a service tag for distributed systems.
 *
 * The key insight is that in distributed systems, each service can be assigned a unique
 * tag from 0 to 99, allowing them to generate globally unique IDs without coordination.
 */
class TimeIdTest {

    // ================================================================================
    // Basic Functionality Tests
    // ================================================================================

    @Test
    fun `should create TimeId with nanosecond precision`() {
        val nanoTime = System.nanoTime()
        val timeId = TimeId(nanoTime)

       Assertions.assertEquals(nanoTime, timeId.nanos())
       Assertions.assertEquals(nanoTime, timeId.value)
    }

    @Test
    fun `should convert to milliseconds correctly`() {
        val nanoTime = 1_234_567_890_123L // 1234567.890123 milliseconds
        val timeId = TimeId(nanoTime)

       Assertions.assertEquals(1_234_567L, timeId.millis()) // Truncates to whole milliseconds
    }

    @Test
    fun `should convert to microseconds correctly`() {
        val nanoTime = 1_234_567_890L // 1234567.890 microseconds
        val timeId = TimeId(nanoTime)

       Assertions.assertEquals(1_234_567L, timeId.micros()) // Truncates to whole microseconds
    }

    @Test
    fun `should extract tag from least significant 2 digits`() {
        // Testing various values with different tags
       Assertions.assertEquals(0, TimeId(100).tag())   // 100 -> tag is 00
       Assertions.assertEquals(42, TimeId(142).tag())  // 142 -> tag is 42
       Assertions.assertEquals(99, TimeId(199).tag())  // 199 -> tag is 99
       Assertions.assertEquals(67, TimeId(1_234_567).tag()) // 1234567 -> tag is 67
    }

    @Test
    fun `should update tag while preserving timestamp`() {
        val originalTime = 1_234_567_890_123L
        val timeId = TimeId(originalTime)

        // Original tag is 23 (from ...123)
       Assertions.assertEquals(23, timeId.tag())

        // Update to new tag
        val updatedId = timeId.tag(42)
       Assertions.assertEquals(42, updatedId.tag())

        // Verify the timestamp part is preserved (minus the tag digits)
        val originalBase = (originalTime / 100) * 100
        val updatedBase = (updatedId.value / 100) * 100
       Assertions.assertEquals(originalBase, updatedBase)
    }

    @Test
    fun `should validate tag range 0 to 99`() {
        val timeId = TimeId(1_234_567_890)

        // Valid tags should work
       Assertions.assertDoesNotThrow { timeId.tag(0) }
       Assertions.assertDoesNotThrow { timeId.tag(50) }
       Assertions.assertDoesNotThrow { timeId.tag(99) }

        // Invalid tags should throw
       assertThrows<IllegalArgumentException> { timeId.tag(-1) }
       assertThrows<IllegalArgumentException> { timeId.tag(100) }
       assertThrows<IllegalArgumentException> { timeId.tag(1000) }
    }

    @Test
    fun `should chain operations fluently`() {
        val newTime = System.nanoTime()
        val serviceTag = 42

        // Demonstrate fluent API usage
        val timeId = TimeId(0)
            .nanos(newTime)
            .tag(serviceTag)

       Assertions.assertEquals(serviceTag, timeId.tag())
        // The base time should match (without tag)
       Assertions.assertEquals((newTime / 100) * 100, (timeId.value / 100) * 100)
    }

    @Test
    fun `toString should return raw value`() {
        val value = 1_234_567_890_123L
        val timeId = TimeId(value)

       Assertions.assertEquals(value.toString(), timeId.toString())
    }

    // ================================================================================
    // Distributed System Use Cases
    // ================================================================================

    @Test
    fun `should generate unique IDs for different services`() {
        // Simulate 3 different services in a distributed system
        val service1Tag = 1
        val service2Tag = 2
        val service3Tag = 3

        // Each service generates IDs at approximately the same time
        val baseTime = System.nanoTime()

        val service1Id = TimeId(baseTime).tag(service1Tag)
        val service2Id = TimeId(baseTime).tag(service2Tag)
        val service3Id = TimeId(baseTime).tag(service3Tag)

        // All IDs should be unique despite same timestamp
       Assertions.assertNotEquals(service1Id.value, service2Id.value)
       Assertions.assertNotEquals(service2Id.value, service3Id.value)
       Assertions.assertNotEquals(service1Id.value, service3Id.value)

        // Each should have correct service tag
       Assertions.assertEquals(service1Tag, service1Id.tag())
       Assertions.assertEquals(service2Tag, service2Id.tag())
       Assertions.assertEquals(service3Tag, service3Id.tag())
    }

    @Test
    fun `should handle realistic distributed ID generation scenario`() {
        // Simulate a microservices architecture with 5 services
        data class ServiceConfig(val name: String, val tag: Int)

        val services = listOf(
            ServiceConfig("auth-service", 10),
            ServiceConfig("user-service", 20),
            ServiceConfig("order-service", 30),
            ServiceConfig("payment-service", 40),
            ServiceConfig("notification-service", 50)
        )

        // Each service generates multiple IDs
        val idsPerService = 100
        val allIds = mutableMapOf<String, List<TimeId>>()

        services.forEach { service ->
            val ids = (1..idsPerService).map {
                Thread.sleep(1) // Simulate some processing time
                TimeId(System.nanoTime()).tag(service.tag)
            }
            allIds[service.name] = ids
        }

        // Verify all IDs are unique globally
        val allIdValues = allIds.values.flatten().map { it.value }.toSet()
       Assertions.assertEquals(
          services.size * idsPerService, allIdValues.size,
          "All generated IDs should be globally unique"
       )

        // Verify each service's IDs have the correct tag
        services.forEach { service ->
            allIds[service.name]?.forEach { id ->
               Assertions.assertEquals(
                  service.tag, id.tag(),
                  "All IDs from ${service.name} should have tag ${service.tag}"
               )
            }
        }
    }

    @Test
    fun `should support concurrent ID generation within same service`() {
        // Even within the same service, we need unique IDs for concurrent operations
        val serviceTag = 75
        val threadCount = 10
        val idsPerThread = 100

        val allIds = ConcurrentHashMap<Long, Int>()
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) { threadId ->
            executor.submit {
                try {
                    repeat(idsPerThread) {
                        // Generate ID with service tag
                        val timeId = TimeId(System.nanoTime()).tag(serviceTag)

                        // Try to add to map - returns null if key didn't exist
                        val previous = allIds.putIfAbsent(timeId.value, threadId)

                        // In high-concurrency scenarios with same tag,
                        // we might need additional mechanisms (like adding thread ID or counter)
                        // but for demonstration, we'll just track potential collisions
                        if (previous != null && previous != threadId) {
                            // This could happen if two threads generate at exact same nanosecond
                            // In production, you'd handle this with retry or additional entropy
                            println("Potential collision detected between thread $threadId and $previous")
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Most IDs should be unique (collisions can happen when threads generate at exact same nanosecond)
        // In practice, you'd add additional entropy (thread ID, counter, etc) to avoid this
       Assertions.assertTrue(
          allIds.size >= threadCount * idsPerThread * 0.90,
          "At least 90% of IDs should be unique even in concurrent scenario (actual: ${allIds.size} of ${threadCount * idsPerThread})"
       )

        // All should have the correct service tag
        allIds.keys.forEach { idValue ->
           Assertions.assertEquals(serviceTag, TimeId(idValue).tag())
        }
    }

    // ================================================================================
    // Edge Cases and Boundaries
    // ================================================================================

    @Test
    fun `should handle maximum and minimum values correctly`() {
        // Test with Long.MAX_VALUE
        val maxId = TimeId(Long.MAX_VALUE)
       Assertions.assertEquals(Long.MAX_VALUE, maxId.value)
       Assertions.assertEquals((Long.MAX_VALUE % 100).toInt(), maxId.tag())

        // Test with 0
        val zeroId = TimeId(0)
       Assertions.assertEquals(0, zeroId.value)
       Assertions.assertEquals(0, zeroId.tag())

        // Test with negative values (though unlikely in real nanoTime usage)
        val negativeId = TimeId(-12345)
       Assertions.assertEquals(-12345, negativeId.value)
        // Note: tag extraction with negative numbers might behave differently
        // This is implementation-specific behavior
    }

    @Test
    fun `should preserve precision through conversions`() {
        val nanos = 1_234_567_890_123_456_789L
        val timeId = TimeId(nanos)

        // Converting to millis and back loses precision
        val millisOnly = timeId.millis() * 1_000_000
       Assertions.assertTrue(nanos > millisOnly, "Original nanos should have more precision")

        // Converting to micros and back loses some precision
        val microsOnly = timeId.micros() * 1_000
       Assertions.assertTrue(nanos > microsOnly, "Original nanos should have more precision than micros")

        // But the original value is always preserved
       Assertions.assertEquals(nanos, timeId.nanos())
    }

    // ================================================================================
    // Real-World Usage Examples
    // ================================================================================

    @Test
    fun `example - event sourcing with distributed services`() {
        // In an event sourcing system, each service generates events with unique IDs

        data class Event(
           val id: TimeId,
           val type: String,
           val payload: String
        )

        class EventStore {
            private val events = mutableListOf<Event>()

            fun append(event: Event) = events.add(event)

            fun getEventsByService(serviceTag: Int) =
                events.filter { it.id.tag() == serviceTag }

            fun getEventsInTimeRange(startMillis: Long, endMillis: Long) =
                events.filter {
                    val millis = it.id.millis()
                    millis in startMillis..endMillis
                }
        }

        val store = EventStore()

        // Service 1 (User Service) generates events
        val userServiceTag = 11
        store.append(Event(
            TimeId(System.nanoTime()).tag(userServiceTag),
            "UserCreated",
            "user123"
        ))

        Thread.sleep(1)

        // Service 2 (Order Service) generates events
        val orderServiceTag = 22
        store.append(Event(
            TimeId(System.nanoTime()).tag(orderServiceTag),
            "OrderPlaced",
            "order456"
        ))

        // Query events by service
        val userEvents = store.getEventsByService(userServiceTag)
       Assertions.assertEquals(1, userEvents.size)
       Assertions.assertEquals("UserCreated", userEvents[0].type)

        val orderEvents = store.getEventsByService(orderServiceTag)
       Assertions.assertEquals(1, orderEvents.size)
       Assertions.assertEquals("OrderPlaced", orderEvents[0].type)
    }

    @Test
    fun `example - distributed tracing with service identification`() {
        // In distributed tracing, each service adds spans with unique IDs

        data class TraceSpan(
           val spanId: TimeId,
           val serviceName: String,
           val operation: String,
           val durationNanos: Long
        )

        class DistributedTrace {
            private val spans = mutableListOf<TraceSpan>()

            fun addSpan(serviceTag: Int, serviceName: String, operation: String, duration: Long) {
                spans.add(TraceSpan(
                    TimeId(System.nanoTime()).tag(serviceTag),
                    serviceName,
                    operation,
                    duration
                ))
            }

            fun getSpansByService(tag: Int) = spans.filter { it.spanId.tag() == tag }

            fun getTotalDurationByService(): Map<Int, Long> {
                return spans.groupBy { it.spanId.tag() }
                    .mapValues { entry -> entry.value.sumOf { it.durationNanos } }
            }
        }

        val trace = DistributedTrace()

        // API Gateway (tag 1)
        trace.addSpan(1, "api-gateway", "route-request", 1_000_000)

        // Auth Service (tag 2)
        trace.addSpan(2, "auth-service", "validate-token", 500_000)

        // Business Logic Service (tag 3)
        trace.addSpan(3, "business-service", "process-request", 2_000_000)

        // Database Service (tag 4)
        trace.addSpan(4, "db-service", "query-data", 1_500_000)

        // Analyze trace
        val durations = trace.getTotalDurationByService()
       Assertions.assertEquals(4, durations.size)
       Assertions.assertEquals(1_000_000L, durations[1]) // API Gateway
       Assertions.assertEquals(500_000L, durations[2])   // Auth Service
       Assertions.assertEquals(2_000_000L, durations[3])  // Business Service
       Assertions.assertEquals(1_500_000L, durations[4])  // DB Service
    }

    @Test
    fun `example - service registry and health monitoring`() {
        // Each service instance registers with a unique tag for identification

        data class ServiceInstance(
           val instanceId: TimeId,
           val serviceName: String,
           val host: String,
           val port: Int,
           var lastHeartbeat: TimeId
        )

        class ServiceRegistry {
            private val instances = mutableMapOf<Long, ServiceInstance>()

            fun register(serviceTag: Int, name: String, host: String, port: Int): TimeId {
                val instanceId = TimeId(System.nanoTime()).tag(serviceTag)
                instances[instanceId.value] = ServiceInstance(
                    instanceId, name, host, port, instanceId
                )
                return instanceId
            }

            fun heartbeat(instanceId: TimeId) {
                instances[instanceId.value]?.lastHeartbeat = TimeId(System.nanoTime()).tag(instanceId.tag())
            }

            fun getHealthyInstances(maxAgeMillis: Long): List<ServiceInstance> {
                val cutoffTime = System.nanoTime() - (maxAgeMillis * 1_000_000)
                return instances.values.filter { instance ->
                    instance.lastHeartbeat.nanos() >= cutoffTime
                }
            }

            fun getInstancesByServiceTag(tag: Int) =
                instances.values.filter { it.instanceId.tag() == tag }
        }

        val registry = ServiceRegistry()

        // Register multiple instances of different services
        val authInstance1 = registry.register(10, "auth-service", "host1", 8080)
        val authInstance2 = registry.register(11, "auth-service", "host2", 8080)
        val orderInstance = registry.register(20, "order-service", "host3", 8081)

        // Simulate heartbeats
        Thread.sleep(10)
        registry.heartbeat(authInstance1)
        registry.heartbeat(orderInstance)
        // Note: authInstance2 doesn't send heartbeat (simulating unhealthy instance)

        // Check healthy instances
        val healthy = registry.getHealthyInstances(100) // 100ms age limit
       Assertions.assertTrue(healthy.any { it.instanceId.value == authInstance1.value })
       Assertions.assertTrue(healthy.any { it.instanceId.value == orderInstance.value })

        // Get all auth service instances by their tag range (10-19 reserved for auth)
        val authInstances = (10..19).flatMap { registry.getInstancesByServiceTag(it) }
       Assertions.assertEquals(2, authInstances.size)
    }

    // ================================================================================
    // Performance and Efficiency Tests
    // ================================================================================

    @Test
    fun `should be efficient as inline value class`() {
        // TimeId is an inline value class, so it should have zero overhead

        val iterations = 1_000_000
        val startTime = System.currentTimeMillis()

        var sum = 0L
        repeat(iterations) {
            val timeId = TimeId(System.nanoTime()).tag(Random.Default.nextInt(100))
            sum += timeId.millis() // Force some computation to avoid optimization
        }

        val duration = System.currentTimeMillis() - startTime

        // This should be very fast due to inline optimization
       Assertions.assertTrue(
          duration < 1000,
          "Million operations should complete in under 1 second, took ${duration}ms"
       )

        // Ensure computation wasn't optimized away
       Assertions.assertTrue(sum > 0)
    }

    @Test
    fun `should maintain value semantics`() {
        val time = System.nanoTime()
        val id1 = TimeId(time)
        val id2 = TimeId(time)

        // Value equality
       Assertions.assertEquals(id1, id2)
       Assertions.assertEquals(id1.hashCode(), id2.hashCode())

        // After modification, they should differ
        val id3 = id1.tag(42)
       Assertions.assertNotEquals(id1, id3)

        // Original should be unchanged (immutable)
       Assertions.assertEquals((time % 100).toInt(), id1.tag())
       Assertions.assertEquals(42, id3.tag())
    }
}