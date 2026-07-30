package io.github.papagrationbizsketch.valhalla

import com.valhalla.valhalla.ValhallaKotlin

/**
 * Minimal Android entry point for the native Valhalla routing engine.
 *
 * @param configPath absolute path to a Valhalla JSON configuration file
 */
class Valhalla(private val configPath: String) {
  private val nativeBridge = ValhallaKotlin()

  /**
   * Runs a raw Valhalla route request.
   *
   * Both the request and response use Valhalla's JSON wire format. Native errors are returned as
   * JSON objects containing `code` and `message`.
   */
  fun route(requestJson: String): String = nativeBridge.route(requestJson, configPath)
}
