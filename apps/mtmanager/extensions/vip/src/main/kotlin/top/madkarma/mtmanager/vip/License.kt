// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("License")
@file:Suppress("unused")

package top.madkarma.mtmanager.vip

import android.util.Log
import app.reseam.runtime.Export
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

// The file opener requires a valid local license, and the code editor requires that license to
// match the account's VIP expiry. Both read one shared far-future expiry.
private const val VIP_EXPIRY = Long.MAX_VALUE

private class ExpiryState(private val state: Any) : InvocationHandler {
    @Volatile private var expiryReads = emptySet<Pair<Method, Any?>>()
    private var recordingThread: Thread? = null
    private var recordedRead: Pair<Method, Any?>? = null

    override fun invoke(proxy: Any, method: Method, arguments: Array<out Any?>?): Any? {
        val read = method to arguments?.singleOrNull()
        if (read in expiryReads) return VIP_EXPIRY

        if (recordingThread === Thread.currentThread() && arguments?.size == 1)
            recordedRead = recordedRead ?: read

        return try {
            method.invoke(state, *(arguments ?: emptyArray()))
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    @Synchronized
    fun cover(getter: Method) {
        recordedRead = null
        recordingThread = Thread.currentThread()

        try {
            getter.invoke(null)
        } finally {
            recordingThread = null
        }

        val read = checkNotNull(recordedRead) { "${getter.name} did not read its state" }
        expiryReads = expiryReads + read
    }
}

@Export
fun track(state: Any, interfaceName: String): Any {
    val stateInterface = Class.forName(interfaceName)
    check(stateInterface.isInstance(state)) { "Unexpected state map" }

    return Proxy.newProxyInstance(
        stateInterface.classLoader,
        arrayOf(stateInterface),
        ExpiryState(state),
    )
}

// Learns which entry a getter reads, then serves the VIP expiry for that entry.
@Export
fun coverExpiry(trackedState: Any, owner: String, getter: String) {
    val handler = Proxy.getInvocationHandler(trackedState) as ExpiryState
    val method = Class.forName(owner).getDeclaredMethod(getter).apply { isAccessible = true }

    handler.cover(method)
    Log.i("MTManagerVIP", "VIP expiry covers $owner.$getter")
}
