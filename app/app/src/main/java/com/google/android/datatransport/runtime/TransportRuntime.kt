/* Do-nothing stand-in; see com/google/android/datatransport/NoTransport.kt. */
package com.google.android.datatransport.runtime

import android.content.Context
import com.google.android.datatransport.Transport
import com.google.android.datatransport.TransportFactory

interface Destination

class TransportRuntime private constructor() {

    fun newFactory(destination: Destination): TransportFactory = object : TransportFactory {
        override fun <T> getTransport(
            name: String,
            payloadType: Class<T>,
            encoding: com.google.android.datatransport.Encoding,
            transformer: com.google.android.datatransport.Transformer<T, ByteArray>,
        ): Transport<T> = Transport { }
    }

    companion object {
        private val instance = TransportRuntime()

        @JvmStatic fun initialize(context: Context) = Unit

        @JvmStatic fun getInstance(): TransportRuntime = instance
    }
}
