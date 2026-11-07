package com.myplanner.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDateTime
import java.time.LocalTime

/** Настройки приложения. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun str(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun bool(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun int(k: String, d: Int) = sp.getInt(k, d)
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun lng(k: String, d: Long) = sp.getLong(k, d)
    private fun put(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    var theme: String                       // system / light / dark
        get() = str("theme", "system")
        set(v) = put("theme", v)
    var speechLang: String
        get() = str("speech_lang", "")
        set(v) = put("speech_lang", v)
    var ttsEnabled: Boolean
        get() = bool("tts", true)
        set(v) = put("tts", v)
    var soundEnabled: Boolean
        get() = bool("sound", true)
        set(v) = put("sound", v)
    var soundId: String
        get() = str("sound_id", "soft")
        set(v) = put("sound_id", v)
    var repeatMinutes: Int
        get() = int("repeat_min", 10)
        set(v) = put("repeat_min", v)
    var warnEnabled: Boolean
        get() = bool("warn_on", true)
        set(v) = put("warn_on", v)
    var warnMinutes: Int
        get() = int("warn_min", 120)
        set(v) = put("warn_min", v)
    var warnText: String
        get() = str("warn_text", "")
        set(v) = put("warn_text", v)
    var city: String
        get() = str("city", "")
        set(v) = put("city", v)
    var quietHoursOn: Boolean
        get() = bool("qh_on", false)
        set(v) = put("qh_on", v)
    var quietFrom: String
        get() = str("qh_from", "22:00")
        set(v) = put("qh_from", v)
    var quietTo: String
        get() = str("qh_to", "08:00")
        set(v) = put("qh_to", v)
    var quietUntil: Long
        get() = lng("quiet_until", 0L)
        set(v) = put("quiet_until", v)

    // кэш погоды
    var wCity: String
        get() = str("w_city", "")
        set(v) = put("w_city", v)
    var wTemp: Int
        get() = int("w_temp", 0)
        set(v) = put("w_temp", v)
    var wCode: Int
        get() = int("w_code", -1)
        set(v) = put("w_code", v)
    var wTime: Long
        get() = lng("w_time", 0L)
        set(v) = put("w_time", v)

    fun nightMode(): Int = when (theme) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** За сколько минут предупреждать по этой задаче (0 — не предупреждать). */
    fun effectiveWarn(t: TaskItem): Int = when {
        !warnEnabled -> 0
        t.warnMinutes < 0 -> warnMinutes
        else -> t.warnMinutes
    }

    private fun parse(s: String, d: LocalTime): LocalTime = try { LocalTime.parse(s) } catch (e: Exception) { d }

    /** Звук сейчас отключён (тихий режим или тихие часы)? */
    fun isQuiet(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (System.currentTimeMillis() < quietUntil) return true
        if (!quietHoursOn) return false
        val from = parse(quietFrom, LocalTime.of(22, 0))
        val to = parse(quietTo, LocalTime.of(8, 0))
        val t = now.toLocalTime()
        return if (from <= to) (t >= from && t < to) else (t >= from || t < to)
    }
}
