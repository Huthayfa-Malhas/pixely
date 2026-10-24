/*
 * Pixely ships without Google's datatransport library. MediaPipe's tasks-core depends on it to send
 * usage statistics, and its embedder always builds a client for that. These do-nothing stand-ins
 * cover exactly what MediaPipe 1.1.1 calls (RemoteLoggingClient), so nothing is queued, stored on
 * the phone or scheduled for upload. A MediaPipe upgrade that calls more fails the on-phone tests
 * with NoSuchMethodError; add the member here.
 */
package com.google.android.datatransport

class Encoding private constructor() {
    companion object {
        @JvmStatic fun of(name: String): Encoding = Encoding()
    }
}

abstract class Event<T> {
    companion object {
        @JvmStatic fun <T> ofData(data: T): Event<T> = object : Event<T>() {}
    }
}

fun interface Transformer<T, U> {
    fun apply(input: T): U
}

fun interface Transport<T> {
    fun send(event: Event<T>)
}

interface TransportFactory {
    fun <T> getTransport(name: String, payloadType: Class<T>, encoding: Encoding, transformer: Transformer<T, ByteArray>): Transport<T>
}
