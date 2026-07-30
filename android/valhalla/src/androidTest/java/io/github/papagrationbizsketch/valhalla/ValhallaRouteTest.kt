package io.github.papagrationbizsketch.valhalla

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
  fun nativeLibraryLoadsAndRoutesFixedFixture() {
    val request =
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

    val response = JSONObject(Valhalla(configFile.absolutePath).route(request))
    val trip = response.getJSONObject("trip")

    assertEquals(0, trip.getInt("status"))
    assertEquals("Found route between points", trip.getString("status_message"))
  }

  @Test
  fun invalidConfigReturnsJsonErrorWithoutNativeCrash() {
    val response =
        JSONObject(
            Valhalla(File(context.filesDir, "missing.json").absolutePath)
                .route("""{"locations":[],"costing":"auto"}""")
        )

    assertEquals(-1, response.getInt("code"))
    assertTrue(response.getString("message").contains("missing.json"))
  }

  private fun copyAsset(name: String): File =
      File(context.filesDir, name).also { output ->
        context.assets.open(name).use { input ->
          output.outputStream().use { stream -> input.copyTo(stream) }
        }
      }
}
