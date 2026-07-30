package io.github.papagrationbizsketch.valhalla

import com.valhalla.valhalla.NativeBridge
import com.valhalla.valhalla.ValhallaNative
import java.io.Closeable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persistent Android entry point for the native Valhalla routing engine.
 *
 * @param configPath absolute path to a Valhalla JSON configuration file
 */
class Valhalla
private constructor(
    configPath: String,
    private val nativeBridge: NativeBridge,
    private val actorExecutor: ActorExecutor,
) : Closeable {
  private val lifecycleLock = ReentrantLock()
  private var closed = false
  private val handle: Long =
      try {
        actorExecutor.execute { nativeBridge.create(configPath.toByteArray(Charsets.UTF_8)) }.also {
          check(it != 0L) { "Native Valhalla actor registry did not return a handle" }
        }
      } catch (error: Throwable) {
        try {
          actorExecutor.close()
        } catch (closeError: Throwable) {
          error.addSuppressed(closeError)
        }
        throw error
      }

  constructor(configPath: String) : this(configPath, ValhallaNative(), SingleThreadActorExecutor())

  /**
   * Runs a raw Valhalla route request.
   *
   * Both the request and response use Valhalla's JSON wire format. Native errors are returned as
   * JSON objects containing `code` and `message`.
   */
  fun route(requestJson: String): String =
      execute { nativeBridge.route(handle, requestJson.toByteArray(Charsets.UTF_8)) }

  /** Runs a raw Valhalla trace_attributes request using this instance's persistent actor. */
  fun traceAttributes(requestJson: String): String =
      execute { nativeBridge.traceAttributes(handle, requestJson.toByteArray(Charsets.UTF_8)) }

  /** Runs a raw Valhalla trace_route request using this instance's persistent actor. */
  fun traceRoute(requestJson: String): String =
      execute { nativeBridge.traceRoute(handle, requestJson.toByteArray(Charsets.UTF_8)) }

  override fun close() {
    lifecycleLock.withLock {
      if (closed) {
        return
      }
      closed = true
      try {
        actorExecutor.execute { nativeBridge.destroy(handle) }
      } finally {
        actorExecutor.close()
      }
    }
  }

  private fun execute(operation: () -> ByteArray): String =
      lifecycleLock.withLock {
        check(!closed) { CLOSED_MESSAGE }
        actorExecutor.execute(operation).toString(Charsets.UTF_8)
      }

  internal companion object {
    private const val CLOSED_MESSAGE = "Valhalla is closed"

    fun createForTesting(
        configPath: String,
        nativeBridge: NativeBridge,
        actorExecutor: ActorExecutor,
    ): Valhalla = Valhalla(configPath, nativeBridge, actorExecutor)
  }
}
