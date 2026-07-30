package io.github.papagrationbizsketch.valhalla

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the shared cross-platform contract fixtures (`contract-tests/fixtures/manifest.json`)
 * against the Android bindings and records each case's raw response for offline comparison
 * against the iOS output via `contract-tests/tools/verify.py`.
 */
@RunWith(AndroidJUnit4::class)
class ContractTest {
  private lateinit var context: Context
  private lateinit var configFile: File

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    val tileArchive = copyAsset("valhalla_tiles.tar")
    val config =
        context.assets.open("config.json").bufferedReader().use { reader ->
          JSONObject(reader.readText())
        }
    config.getJSONObject("mjolnir").put("tile_extract", tileArchive.absolutePath)
    configFile = File(context.filesDir, "valhalla.json").apply { writeText(config.toString()) }
  }

  @Test
  fun runsAllContractCases() {
    val manifest = JSONObject(readAsset(MANIFEST_ASSET))
    val cases = manifest.getJSONArray("cases")
    val outputDir = outputDir()
    Log.i(TAG, "Writing contract results to ${outputDir.absolutePath}")

    Valhalla(configFile.absolutePath).use { valhalla ->
      for (index in 0 until cases.length()) {
        runCase(valhalla, cases.getJSONObject(index), outputDir)
      }
    }
  }

  private fun runCase(valhalla: Valhalla, case: JSONObject, outputDir: File) {
    val caseId = case.getString("id")
    val endpoint = case.getString("endpoint")
    val requestJson = readAsset(case.getString("request"))
    val comparisonProfile = case.getString("comparison_profile")
    val execution = case.optJSONObject("execution")

    val response =
        try {
          if (execution != null) runActorLifecycleCase(endpoint, requestJson, execution)
          else dispatch(valhalla, endpoint, requestJson)
        } catch (error: Throwable) {
          throw AssertionError("Case '$caseId' threw: ${error.message}", error)
        }

    File(outputDir, "$caseId.json").writeText(response)

    // The invalid-request case is expected to surface an error object; every other case must
    // succeed against real tiles.
    if (comparisonProfile != "error") {
      assertSuccessfulResponse(caseId, response)
    }
  }

  /**
   * Runs [execution.reuse_count] requests on a single actor, then (when [reinitialize_after] is
   * set) closes and recreates the actor and runs one more request, returning that final response.
   */
  private fun runActorLifecycleCase(
      endpoint: String,
      requestJson: String,
      execution: JSONObject,
  ): String {
    val reuseCount = execution.getInt("reuse_count")
    val reinitializeAfter = execution.optBoolean("reinitialize_after", false)

    var lastResponse = ""
    Valhalla(configFile.absolutePath).use { actor ->
      repeat(reuseCount) { lastResponse = dispatch(actor, endpoint, requestJson) }
    }

    if (!reinitializeAfter) {
      return lastResponse
    }

    return Valhalla(configFile.absolutePath).use { actor -> dispatch(actor, endpoint, requestJson) }
  }

  private fun dispatch(valhalla: Valhalla, endpoint: String, requestJson: String): String =
      when (endpoint) {
        "route" -> valhalla.route(requestJson)
        "trace_attributes" -> valhalla.traceAttributes(requestJson)
        "trace_route" -> valhalla.traceRoute(requestJson)
        else -> error("Unknown contract endpoint: $endpoint")
      }

  private fun assertSuccessfulResponse(caseId: String, response: String) {
    val json = JSONObject(response)
    assertFalse("$caseId: response contains a top-level 'error': $response", json.has("error"))
    if (json.has("trip")) {
      assertEquals(
          "$caseId: trip.status was non-zero: $response",
          0,
          json.getJSONObject("trip").optInt("status", 0),
      )
    }
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
    const val TAG = "ContractTest"
    const val MANIFEST_ASSET = "manifest.json"
  }
}
