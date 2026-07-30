package io.github.papagrationbizsketch.valhalla

import android.content.Context
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValhallaRouteTest {
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
  fun oneActorRunsRouteAndBothTraceApis() {
    Valhalla(configFile.absolutePath).use { valhalla ->
      val route = successfulRoute(valhalla)
      val encodedShape =
          route
              .getJSONObject("trip")
              .getJSONArray("legs")
              .getJSONObject(0)
              .getString("shape")
      val traceRequest =
          JSONObject()
              .put("encoded_polyline", encodedShape)
              .put("costing", "auto")
              .put("shape_match", "map_snap")
              .toString()

      val attributes = JSONObject(valhalla.traceAttributes(traceRequest))
      assertFalse(attributes.toString(), attributes.has("code"))
      assertTrue(attributes.has("edges"))
      assertTrue(attributes.getJSONArray("edges").length() > 0)

      val traceRoute = JSONObject(valhalla.traceRoute(traceRequest))
      assertFalse(traceRoute.toString(), traceRoute.has("code"))
      assertEquals(0, traceRoute.getJSONObject("trip").getInt("status"))
    }
  }

  @Test
  fun oneActorRoutesOneHundredTimesWithoutSignificantNativeHeapGrowth() {
    Valhalla(configFile.absolutePath).use { valhalla ->
      repeat(WARMUP_ROUTES) { successfulRoute(valhalla) }
      Runtime.getRuntime().gc()
      val baseline = Debug.getNativeHeapAllocatedSize()

      repeat(MEASURED_ROUTES) { successfulRoute(valhalla) }
      Runtime.getRuntime().gc()
      val growth = Debug.getNativeHeapAllocatedSize() - baseline
      val allowedGrowth = max(MINIMUM_ALLOWED_GROWTH_BYTES, baseline / 10)

      assertTrue(
          "native heap grew by $growth bytes; allowed $allowedGrowth from baseline $baseline",
          growth <= allowedGrowth,
      )
    }
  }

  @Test
  fun concurrentCallersAreSerializedWithoutNativeFailure() {
    Valhalla(configFile.absolutePath).use { valhalla ->
      val callers = Executors.newFixedThreadPool(8)
      try {
        val routes = List(16) { callers.submit<JSONObject> { successfulRoute(valhalla) } }
        routes.forEach { route -> assertEquals(0, route.get().getJSONObject("trip").getInt("status")) }
      } finally {
        callers.shutdown()
        assertTrue(callers.awaitTermination(30, TimeUnit.SECONDS))
      }
    }
  }

  @Test
  fun invalidConfigReturnsJsonErrorWithoutNativeCrash() {
    Valhalla(File(context.filesDir, "missing.json").absolutePath).use { valhalla ->
      val response = JSONObject(valhalla.route(ROUTE_REQUEST))

      assertEquals(-1, response.getInt("code"))
      assertTrue(response.getString("message").contains("missing.json"))
      assertEquals(response.toString(), JSONObject(valhalla.traceAttributes("{}")).toString())
      assertEquals(response.toString(), JSONObject(valhalla.traceRoute("{}")).toString())
    }
  }

  @Test
  fun closeIsIdempotentAndCallsAfterCloseFail() {
    val valhalla = Valhalla(configFile.absolutePath)
    successfulRoute(valhalla)

    valhalla.close()
    valhalla.close()

    val error = assertThrows(IllegalStateException::class.java) { valhalla.route(ROUTE_REQUEST) }
    assertEquals("Valhalla is closed", error.message)
  }

  private fun successfulRoute(valhalla: Valhalla): JSONObject =
      JSONObject(valhalla.route(ROUTE_REQUEST)).also { response ->
        val trip = response.getJSONObject("trip")
        assertEquals(0, trip.getInt("status"))
        assertEquals("Found route between points", trip.getString("status_message"))
      }

  private fun copyAsset(name: String): File =
      File(context.filesDir, name).also { output ->
        context.assets.open(name).use { input ->
          output.outputStream().use { stream -> input.copyTo(stream) }
        }
      }

  private companion object {
    const val WARMUP_ROUTES = 10
    const val MEASURED_ROUTES = 100
    const val MINIMUM_ALLOWED_GROWTH_BYTES = 16L * 1024L * 1024L
    val ROUTE_REQUEST =
        """
        {
          "locations": [
            {"lat": 42.5063, "lon": 1.5218},
            {"lat": 42.5086, "lon": 1.5394}
          ],
          "costing": "auto",
          "units": "miles"
        }
        """
            .trimIndent()
  }
}
