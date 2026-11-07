package com.myplanner.app.logic

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Форматирование дат и времени по языку интерфейса. */
object Fmt {
    private val supported = setOf("ru", "en", "de", "fr")

    fun locale(): Locale {
        val l = AppCompatDelegate.getApplicationLocales()
        val loc = if (l.isEmpty()) Locale.getDefault() else (l.get(0) ?: Locale.getDefault())
        return if (loc.language in supported) loc else Locale("ru")
    }

    fun time(t: LocalDateTime, loc: Locale = locale()): String =
        t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(loc))

    fun dateFull(d: LocalDate, loc: Locale = locale()): String =
        d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(loc))

    fun dateMedium(d: LocalDate, loc: Locale = locale()): String =
        d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc))

    fun dateTime(t: LocalDateTime): String = dateMedium(t.toLocalDate()) + ", " + time(t)

    /** Строка на указанном языке (для озвучивания, не зависит от языка интерфейса). */
    fun stringIn(ctx: Context, langTag: String, id: Int, vararg args: Any): String {
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(langTag))
        return ctx.createConfigurationContext(cfg).getString(id, *args)
    }
}
