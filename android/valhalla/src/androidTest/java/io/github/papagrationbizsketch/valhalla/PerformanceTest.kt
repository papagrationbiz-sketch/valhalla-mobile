package io.github.papagrationbizsketch.valhalla

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records a cross-platform performance baseline (`contract-tests/examples/performance-baseline.json`
 * schema) for the Android bindings so it can be compared against the iOS run in CI.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
  private lateinit var context: Context
  private lateinit var configFile: File
  private lateinit var routeRequest: String

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    val tileArchive = copyAsset(TILE_ASSET)
    val config =
        context.assets.open("config.json").bufferedReader().use { reader ->
          JSONObject(reader.readText())
        }
    config.getJSONObject("mjolnir").put("tile_extract", tileArchive.absolutePath)
    configFile = File(context.filesDir, "valhalla.json").apply { writeText(config.toString()) }
    routeRequest = readAsset("requests/route-auto.json")
  }

  @Test
  fun recordsPerformanceBaseline() {
    val fixtureId = JSONObject(readAsset(MANIFEST_ASSET)).getString("fixture_id")
    val tileSha256 = sha256OfAsset(TILE_ASSET)

    var coldRouteMs = 0.0
    val warmRouteMs = DoubleArray(WARM_ITERATIONS)
    var failures = 0

    Valhalla(configFile.absolutePath).use { valhalla ->
      val coldStart = System.nanoTime()
      dispatchRoute(valhalla)
      coldRouteMs = (System.nanoTime() - coldStart) / NANOS_PER_MILLI

      repeat(WARM_ITERATIONS) { index ->
        val start = System.nanoTime()
        val failed =
            try {
              isFailureResponse(JSONObject(valhalla.route(routeRequest)))
            } catch (error: Throwable) {
              true
            }
        warmRouteMs[index] = (System.nanoTime() - start) / NANOS_PER_MILLI
        if (failed) failures++
      }
    }

    val metrics =
        JSONObject()
            .put("cold_route_ms", coldRouteMs)
            .put("warm_route_ms_p50", median(warmRouteMs))
            .put("peak_rss_mb", peakRssMb())
            .put("iterations", WARM_ITERATIONS)
            .put("failures", failures)

    // valhalla_commit is deliberately omitted here: CI injects it after the run, since the
    // native commit hash isn't known to the instrumentation test itself.
    val result =
        JSONObject()
            .put("schema_version", 1)
            .put("platform", "android")
            .put("fixture_id", fixtureId)
            .put("tile_sha256", tileSha256)
            .put("metrics", metrics)

    val outputFile = File(outputDir(), "performance.json")
    outputFile.writeText(result.toString())
    Log.i(TAG, "Wrote performance baseline to ${outputFile.absolutePath}")
  }

  private fun dispatchRoute(valhalla: Valhalla): String = valhalla.route(routeRequest)

  private fun isFailureResponse(json: JSONObject): Boolean {
    if (json.has("error")) return true
    if (json.has("trip") && json.getJSONObject("trip").optInt("status", 0) != 0) return true
    return false
  }

  private fun median(values: DoubleArray): Double {
    val sorted = values.sortedArray()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
      (sorted[middle - 1] + sorted[middle]) / 2.0
    } else {
      sorted[middle]
    }
  }

  private fun peakRssMb(): Double {
    val statusFile = File("/proc/self/status")
    val vmHwmLine =
        statusFile.readLines().firstOrNull { line -> line.startsWith("VmHWM:") }
            ?: run {
              fail("Could not find VmHWM in /proc/self/status; refusing to fabricate peak_rss_mb")
              return 0.0
            }
    val kilobytes =
        vmHwmLine.removePrefix("VmHWM:").trim().removeSuffix("kB").trim().toDouble()
    return kilobytes / 1024.0
  }

  private fun sha256OfAsset(name: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    context.assets.open(name).use { input ->
      val buffer = ByteArray(SHA256_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
  }

  private fun readAsset(path: String): String =
      context.assets.open(path).bufferedReader().use { it.readText() }

  private fun copyAsset(name: String): File =
      File(context.filesDir, name).also { output ->
        context.assets.open(name).use { input ->
          output.outputStream().use { stream -> input.copyTo(stream) }
        }
      }

  private fun outputDir(): File = contractResultsDir()

  private companion object {
    const val TAG = "PerformanceTest"
    const val MANIFEST_ASSET = "manifest.json"
    const val TILE_ASSET = "valhalla_tiles.tar"
    const val WARM_ITERATIONS = 100
    const val SHA256_BUFFER_SIZE = 8192
    const val NANOS_PER_MILLI = 1_000_000.0
  }
}
