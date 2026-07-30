package io.github.papagrationbizsketch.valhalla.consumersmoke

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.github.papagrationbizsketch.valhalla.Valhalla

class RouteProvider : ContentProvider() {
  override fun onCreate(): Boolean = true

  override fun call(method: String, argument: String?, extras: Bundle?): Bundle {
    require(method == ROUTE_METHOD)
    val configPath = requireNotNull(argument)
    val requestJson = requireNotNull(extras).getString(REQUEST_KEY).orEmpty()
    val response = Valhalla(configPath).use { valhalla -> valhalla.route(requestJson) }
    return Bundle().apply { putString(RESPONSE_KEY, response) }
  }

  override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
  ): Cursor? = null

  override fun getType(uri: Uri): String? = null

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

  override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
  ): Int = 0

  companion object {
    const val AUTHORITY = "io.github.papagrationbizsketch.valhalla.consumersmoke.route"
    const val ROUTE_METHOD = "route"
    const val REQUEST_KEY = "request"
    const val RESPONSE_KEY = "response"
  }
}
