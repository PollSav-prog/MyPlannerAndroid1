package com.myplanner.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myplanner.app.App
import com.myplanner.app.R
import com.myplanner.app.data.RepeatKind
import com.myplanner.app.data.TaskItem
import com.myplanner.app.logic.Fmt
import com.myplanner.app.logic.NaturalDateParser
import com.myplanner.app.logic.ParsedTask
import com.myplanner.app.logic.TaskOps
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

private val speechLangs = listOf("ru-RU" to "RU", "en-US" to "EN", "de-DE" to "DE", "fr-FR" to "FR")

private fun toTask(p: ParsedTask) = TaskItem(
    title = p.title, due = p.due, repeat = p.repeat, repeatInterval = p.repeatInterval, repeatDays = p.repeatDays
)

/** Текст подтверждения на языке речи, а не на языке интерфейса. */
fun buildConfirmText(ctx: Context, p: ParsedTask, code: String): String {
    val due = p.due
    val loc = Locale.forLanguageTag(code)
    val dateText = when (due.toLocalDate()) {
        LocalDate.now() -> Fmt.stringIn(ctx, code, R.string.rel_today)
        LocalDate.now().plusDays(1) -> Fmt.stringIn(ctx, code, R.string.rel_tomorrow)
        else -> Fmt.dateFull(due.toLocalDate(), loc)
    }
    return Fmt.stringIn(ctx, code, R.string.voice_confirm, p.title, dateText, Fmt.time(due, loc))
}

@Composable
fun VoiceDialog(vm: MainViewModel, onFix: (TaskItem) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var lang by remember {
        mutableStateOf(
            App.prefs.speechLang.ifBlank {
                speechLangs.firstOrNull { it.first.startsWith(Fmt.locale().language) }?.first ?: "ru-RU"
            }
        )
    }
    var phrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedTask?>(null) }
    var rev by remember { mutableStateOf(0) }   // чтобы экран обновлялся после правки полей ParsedTask

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx) { }
        tts = t
        onDispose { t.stop(); t.shutdown() }
    }

    val noVoice = stringResource(R.string.tts_no_voice_a)
    fun speak(p: ParsedTask) {
        val t = tts ?: return
        val r = t.setLanguage(Locale.forLanguageTag(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            status = noVoice
        } else {
            t.speak(buildConfirmText(ctx, p, lang), TextToSpeech.QUEUE_FLUSH, null, "confirm")
        }
    }

    val msgEmpty = stringResource(R.string.voice_empty)
    val msgNoTitle = stringResource(R.string.voice_no_title)
    fun parse() {
        val text = phrase.trim()
        if (text.isEmpty()) { status = msgEmpty; return }
        val p = NaturalDateParser.parse(text, lang, LocalDateTime.now())
        if (p.title.isBlank()) { status = msgNoTitle; parsed = null; return }
        if (p.date == null) p.date = vm.selectedDate
        parsed = p
        status = ""
        rev++
        if (!p.ambiguousHour && App.prefs.ttsEnabled) speak(p)
    }

    val msgHeard = stringResource(R.string.voice_heard)
    val msgNothing = stringResource(R.string.speech_nothing)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) { phrase = text; status = msgHeard; parse() } else status = msgNothing
        } else status = msgNothing
    }
    val msgNoService = stringResource(R.string.voice_no_service)
    fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PROMPT, ctx.getString(R.string.voice_listening))
        }
        try { launcher.launch(i) } catch (e: ActivityNotFoundException) { status = msgNoService }
    }

    val p = parsed
    @Suppress("UNUSED_VARIABLE") val touch = rev

    AppDialog(
        stringResource(R.string.voice_title), onDismiss,
        confirmLabel = if (p != null) stringResource(R.string.btn_save) else null,
        onConfirm = {
            if (p != null && !p.ambiguousHour) {
                val t = toTask(p)
                TaskOps.save(ctx, t)
                vm.selectDate(t.due.toLocalDate())
                vm.selectedId = t.id
                onDismiss()
            }
        },
        dismissLabel = stringResource(R.string.btn_cancel)
    ) {
        SectionLabel(stringResource(R.string.voice_lang))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            speechLangs.forEach { (code, badge) ->
                FilterChip(selected = lang == code, onClick = { lang = code; App.prefs.speechLang = code }, label = { Text(badge) })
            }
        }
        Text(stringResource(R.string.speech_android), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        SectionLabel(stringResource(R.string.voice_say))
        Button(onClick = { listen() }) {
            Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.btn_speak))
        }

        SectionLabel(stringResource(R.string.voice_or_type))
        OutlinedTextField(value = phrase, onValueChange = { phrase = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { parse() }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.btn_parse)) }
        if (status.isNotEmpty()) Text(status, modifier = Modifier.padding(top = 8.dp))

        if (p != null) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    val due = p.due
                    Text(
                        Fmt.dateFull(due.toLocalDate()) + ", " + Fmt.time(due) +
                            if (p.hasTime) "" else "  (" + stringResource(R.string.voice_default_time) + ")",
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (p.repeat != RepeatKind.None) {
                        Text(
                            stringResource(R.string.lbl_repeat) + ": " + describeRepeat(ctx, p.repeat, p.repeatInterval, p.repeatDays),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val t = p.time
                    if (p.ambiguousHour && t != null) {
                        Text(stringResource(R.string.voice_ambiguous), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        val h12 = t.hour % 12
                        val am = LocalDate.now().atTime(h12, t.minute)
                        val pm = LocalDate.now().atTime(h12 + 12, t.minute)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(onClick = {
                                NaturalDateParser.applyHour(p, false); rev++
                                if (App.prefs.ttsEnabled) speak(p)
                            }) { Text(Fmt.time(am)) }
                            OutlinedButton(onClick = {
                                NaturalDateParser.applyHour(p, true); rev++
                                if (App.prefs.ttsEnabled) speak(p)
                            }) { Text(Fmt.time(pm)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                        TextButton(onClick = { speak(p) }) { Text(stringResource(R.string.btn_speak_back)) }
                        TextButton(onClick = { onFix(toTask(p)) }) { Text(stringResource(R.string.btn_fix)) }
                    }
                }
            }
        }
    }
}
