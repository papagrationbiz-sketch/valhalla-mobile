package io.github.papagrationbizsketch.valhalla

import com.valhalla.valhalla.NativeBridge
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaLifecycleTest {
  @Test
  fun zeroHandleClosesExecutorAndFailsConstruction() {
    val executor = TrackingExecutor(SingleThreadActorExecutor())
    val bridge = RecordingNativeBridge(createResult = 0L)

    val error =
        assertThrows(IllegalStateException::class.java) {
          Valhalla.createForTesting("config.json", bridge, executor)
        }

    assertEquals("Native Valhalla actor registry did not return a handle", error.message)
    assertTrue(executor.closed)
  }

  @Test
  fun createExceptionClosesExecutorAndRethrowsOriginal() {
    val executor = TrackingExecutor(SingleThreadActorExecutor())
    val expected = IllegalArgumentException("create failed")
    val bridge = RecordingNativeBridge(createError = expected)

    val actual =
        assertThrows(IllegalArgumentException::class.java) {
          Valhalla.createForTesting("config.json", bridge, executor)
        }

    assertTrue(actual === expected)
    assertTrue(executor.closed)
  }

  @Test
  fun nativeCallsUseOneThreadInSubmissionOrder() {
    val bridge = RecordingNativeBridge()
    val valhalla =
        Valhalla.createForTesting("config.json", bridge, SingleThreadActorExecutor())

    assertEquals("""{"operation":"route"}""", valhalla.route("{}"))
    assertEquals(
        """{"operation":"trace_attributes"}""",
        valhalla.traceAttributes("{}"),
    )
    assertEquals("""{"operation":"trace_route"}""", valhalla.traceRoute("{}"))
    valhalla.close()

    assertEquals(listOf("create", "route", "trace_attributes", "trace_route", "destroy"), bridge.calls)
    assertEquals(1, bridge.threadNames.toSet().size)
  }

  @Test
  fun closeIsIdempotentAndUseAfterCloseFails() {
    val bridge = RecordingNativeBridge()
    val valhalla =
        Valhalla.createForTesting("config.json", bridge, SingleThreadActorExecutor())

    valhalla.close()
    valhalla.close()

    assertEquals(1, bridge.calls.count { it == "destroy" })
    val error = assertThrows(IllegalStateException::class.java) { valhalla.route("{}") }
    assertEquals("Valhalla is closed", error.message)
  }

  @Test
  fun closeWaitsForRouteAndDeleteBeforeExecutorShutdown() {
    val routeStarted = CountDownLatch(1)
    val releaseRoute = CountDownLatch(1)
    val destroyStarted = CountDownLatch(1)
    val releaseDestroy = CountDownLatch(1)
    val bridge =
        RecordingNativeBridge(
            beforeRoute = {
              routeStarted.countDown()
              assertTrue(releaseRoute.await(5, TimeUnit.SECONDS))
            },
            beforeDestroy = {
              destroyStarted.countDown()
              assertTrue(releaseDestroy.await(5, TimeUnit.SECONDS))
            },
        )
    val executor = TrackingExecutor(SingleThreadActorExecutor())
    val valhalla = Valhalla.createForTesting("config.json", bridge, executor)

    val routeThread = thread { valhalla.route("{}") }
    assertTrue(routeStarted.await(5, TimeUnit.SECONDS))
    val closeThread = thread { valhalla.close() }
    assertFalse(destroyStarted.await(100, TimeUnit.MILLISECONDS))

    releaseRoute.countDown()
    routeThread.join(5_000)
    assertTrue(destroyStarted.await(5, TimeUnit.SECONDS))
    assertFalse(executor.closed)

    releaseDestroy.countDown()
    closeThread.join(5_000)
    assertTrue(executor.closed)
    assertEquals(listOf("create", "route", "destroy"), bridge.calls)
  }

  private class TrackingExecutor(private val delegate: ActorExecutor) : ActorExecutor {
    @Volatile var closed = false

    override fun <T> execute(block: () -> T): T = delegate.execute(block)

    override fun close() {
      delegate.close()
      closed = true
    }
  }

  private class RecordingNativeBridge(
      private val beforeRoute: () -> Unit = {},
      private val beforeDestroy: () -> Unit = {},
      private val createResult: Long = 41L,
      private val createError: Throwable? = null,
  ) : NativeBridge {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val threadNames: MutableList<String> = Collections.synchronizedList(mutableListOf())

    override fun create(configPathUtf8: ByteArray): Long {
      record("create")
      createError?.let { throw it }
      return createResult
    }

    override fun destroy(handle: Long) {
      beforeDestroy()
      record("destroy")
    }

    override fun route(handle: Long, requestUtf8: ByteArray): ByteArray {
      beforeRoute()
      record("route")
      return """{"operation":"route"}""".toByteArray()
    }

    override fun traceAttributes(handle: Long, requestUtf8: ByteArray): ByteArray {
      record("trace_attributes")
      return """{"operation":"trace_attributes"}""".toByteArray()
    }

    override fun traceRoute(handle: Long, requestUtf8: ByteArray): ByteArray {
      record("trace_route")
      return """{"operation":"trace_route"}""".toByteArray()
    }

    private fun record(operation: String) {
      calls += operation
      threadNames += Thread.currentThread().name
    }
  }
}
