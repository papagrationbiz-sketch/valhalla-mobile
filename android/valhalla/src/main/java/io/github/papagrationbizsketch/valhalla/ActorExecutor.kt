package io.github.papagrationbizsketch.valhalla

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal interface ActorExecutor : AutoCloseable {
  fun <T> execute(block: () -> T): T

  override fun close()
}

internal class SingleThreadActorExecutor : ActorExecutor {
  private val actorThread = AtomicReference<Thread>()
  private val executor: ExecutorService =
      Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "valhalla-actor").also(actorThread::set)
      }

  override fun <T> execute(block: () -> T): T {
    if (Thread.currentThread() === actorThread.get()) {
      return block()
    }

    return try {
      executor.submit(Callable(block)).get()
    } catch (error: ExecutionException) {
      throw error.cause ?: error
    }
  }

  override fun close() {
    executor.shutdown()
    if (Thread.currentThread() !== actorThread.get()) {
      check(executor.awaitTermination(30, TimeUnit.SECONDS)) {
        "Valhalla actor executor did not terminate"
      }
    }
  }
}
