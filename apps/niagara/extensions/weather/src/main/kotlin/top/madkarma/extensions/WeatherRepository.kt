// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("WeatherRepository") @file:Suppress("unused")

package top.madkarma.extensions

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export
import java.lang.reflect.InvocationTargetException

/** Uses members resolved by the patch, including older static cache writers and coroutine parsers. */
@Export
fun fetchForecast(
    repository: Any,
    context: Context,
    parserName: String,
    cacheName: String,
    publisherName: String,
    readerClassName: String,
    readerMethodName: String,
    readerResultFieldName: String,
): Any? {
    try {
        val api = WeatherApi.create(context)
        val json = api.fetch()

        val type = repository.javaClass

        val forecast = if (parserName.isNotEmpty()) {
            val parser = type.getDeclaredMethod(parserName, String::class.java, java.lang.Long.TYPE)
            parser.isAccessible = true

            val parsed = parser.invoke(repository, json, api.now())
            writeCache(repository, cacheName, json)
            parsed
        } else {
            writeCache(repository, cacheName, json)
            readCache(repository, readerClassName, readerMethodName, readerResultFieldName)
        }

        val publisher = type.getDeclaredMethod(publisherName, Any::class.java)
        publisher.isAccessible = true
        publisher.invoke(repository, forecast)

        Log.d("NiagaraWeather", "Published WeatherAPI.com forecast")
        return forecast
    } catch (exception: InvocationTargetException) {
        throw exception.targetException
    } catch (exception: Exception) {
        Log.w("NiagaraWeather", "Weather refresh failed", exception)
        throw exception
    }
}

private fun writeCache(repository: Any, name: String, json: String?) {
    val type = repository.javaClass

    val writer = try {
        type.getDeclaredMethod(name, String::class.java)
    } catch (_: NoSuchMethodException) {
        val staticWriter = type.getDeclaredMethod(name, type, String::class.java)
        staticWriter.isAccessible = true
        staticWriter.invoke(null, repository, json)
        return
    }

    writer.isAccessible = true
    writer.invoke(repository, json)
}

private fun readCache(
    repository: Any, className: String, methodName: String, resultFieldName: String
): Any? {
    val type = Class.forName(className, true, repository.javaClass.classLoader)

    val constructor = type.declaredConstructors.single { repository.javaClass in it.parameterTypes }
    constructor.isAccessible = true

    val arguments =
        constructor.parameterTypes.map { if (it == repository.javaClass) repository else null }
    val reader = constructor.newInstance(*arguments.toTypedArray())

    val method = type.getDeclaredMethod(methodName, Any::class.java)
    method.isAccessible = true

    val result =
        checkNotNull(method.invoke(reader, null)) { "Weather cache reader returned no result" }

    val value = result.javaClass.getDeclaredField(resultFieldName)
    value.isAccessible = true
    return value.get(result)
}
