package com.myplanner.app.ui

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myplanner.app.R
import com.myplanner.app.data.TaskItem
import com.myplanner.app.logic.Fmt
import com.myplanner.app.logic.TaskOps
import com.myplanner.app.notify.Notifier
import com.myplanner.app.weather.Weather
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields

sealed interface Dlg {
    data class EditTask(val task: TaskItem?) : Dlg
    data class Card(val id: Long) : Dlg
    data object Repeat : Dlg
    data object Sound : Dlg
    data object Quiet : Dlg
    data object Weather : Dlg
    data object History : Dlg
    data object Language : Dlg
    data object Settings : Dlg
    data object Voice : Dlg
    data class Confirm(val text: String, val onYes: () -> Unit) : Dlg
}

@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<Dlg?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            vm.tick()
        }
    }
    LaunchedEffect(vm.cardId) {
        vm.cardId?.let { dlg = Dlg.Card(it); vm.cardId = null }
    }

    val msgSelect = stringResource(R.string.msg_select_task)
    fun withSelected(block: (TaskItem) -> Unit) {
        val t = vm.find(vm.selectedId)
        if (t == null) Toast.makeText(ctx, msgSelect, Toast.LENGTH_SHORT).show() else block(t)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- шапка ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.app_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (vm.quiet) {
                Icon(Icons.Filled.NightsStay, stringResource(R.string.quiet_on), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { dlg = Dlg.Voice }) {
                Icon(Icons.Filled.Mic, stringResource(R.string.tip_voice), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { dlg = Dlg.Settings }) {
                Icon(Icons.Filled.Settings, stringResource(R.string.tip_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---- центр + правая панель ----
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                CalendarCard(vm)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.plans_title),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                val drops = vm.dropletTasks()
                if (drops.isNotEmpty()) {
                    DropletBanner(drops.first()) { dlg = Dlg.Card(drops.first().id) }
                    Spacer(Modifier.height(6.dp))
                }
                val list = vm.dayTasks
                if (list.isEmpty()) {
                    Text(
                        stringResource(R.string.no_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list, key = { it.id }) { t ->
                            TaskRow(
                                t, vm, selected = vm.selectedId == t.id,
                                onClick = { vm.selectedId = t.id },
                                onEdit = { dlg = Dlg.EditTask(t) },
                                onDelete = {
                                    dlg = Dlg.Confirm(ctx.getString(R.string.msg_confirm_delete, t.title)) { TaskOps.archive(ctx, t) }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))
            Column(
                Modifier
                    .width(54.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RailButton(Icons.Filled.Add, stringResource(R.string.tip_add)) { dlg = Dlg.EditTask(null) }
                RailButton(Icons.Filled.Edit, stringResource(R.string.tip_edit)) { withSelected { dlg = Dlg.EditTask(it) } }
                RailButton(Icons.Filled.Check, stringResource(R.string.tip_done)) {
                    withSelected { TaskOps.complete(ctx, it); Notifier.cancel(ctx, it.id) }
                }
                RailButton(Icons.Filled.Repeat, stringResource(R.string.tip_repeat)) { withSelected { dlg = Dlg.Repeat } }
                RailButton(Icons.Filled.VolumeUp, stringResource(R.string.tip_sound)) { dlg = Dlg.Sound }
                RailButton(Icons.Filled.NightsStay, stringResource(R.string.tip_quiet)) { dlg = Dlg.Quiet }
                RailButton(Icons.Filled.Cloud, stringResource(R.string.tip_weather)) { dlg = Dlg.Weather }
                RailButton(Icons.Filled.History, stringResource(R.string.tip_history)) { dlg = Dlg.History }
                RailButton(Icons.Filled.Language, stringResource(R.string.tip_language)) { dlg = Dlg.Language }
            }
        }

        BottomBar(vm) { dlg = Dlg.Weather }
    }

    // ---- диалоги ----
    when (val d = dlg) {
        is Dlg.EditTask -> TaskDialog(vm, d.task) { dlg = null }
        is Dlg.Card -> CardDialog(vm, d.id, onEdit = { dlg = Dlg.EditTask(it) }) { dlg = null }
        Dlg.Repeat -> RepeatDialog(vm) { dlg = null }
        Dlg.Sound -> SoundDialog { dlg = null }
        Dlg.Quiet -> QuietDialog(vm) { dlg = null }
        Dlg.Weather -> WeatherDialog(vm) { dlg = null }
        Dlg.History -> HistoryDialog(vm) { dlg = null }
        Dlg.Language -> LanguageDialog { dlg = null }
        Dlg.Settings -> SettingsDialog(vm) { dlg = null }
        Dlg.Voice -> VoiceDialog(vm, onFix = { dlg = Dlg.EditTask(it) }) { dlg = null }
        is Dlg.Confirm -> AlertDialog(
            onDismissRequest = { dlg = null },
            text = { Text(d.text) },
            confirmButton = { TextButton(onClick = { d.onYes(); dlg = null }) { Text(stringResource(R.string.btn_yes)) } },
            dismissButton = { TextButton(onClick = { dlg = null }) { Text(stringResource(R.string.btn_no)) } }
        )
        null -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RailButton(icon: ImageVector, tip: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.2f else 1f, label = "railScale")
    val ctx = LocalContext.current
    Box(
        Modifier
            .size(46.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (pressed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = { Toast.makeText(ctx, tip, Toast.LENGTH_SHORT).show() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, tip, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun DropletBanner(t: TaskItem, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickableSimple(onClick)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, stringResource(R.string.tip_droplet), tint = Color(0xFFE53935), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.droplet_title), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(t.title + " · " + Fmt.time(t.due), color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

@Composable
fun CalendarCard(vm: MainViewModel) {
    val loc = Fmt.locale()
    val first = WeekFields.of(loc).firstDayOfWeek
    val ym = vm.month
    val withTasks = vm.datesWithTasks()
    val drops = vm.dropletTasks().map { it.due.toLocalDate() }.toSet()
    val today = LocalDate.now()

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.month = ym.minusYears(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardDoubleArrowLeft, null) }
                IconButton(onClick = { vm.month = ym.minusMonths(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ChevronLeft, null) }
                Text(
                    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.uppercase() } + " " + ym.year,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { vm.month = ym.plusMonths(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.ChevronRight, null) }
                IconButton(onClick = { vm.month = ym.plusYears(1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.KeyboardDoubleArrowRight, null) }
            }
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    Text(
                        first.plus(i.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, loc),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val offset = (ym.atDay(1).dayOfWeek.value - first.value + 7) % 7
            val len = ym.lengthOfMonth()
            val rows = (offset + len + 6) / 7
            for (r in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val day = r * 7 + c - offset + 1
                        if (day in 1..len) {
                            val date = ym.atDay(day)
                            DayCell(
                                day, date == vm.selectedDate, date == today,
                                date in withTasks, date in drops
                            ) { vm.selectDate(date) }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            TextButton(onClick = { vm.selectDate(today) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.btn_today))
            }
        }
    }
}

@Composable
fun RowScope.DayCell(day: Int, selected: Boolean, isToday: Boolean, hasTask: Boolean, droplet: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(40.dp)
            .combinedClickableSimple(onClick),
        contentAlignment = Alignment.Center
    ) {
        val primary = MaterialTheme.colorScheme.primary
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (selected) primary else Color.Transparent)
                .then(if (isToday && !selected) Modifier.border(1.5.dp, primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                day.toString(),
                fontSize = 14.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
        if (hasTask) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onPrimary else primary)
            )
        }
        if (droplet) {
            Icon(
                Icons.Filled.WaterDrop, null, tint = Color(0xFFE53935),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    t: TaskItem, vm: MainViewModel, selected: Boolean,
    onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val overdue = !t.isDone && t.due.isBefore(vm.now)
    val prioColor = when (t.priority) { 0 -> Color(0xFF90A4AE); 2 -> Color(0xFFE53935); else -> Color(0xFF3F7BE0) }
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onDoubleClick = onEdit, onLongClick = { menu = true })
        ) {
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(52.dp)
                        .background(prioColor)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(vertical = 8.dp).weight(1f)) {
                    Text(
                        t.title, fontWeight = FontWeight.SemiBold,
                        textDecoration = if (t.isDone) TextDecoration.LineThrough else null,
                        color = if (t.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    val rep = describeRepeat(ctx, t.repeat, t.repeatInterval, t.repeatDays)
                    Text(
                        Fmt.time(t.due) + (if (rep.isNotEmpty()) " · $rep" else ""),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (overdue) Text(stringResource(R.string.status_overdue), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                if (t.isDone) Icon(Icons.Filled.Check, null, tint = Color(0xFF43A047))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.move_today)) }, onClick = { menu = false; TaskOps.moveToDate(ctx, t, 0) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_tomorrow)) }, onClick = { menu = false; TaskOps.moveToDate(ctx, t, 1) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_week)) }, onClick = { menu = false; TaskOps.moveToDate(ctx, t, 7) })
            DropdownMenuItem(text = { Text(stringResource(R.string.tip_edit)) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.btn_delete)) }, onClick = { menu = false; onDelete() })
        }
    }
}

@Composable
fun BottomBar(vm: MainViewModel, onWeather: () -> Unit) {
    val now = vm.now
    val w = vm.weather
    val text = when {
        w != null && vm.weatherOffline -> stringResource(
            R.string.weather_offline,
            w.city + " " + w.temp + "°",
            Fmt.time(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(w.time), java.time.ZoneId.systemDefault()))
        )
        w != null -> w.city + ": " + w.temp + "°C, " + stringResource(Weather.wxRes(w.code))
        vm.weatherNotFound -> stringResource(R.string.weather_not_found)
        else -> stringResource(R.string.weather_set_city)
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(Fmt.time(now), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(Fmt.dateMedium(now.toLocalDate()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text, fontSize = 13.sp, textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1.4f)
                    .combinedClickableSimple(onWeather)
            )
        }
    }
}
