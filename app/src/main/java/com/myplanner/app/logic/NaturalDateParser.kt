package com.myplanner.app.logic

import com.myplanner.app.data.RepeatKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class ParsedTask {
    var title: String = ""
    var date: LocalDate? = null
    var time: LocalTime? = null
    /** Час назван без «утра/вечера» — нужно подтверждение (например, «в три часа»). */
    var ambiguousHour: Boolean = false
    var repeat: RepeatKind = RepeatKind.None
    var repeatInterval: Int = 1
    var repeatDays: Int = 0
    val hasTime: Boolean get() = time != null
    val due: LocalDateTime get() = (date ?: LocalDate.now()).atTime(time ?: LocalTime.of(9, 0))
}

/**
 * Разбор фраз вида «Завтра в три часа позвонить врачу» на русском, английском, немецком и французском:
 * находит дату, время, повторение и название задачи.
 */
object NaturalDateParser {
    private class Rules {
        var numbers: Array<String> = emptyArray()
        var today = ""; var tomorrow = ""; var dayAfter = ""
        var weekdays: Array<String> = emptyArray()
        var months: Array<String> = emptyArray()
        var repDaily = ""; var repWeekly = ""; var repMonthly = ""; var repYearly = ""; var repEveryN = ""
        var repWeekdayPrefix = ""
        var weekdayPrefix = ""
        var offset = ""
        var timePatterns: Array<String> = emptyArray()
        var datePatterns: Array<String> = emptyArray()
        var am = ""; var pm = ""; var evening = ""; var night = ""
        var leadFiller = ""
        var edgePreps = ""
    }

    private val numRu = arrayOf("ноль", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать", "двадцать", "двадцать один", "двадцать два", "двадцать три", "двадцать четыре")
    private val numEn = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "twenty one", "twenty two", "twenty three", "twenty four")
    private val numDe = arrayOf("null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig", "einundzwanzig", "zweiundzwanzig", "dreiundzwanzig", "vierundzwanzig")
    private val numFr = arrayOf("zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf", "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize", "dix-sept", "dix-huit", "dix-neuf", "vingt", "vingt et un", "vingt-deux", "vingt-trois", "vingt-quatre")

    private val extraRu = listOf("одну" to 1, "одного" to 1, "две" to 2, "двух" to 2, "трёх" to 3, "трех" to 3, "пол" to 0)
    private val extraEn = listOf("a" to 1, "an" to 1)
    private val extraDe = listOf("ein" to 1, "einer" to 1, "einem" to 1, "eine" to 1)
    private val extraFr = listOf("une" to 1, "vingt-et-un" to 21)

    private fun numAlt(words: Array<String>, extra: List<Pair<String, Int>>): String =
        (words.toList() + extra.map { it.first }).distinct()
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }

    private fun numValue(words: Array<String>, tokenRaw: String, extra: List<Pair<String, Int>>): Int {
        val token = tokenRaw.trim().lowercase().replace(Regex("\\s+"), " ")
        token.toIntOrNull()?.let { return it }
        for (i in words.indices) if (words[i] == token) return i
        for (e in extra) if (e.first == token) return e.second
        return -1
    }

    private val all: Map<String, Rules> by lazy { build() }

    private fun build(): Map<String, Rules> {
        val ru = Rules()
        val nru = numAlt(numRu, extraRu)
        ru.numbers = numRu
        ru.today = """\bсегодня\b"""; ru.tomorrow = """\bзавтра\b"""; ru.dayAfter = """\bпослезавтра\b"""
        ru.weekdays = arrayOf("""понедельник\w*""", """вторник\w*""", """сред[ауые]\b""", """четверг\w*""", """пятниц\w*""", """суббот\w*""", """воскресень\w*""")
        ru.months = arrayOf("""январ\w*""", """феврал\w*""", """март\w*""", """апрел\w*""", """ма[яй]\b""", """июн\w*""", """июл\w*""", """август\w*""", """сентябр\w*""", """октябр\w*""", """ноябр\w*""", """декабр\w*""")
        ru.repDaily = """\b(?:кажд(?:ый|ую|ое)\s+день|ежедневно)\b"""
        ru.repWeekly = """\b(?:кажд(?:ую|ая)\s+неделю|еженедельно)\b"""
        ru.repMonthly = """\b(?:кажд(?:ый|ое)\s+месяц|ежемесячно)\b"""
        ru.repYearly = """\b(?:кажд(?:ый|ое)\s+год|ежегодно)\b"""
        ru.repEveryN = """\bкажд(?:ые|ых)\s+(?<n>\d+|$nru)\s+(?:дня|дней|день)\b"""
        ru.repWeekdayPrefix = """\bкажд(?:ый|ую|ое|ые)\s+"""
        ru.weekdayPrefix = """(?:\b(?:в|во|на)\s+)?"""
        ru.offset = """\bчерез\s+(?:(?<n>\d+|$nru)\s+)?(?<u>минут\w*|час\w*|д(?:ень|ня|ней)\b|недел\w*)"""
        ru.timePatterns = arrayOf(
            """(?:\b(?:в|к)\s+)?(?<!\d)(?<h>\d{1,2})[:.](?<m>\d{2})(?!\d)(?:\s*(?<p>утра|вечера|дня|ночи))?""",
            """\b(?:в|к)\s+(?<h>\d{1,2}|$nru)(?:\s*(?:часов|часа|час|ч)\b)?(?:\s*(?<p>утра|вечера|дня|ночи)\b)?"""
        )
        val mru = ru.months.joinToString("|")
        ru.datePatterns = arrayOf("""(?<d>\d{1,2})(?:-?го)?\s+(?<mon>$mru)""")
        ru.am = "утра"; ru.pm = "дня"; ru.evening = "вечера"; ru.night = "ночи"
        ru.leadFiller = """^(?:напомни(?:ть)?(?:\s+мне)?|нужно|надо|пожалуйста)\s+"""
        ru.edgePreps = "в|во|на|к|по|и"

        val en = Rules()
        val nen = numAlt(numEn, extraEn)
        en.numbers = numEn
        en.today = """\btoday\b"""; en.tomorrow = """\btomorrow\b"""; en.dayAfter = """\bthe day after tomorrow\b|\bday after tomorrow\b"""
        en.weekdays = arrayOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        en.months = arrayOf("""january|jan\b""", """february|feb\b""", """march|mar\b""", """april|apr\b""", """may\b""", """june|jun\b""", """july|jul\b""", """august|aug\b""", """september|sept\b|sep\b""", """october|oct\b""", """november|nov\b""", """december|dec\b""")
        en.repDaily = """\b(?:every\s+day|everyday|daily)\b"""
        en.repWeekly = """\b(?:every\s+week|weekly)\b"""
        en.repMonthly = """\b(?:every\s+month|monthly)\b"""
        en.repYearly = """\b(?:every\s+year|yearly|annually)\b"""
        en.repEveryN = """\bevery\s+(?<n>\d+|$nen)\s+days\b"""
        en.repWeekdayPrefix = """\bevery\s+"""
        en.weekdayPrefix = """(?:\b(?:on|next|this)\s+)?"""
        en.offset = """\bin\s+(?:(?<n>\d+|$nen)\s+)?(?<u>minutes?|mins?|hours?|hrs?|days?|weeks?)\b"""
        en.timePatterns = arrayOf(
            """(?:\b(?:at|@)\s+)?(?<!\d)(?<h>\d{1,2})(?:[:.](?<m>\d{2}))?\s*(?<ap>a\.?m\.?|p\.?m\.?)(?!\w)""",
            """(?:\b(?:at|@)\s+)?(?<!\d)(?<h>\d{1,2}):(?<m>\d{2})(?!\d)(?:\s+in the\s+(?<p>morning|afternoon|evening|night))?""",
            """\b(?:at|@)\s+(?<h>\d{1,2}|$nen)(?:\s*o'?clock)?(?:\s+in the\s+(?<p>morning|afternoon|evening|night))?"""
        )
        val men = en.months.joinToString("|")
        en.datePatterns = arrayOf(
            """(?<d>\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(?<mon>$men)""",
            """(?<mon>$men)\s+(?<d>\d{1,2})(?:st|nd|rd|th)?\b"""
        )
        en.am = "morning"; en.pm = "afternoon"; en.evening = "evening"; en.night = "night"
        en.leadFiller = """^(?:remind me to|please|i need to|i have to)\s+"""
        en.edgePreps = "at|on|in|to|the|and"

        val de = Rules()
        val nde = numAlt(numDe, extraDe)
        de.numbers = numDe
        de.today = """\bheute\b"""; de.tomorrow = """\bmorgen\b"""; de.dayAfter = """\bübermorgen\b"""
        de.weekdays = arrayOf("""montag\w*""", """dienstag\w*""", """mittwoch\w*""", """donnerstag\w*""", """freitag\w*""", """samstag\w*""", """sonntag\w*""")
        de.months = arrayOf("""januar\w*""", """februar\w*""", """märz\w*""", """april\w*""", """mai\b""", """juni\b""", """juli\b""", """august\w*""", """september\w*""", """oktober\w*""", """november\w*""", """dezember\w*""")
        de.repDaily = """\b(?:jeden\s+tag|täglich)\b"""
        de.repWeekly = """\b(?:jede\s+woche|wöchentlich)\b"""
        de.repMonthly = """\b(?:jeden\s+monat|monatlich)\b"""
        de.repYearly = """\b(?:jedes\s+jahr|jährlich)\b"""
        de.repEveryN = """\balle\s+(?<n>\d+|$nde)\s+tage\b"""
        de.repWeekdayPrefix = """\bjede[nrs]?\s+"""
        de.weekdayPrefix = """(?:\b(?:am|diesen|nächsten)\s+)?"""
        de.offset = """\bin\s+(?:(?<n>\d+|$nde)\s+)?(?<u>minuten?|stunden?|tag(?:en|e)?|wochen?)\b"""
        de.timePatterns = arrayOf(
            """(?:\b(?:um|gegen)\s+)?(?<!\d)(?<h>\d{1,2})[:.](?<m>\d{2})(?!\d)(?:\s*uhr)?(?:\s*(?<p>morgens|vormittags|nachmittags|abends|nachts))?""",
            """\b(?:um|gegen)\s+(?<h>\d{1,2}|$nde)(?:\s*uhr)?(?:\s*(?<p>morgens|vormittags|nachmittags|abends|nachts)\b)?""",
            """(?<!\d)(?<h>\d{1,2})\s*uhr(?:\s*(?<p>morgens|vormittags|nachmittags|abends|nachts)\b)?"""
        )
        val mde = de.months.joinToString("|")
        de.datePatterns = arrayOf("""(?<d>\d{1,2})\.?\s+(?<mon>$mde)""")
        de.am = "morgens|vormittags"; de.pm = "nachmittags"; de.evening = "abends"; de.night = "nachts"
        de.leadFiller = """^(?:erinnere mich(?: daran)?,?|bitte)\s+"""
        de.edgePreps = "um|am|an|in|und|zu"

        val fr = Rules()
        val nfr = numAlt(numFr, extraFr)
        fr.numbers = numFr
        fr.today = """\baujourd['’]hui\b"""; fr.tomorrow = """\bdemain\b"""; fr.dayAfter = """\bapr[èe]s-demain\b"""
        fr.weekdays = arrayOf("""lundis?\b""", """mardis?\b""", """mercredis?\b""", """jeudis?\b""", """vendredis?\b""", """samedis?\b""", """dimanches?\b""")
        fr.months = arrayOf("janvier", """f[ée]vrier""", "mars", "avril", """mai\b""", "juin", "juillet", """ao[uû]t""", "septembre", "octobre", "novembre", """d[ée]cembre""")
        fr.repDaily = """\b(?:tous\s+les\s+jours|chaque\s+jour|quotidien(?:nement)?)\b"""
        fr.repWeekly = """\b(?:chaque\s+semaine|toutes\s+les\s+semaines|hebdomadaire)\b"""
        fr.repMonthly = """\b(?:chaque\s+mois|tous\s+les\s+mois|mensuel(?:lement)?)\b"""
        fr.repYearly = """\b(?:chaque\s+ann[ée]e|tous\s+les\s+ans|annuel(?:lement)?)\b"""
        fr.repEveryN = """\btous\s+les\s+(?<n>\d+|$nfr)\s+jours\b"""
        fr.repWeekdayPrefix = """\b(?:chaque|tous\s+les)\s+"""
        fr.weekdayPrefix = """(?:\b(?:ce|le)\s+)?"""
        fr.offset = """\bdans\s+(?:(?<n>\d+|$nfr)\s+)?(?<u>minutes?|heures?|jours?|semaines?)\b"""
        fr.timePatterns = arrayOf(
            """(?:\b[àa]\s+)?(?<!\d)(?<h>\d{1,2})\s*[:h]\s*(?<m>\d{2})(?!\d)(?:\s+(?:du|de l['’]?)\s*(?<p>matin|apr[èe]s-midi|soir|nuit))?""",
            """\b[àa]\s+(?<h>\d{1,2}|$nfr)\s*(?:heures?|h)?(?:\s+(?:du|de l['’]?)\s*(?<p>matin|apr[èe]s-midi|soir|nuit))?(?!\w)"""
        )
        val mfr = fr.months.joinToString("|")
        fr.datePatterns = arrayOf("""(?<d>\d{1,2}|1er)\s+(?<mon>$mfr)""")
        fr.am = "matin"; fr.pm = """apr[èe]s-midi"""; fr.evening = "soir"; fr.night = "nuit"
        fr.leadFiller = """^(?:rappelle[- ]moi(?: de)?|il faut|s'il te pla[iî]t)\s+"""
        fr.edgePreps = "à|a|le|la|de|du|et"

        return mapOf("ru" to ru, "en" to en, "de" to de, "fr" to fr)
    }

    private val opt = setOf(RegexOption.IGNORE_CASE)

    private fun MatchResult.grp(name: String): String? =
        try { groups[name]?.value } catch (e: IllegalArgumentException) { null }

    fun parse(text: String, cultureOrLang: String, now: LocalDateTime): ParsedTask {
        var lang = if (cultureOrLang.length >= 2) cultureOrLang.substring(0, 2).lowercase() else "ru"
        val r: Rules = all[lang] ?: run { lang = "ru"; all.getValue("ru") }

        val res = ParsedTask()
        var work = " " + text.trim() + " "

        fun take(pattern: String): MatchResult? {
            val m = Regex(pattern, opt).find(work) ?: return null
            work = work.removeRange(m.range.first, m.range.last + 1).let {
                it.substring(0, m.range.first) + " " + it.substring(m.range.first)
            }
            return m
        }

        fun n(token: String): Int = when (lang) {
            "en" -> numValue(numEn, token, extraEn)
            "de" -> numValue(numDe, token, extraDe)
            "fr" -> numValue(numFr, token, extraFr)
            else -> numValue(numRu, token, extraRu)
        }

        var date: LocalDate? = null
        var time: LocalTime? = null
        var offsetTime = false
        val today = now.toLocalDate()

        // ---------- Повторение ----------
        var m: MatchResult? = take(r.repEveryN)
        if (m != null) {
            res.repeat = RepeatKind.EveryNDays
            res.repeatInterval = maxOf(1, n(m.grp("n") ?: ""))
        } else if (take(r.repDaily) != null) res.repeat = RepeatKind.Daily
        else if (take(r.repWeekly) != null) res.repeat = RepeatKind.Weekly
        else if (take(r.repMonthly) != null) res.repeat = RepeatKind.Monthly
        else if (take(r.repYearly) != null) res.repeat = RepeatKind.Yearly
        else {
            var mask = 0
            for (guard in 0 until 7) {
                var found = false
                for (d in 0 until 7) {
                    if (take(r.repWeekdayPrefix + "(?:" + r.weekdays[d] + ")") != null) {
                        mask = mask or (1 shl d)
                        found = true
                        break
                    }
                }
                if (!found) break
            }
            if (mask != 0) {
                res.repeat = RepeatKind.Weekdays
                res.repeatDays = mask
            }
        }

        // ---------- «Через N …» ----------
        m = take(r.offset)
        if (m != null) {
            var cnt = if (m.grp("n") != null) n(m.grp("n")!!) else 1
            if (cnt < 0) cnt = 1
            when (unitKind(m.grp("u") ?: "")) {
                0 -> { val d = now.plusMinutes(cnt.toLong()); date = d.toLocalDate(); time = LocalTime.of(d.hour, d.minute); offsetTime = true }
                1 -> { val d = now.plusHours(cnt.toLong()); date = d.toLocalDate(); time = LocalTime.of(d.hour, d.minute); offsetTime = true }
                2 -> date = today.plusDays(cnt.toLong())
                else -> date = today.plusDays(7L * cnt)
            }
        }

        // ---------- Дата ----------
        if (date == null) {
            if (take(r.dayAfter) != null) date = today.plusDays(2)
            else if (take(r.tomorrow) != null) date = today.plusDays(1)
            else if (take(r.today) != null) date = today
        }
        if (date == null) {
            for (pat in r.datePatterns) {
                m = take(pat) ?: continue
                val dTok = m.grp("d") ?: ""
                val day = if (dTok.startsWith("1er")) 1 else (Regex("\\d+").find(dTok)?.value?.toIntOrNull() ?: 0)
                val mon = monthIndex(r, m.grp("mon") ?: "")
                if (mon > 0 && day in 1..31) date = resolveDate(today, mon, day, null)
                break
            }
        }
        if (date == null) {
            // числовая дата 15.11 / 15/11/2026 — не трогаем текст, если это на самом деле время вроде 14.30
            val dm = Regex("""(?<!\d)(?<d>\d{1,2})[./](?<mo>\d{1,2})(?:[./](?<y>\d{2,4}))?(?![\d:])""", opt).find(work)
            if (dm != null) {
                val dd = dm.grp("d")!!.toInt()
                val mo = dm.grp("mo")!!.toInt()
                if (mo in 1..12 && dd in 1..31) {
                    work = work.substring(0, dm.range.first) + " " + work.substring(dm.range.last + 1)
                    var y = dm.grp("y")?.toIntOrNull()
                    if (y != null && y < 100) y += 2000
                    date = resolveDate(today, mo, dd, y)
                }
            }
        }
        if (date == null && res.repeat != RepeatKind.Weekdays) {
            // день недели: «в пятницу», «on Friday»
            var d = 0
            while (d < 7 && date == null) {
                if (take(r.weekdayPrefix + "(?:" + r.weekdays[d] + ")") != null) {
                    val todayIdx = now.dayOfWeek.value - 1
                    var diff = (d - todayIdx + 7) % 7
                    if (diff == 0) diff = 7
                    date = today.plusDays(diff.toLong())
                }
                d++
            }
        }

        // ---------- Время ----------
        if (time == null) {
            for (pat in r.timePatterns) {
                m = take(pat) ?: continue
                var h = n(m.grp("h") ?: "")
                if (h < 0 || h > 24) continue
                val mi = m.grp("m")?.toIntOrNull() ?: 0
                if (mi > 59) continue
                val p = m.grp("p")?.lowercase() ?: ""
                val ap = m.grp("ap")?.lowercase()?.replace(".", "") ?: ""
                val colonForm = m.grp("m") != null
                val twelveStyle = h in 1..12
                var decided = false

                if (ap == "am") { if (h == 12) h = 0; decided = true }
                else if (ap == "pm") { if (h < 12) h += 12; decided = true }
                else if (p.isNotEmpty()) {
                    if (Regex("^(?:" + r.evening + ")\$", opt).containsMatchIn(p)) { if (h < 12) h += 12 }
                    else if (Regex("^(?:" + r.pm + ")\$", opt).containsMatchIn(p)) { if (h < 12) h += 12; if (h == 24) h = 12 }
                    else if (Regex("^(?:" + r.night + ")\$", opt).containsMatchIn(p)) { if (h == 12) h = 0 else if (h in 6..11) h += 12 }
                    else { if (h == 12) h = 0 } // утро
                    decided = true
                } else if (h > 12 || h == 0 || (colonForm && lang != "en") || (colonForm && (m.grp("h") ?: "").startsWith("0"))) {
                    decided = true // 24-часовой формат
                }

                if (!decided && twelveStyle) {
                    res.ambiguousHour = true
                    if (h in 1..6) h += 12
                }
                if (h == 24) h = 0
                time = LocalTime.of(h, mi)
                break
            }
        }

        // ---------- Значения по умолчанию ----------
        if (res.repeat == RepeatKind.Weekdays && date == null && res.repeatDays != 0) {
            val t = time ?: LocalTime.of(9, 0)
            for (i in 0 until 8) {
                val cand = today.plusDays(i.toLong())
                if ((res.repeatDays and Recurrence.dayBit(cand.dayOfWeek)) == 0) continue
                if (i == 0 && !cand.atTime(t).isAfter(now)) continue
                date = cand
                break
            }
        }
        if (date == null && time != null && !offsetTime) {
            date = if (today.atTime(time).isAfter(now)) today else today.plusDays(1)
        }

        res.date = date
        res.time = time
        res.title = cleanTitle(work, r)
        return res
    }

    /** Пользователь подтвердил, что имелось в виду другое время (до/после полудня). */
    fun applyHour(t: ParsedTask, pm: Boolean) {
        val tm = t.time ?: return
        var h = tm.hour % 12
        if (pm) h += 12
        t.time = LocalTime.of(h, tm.minute)
        t.ambiguousHour = false
    }

    private fun unitKind(uRaw: String): Int {
        val u = uRaw.lowercase()
        if (u.startsWith("мин") || u.startsWith("min")) return 0
        if (u.startsWith("час") || u.startsWith("hour") || u.startsWith("hr") || u.startsWith("stund") || u.startsWith("heure")) return 1
        if (u.startsWith("нед") || u.startsWith("week") || u.startsWith("woch") || u.startsWith("semaine")) return 3
        return 2
    }

    private fun monthIndex(r: Rules, token: String): Int {
        for (i in 0 until 12) if (Regex("^(?:" + r.months[i] + ")", opt).containsMatchIn(token)) return i + 1
        return 0
    }

    private fun resolveDate(today: LocalDate, month: Int, dayIn: Int, year: Int?): LocalDate {
        var y = year ?: today.year
        var d = LocalDate.of(y, month, minOf(dayIn, YearMonth.of(y, month).lengthOfMonth()))
        if (year == null && d.isBefore(today)) {
            y++
            d = LocalDate.of(y, month, minOf(dayIn, YearMonth.of(y, month).lengthOfMonth()))
        }
        return d
    }

    private val trimChars = charArrayOf(' ', ',', '.', ';', ':', '-', '—')

    private fun cleanTitle(src: String, r: Rules): String {
        var s = src.replace(Regex("\\s+"), " ").trim(*trimChars)
        s = Regex(r.leadFiller, opt).replace(s, "").trim()
        for (i in 0 until 3) {
            val before = s
            s = Regex("^(?:" + r.edgePreps + ")\\s+", opt).replace(s, "")
            s = Regex("\\s+(?:" + r.edgePreps + ")\$", opt).replace(s, "")
            s = s.trim(*trimChars)
            if (s == before) break
        }
        if (s.isNotEmpty()) s = s.substring(0, 1).uppercase() + s.substring(1)
        return s
    }
}
