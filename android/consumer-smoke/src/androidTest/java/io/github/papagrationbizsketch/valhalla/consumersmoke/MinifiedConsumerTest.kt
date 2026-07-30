package io.github.papagrationbizsketch.valhalla.consumersmoke

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinifiedConsumerTest {
  @Test
  fun minifiedConsumerLoadsRegisteredNativeMethodsAndRoutes() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val assets = instrumentation.context.assets
    val tileArchive = copyAsset(context, assets, "valhalla_tiles.tar")
    val config =
        assets.open("config.json").bufferedReader().use { reader ->
          JSONObject(reader.readText())
        }
    config.getJSONObject("mjolnir").put("tile_extract", tileArchive.absolutePath)
    val configFile =
        File(context.filesDir, "valhalla.json").apply { writeText(config.toString()) }

    val extras = Bundle().apply { putString(RouteProvider.REQUEST_KEY, ROUTE_REQUEST) }
    val result =
        context.contentResolver.call(
            Uri.parse("content://${RouteProvider.AUTHORITY}"),
            RouteProvider.ROUTE_METHOD,
            configFile.absolutePath,
            extras,
        )
    val response = JSONObject(requireNotNull(result).getString(RouteProvider.RESPONSE_KEY).orEmpty())
    assertEquals(0, response.getJSONObject("trip").getInt("status"))
  }

  private fun copyAsset(context: Context, assets: AssetManager, name: String): File =
      File(context.filesDir, name).also { output ->
        assets.open(name).use { input ->
          output.outputStream().use { stream -> input.copyTo(stream) }
        }
      }

  private companion object {
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
