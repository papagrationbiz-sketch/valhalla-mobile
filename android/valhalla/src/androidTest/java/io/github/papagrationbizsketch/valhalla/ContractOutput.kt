package io.github.papagrationbizsketch.valhalla

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

private const val ADDITIONAL_TEST_OUTPUT_DIR = "additionalTestOutputDir"

/**
 * Directory the contract and performance runners write their results to.
 *
 * Prefers the directory AGP passes as `additionalTestOutputDir`, because AGP copies it off the
 * device before uninstalling the test package. The app-private external directory used as the
 * fallback is removed together with the package, so results written there do not survive a
 * Gradle-driven run.
 */
internal fun contractResultsDir(): File {
  val additionalTestOutputDir =
      InstrumentationRegistry.getArguments().getString(ADDITIONAL_TEST_OUTPUT_DIR)
  val base =
      if (additionalTestOutputDir != null) File(additionalTestOutputDir)
      else
          InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
              ?: error("No external files directory available for contract results")
  return File(base, "contract-results").apply { mkdirs() }
}
