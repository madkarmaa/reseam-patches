// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Network")
@file:Suppress("unused")

package top.madkarma.mtmanager.vip

import app.reseam.runtime.Export
import java.lang.reflect.InvocationTargetException

@Export
fun proceed(chain: Any, requestName: String, proceedName: String): Any {
    try {
        val requestMethod = chain.javaClass.getMethod(requestName)
        val proceedMethod = chain.javaClass.getMethod(proceedName, requestMethod.returnType)
        return checkNotNull(proceedMethod.invoke(chain, requestMethod.invoke(chain)))
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    }
}
