// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.weather

import app.reseam.patch.*

private val weatherRepository =
    klass("weatherRepository") {
        strings("Weather not fetched yet")
    }

internal val weatherContext = weatherRepository.fieldOfType(Type.Context)

internal val weatherFetch =
    method("weatherFetch") {
        strings("location missing")
    }

// Newer repositories extract parsing into a method; older releases parse inside cache-reader
// coroutines.
internal val responseParsers =
    methods("responseParsers") {
        inClass(weatherRepository)
        calls {
            owner($$"bitpit.launcher.backend.weather.model.WeatherResponse$Companion")
            name("serializer")
        }
    }

internal val parseResponse = responseParsers.single()

internal val readCachedResponse =
    method("readCachedResponse") {
        strings("Cache expired")
        params(Type.Object)
    }

internal val cachedResponseConstructor =
    method("cachedResponseConstructor") {
        inClass(klass(readCachedResponse.owner))
        name("<init>")
        hasParam(weatherRepository.descriptor)
    }

private val cachedResultConstructor =
    method("cachedResultConstructor") {
        calledBy(readCachedResponse)
        name("<init>")
        params(Type.Object)
    }

internal val cachedResultValue =
    fieldTarget("cachedResultValue") {
        klass(cachedResultConstructor.owner).fieldOfType(Type.Object).ref
    }

internal val cacheResponse =
    method("cacheResponse") {
        inClass(weatherRepository)
        strings("weather2")
    }

// Only the publisher takes a single model object; refresh scheduling may live in a separate method.
internal val publishState =
    method("publishState") {
        inClass(weatherRepository)
        params(Type.Object)
    }

internal const val WEATHER_WIDGET_ARTICLE_URL =
    "https://help.niagaralauncher.app/article/105-weather-widget"

internal val weatherSheet =
    method("weatherSheet") {
        strings(WEATHER_WIDGET_ARTICLE_URL)
    }

// The other location-name readers only touch preferences; this one binds the settings views.
internal val weatherContentBind =
    method("weatherContentBind") {
        strings("bitpit.launcher.key.WEATHER_LOCATION_NAME")
        calls {
            owner("android.view.View")
            name("findViewById")
        }
    }
