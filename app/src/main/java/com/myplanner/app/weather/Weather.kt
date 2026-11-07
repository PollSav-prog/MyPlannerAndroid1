package com.myplanner.app.weather

import com.myplanner.app.App
import com.myplanner.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherInfo(val city: String, val temp: Int, val code: Int, val time: Long)

/** Погода из Open-Meteo (бесплатно, без ключа). */
object Weather {
    fun cached(): WeatherInfo? {
        val p = App.prefs
        return if (p.wCode >= 0 && p.wCity.isNotEmpty()) WeatherInfo(p.wCity, p.wTemp, p.wCode, p.wTime) else null
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** null — нет сети; WeatherInfo с code = -2 — город не найден. */
    suspend fun fetch(city: String, lang: String): WeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val q = URLEncoder.encode(city.trim(), "UTF-8")
            val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=$lang"))
            val results = geo.optJSONArray("results")
            if (results == null || results.length() == 0) return@withContext WeatherInfo(city, 0, -2, 0)
            val g = results.getJSONObject(0)
            val lat = g.getDouble("latitude")
            val lon = g.getDouble("longitude")
            val name = g.optString("name", city)
            val w = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"))
            val cur = w.getJSONObject("current")
            val info = WeatherInfo(name, Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), System.currentTimeMillis())
            App.prefs.wCity = info.city
            App.prefs.wTemp = info.temp
            App.prefs.wCode = info.code
            App.prefs.wTime = info.time
            info
        } catch (e: Exception) {
            null
        }
    }

    fun wxRes(code: Int): Int = when (code) {
        0 -> R.string.wx_clear
        1, 2 -> R.string.wx_partly
        3 -> R.string.wx_cloudy
        45, 48 -> R.string.wx_fog
        in 51..57 -> R.string.wx_drizzle
        in 61..67 -> R.string.wx_rain
        in 71..77, 85, 86 -> R.string.wx_snow
        in 80..82 -> R.string.wx_showers
        in 95..99 -> R.string.wx_thunder
        else -> R.string.wx_unknown
    }
}
