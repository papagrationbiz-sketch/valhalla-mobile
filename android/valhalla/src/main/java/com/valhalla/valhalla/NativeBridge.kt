package com.valhalla.valhalla

internal interface NativeBridge {
  fun create(configPathUtf8: ByteArray): Long

  fun destroy(handle: Long)

  fun route(handle: Long, requestUtf8: ByteArray): ByteArray

  fun traceAttributes(handle: Long, requestUtf8: ByteArray): ByteArray

  fun traceRoute(handle: Long, requestUtf8: ByteArray): ByteArray
}

internal class ValhallaNative : NativeBridge {
  companion object {
    init {
      System.loadLibrary("valhalla-wrapper")
    }
  }

  external override fun create(configPathUtf8: ByteArray): Long

  external override fun destroy(handle: Long)

  external override fun route(handle: Long, requestUtf8: ByteArray): ByteArray

  external override fun traceAttributes(handle: Long, requestUtf8: ByteArray): ByteArray

  external override fun traceRoute(handle: Long, requestUtf8: ByteArray): ByteArray
}
