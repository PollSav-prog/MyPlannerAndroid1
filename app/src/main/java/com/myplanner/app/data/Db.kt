package com.myplanner.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private fun LocalDateTime.ms(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun Long.ldt(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
private fun Cursor.l(n: String): Long = getLong(getColumnIndexOrThrow(n))
private fun Cursor.s(n: String): String = getString(getColumnIndexOrThrow(n)) ?: ""
private fun Cursor.lo(n: String): Long? {
    val i = getColumnIndexOrThrow(n)
    return if (isNull(i)) null else getLong(i)
}

/** База задач (SQLite). Всё состояние напоминаний хранится здесь, поэтому переживает перезагрузку телефона. */
class Db(private val ctx: Context) : SQLiteOpenHelper(ctx, FILE, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', " +
                "due INTEGER NOT NULL, priority INTEGER NOT NULL DEFAULT 1, " +
                "category TEXT NOT NULL DEFAULT '', remind INTEGER NOT NULL DEFAULT 1, " +
                "warn_min INTEGER NOT NULL DEFAULT -1, repeat_kind INTEGER NOT NULL DEFAULT 0, " +
                "repeat_interval INTEGER NOT NULL DEFAULT 1, repeat_days INTEGER NOT NULL DEFAULT 0, " +
                "done INTEGER NOT NULL DEFAULT 0, completed_at INTEGER, archived INTEGER NOT NULL DEFAULT 0, " +
                "last_signal INTEGER, snooze_until INTEGER, warned INTEGER NOT NULL DEFAULT 0, " +
                "signaled INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX idx_tasks_due ON tasks(due)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun read(c: Cursor) = TaskItem(
        id = c.l("id"),
        title = c.s("title"),
        description = c.s("description"),
        due = c.l("due").ldt(),
        priority = c.l("priority").toInt(),
        category = c.s("category"),
        remindEnabled = c.l("remind") != 0L,
        warnMinutes = c.l("warn_min").toInt(),
        repeat = RepeatKind.values().getOrElse(c.l("repeat_kind").toInt()) { RepeatKind.None },
        repeatInterval = c.l("repeat_interval").toInt(),
        repeatDays = c.l("repeat_days").toInt(),
        isDone = c.l("done") != 0L,
        completedAt = c.lo("completed_at")?.ldt(),
        isArchived = c.l("archived") != 0L,
        lastSignal = c.lo("last_signal")?.ldt(),
        snoozeUntil = c.lo("snooze_until")?.ldt(),
        warned = c.l("warned") != 0L,
        signaled = c.l("signaled") != 0L
    )

    private fun values(t: TaskItem) = ContentValues().apply {
        put("title", t.title)
        put("description", t.description)
        put("due", t.due.ms())
        put("priority", t.priority)
        put("category", t.category)
        put("remind", if (t.remindEnabled) 1 else 0)
        put("warn_min", t.warnMinutes)
        put("repeat_kind", t.repeat.ordinal)
        put("repeat_interval", t.repeatInterval)
        put("repeat_days", t.repeatDays)
        put("done", if (t.isDone) 1 else 0)
        put("completed_at", t.completedAt?.ms())
        put("archived", if (t.isArchived) 1 else 0)
        put("last_signal", t.lastSignal?.ms())
        put("snooze_until", t.snoozeUntil?.ms())
        put("warned", if (t.warned) 1 else 0)
        put("signaled", if (t.signaled) 1 else 0)
    }

    @Synchronized
    fun loadAll(): List<TaskItem> {
        val list = ArrayList<TaskItem>()
        readableDatabase.rawQuery("SELECT * FROM tasks ORDER BY due", null).use { c ->
            while (c.moveToNext()) list.add(read(c))
        }
        return list
    }

    @Synchronized
    fun get(id: Long): TaskItem? =
        readableDatabase.rawQuery("SELECT * FROM tasks WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) read(c) else null
        }

    @Synchronized
    fun insert(t: TaskItem): Long {
        val id = writableDatabase.insert("tasks", null, values(t))
        t.id = id
        return id
    }

    @Synchronized
    fun update(t: TaskItem) {
        writableDatabase.update("tasks", values(t), "id = ?", arrayOf(t.id.toString()))
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete("tasks", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Резервные копии ----------
    private fun dbFile(): File = ctx.getDatabasePath(FILE)

    @Synchronized
    fun exportTo(uri: Uri): Boolean = try {
        close()
        ctx.contentResolver.openOutputStream(uri)?.use { out -> dbFile().inputStream().use { it.copyTo(out) } }
        true
    } catch (e: Exception) {
        false
    }

    /** Заменяет базу выбранным файлом. Перед этим делает автоматическую копию. */
    @Synchronized
    fun importFrom(uri: Uri): Boolean = try {
        val tmp = File(ctx.cacheDir, "restore.db")
        ctx.contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        val header = ByteArray(15)
        tmp.inputStream().use { it.read(header) }
        if (String(header, Charsets.US_ASCII) != "SQLite format 3") {
            tmp.delete()
            false
        } else {
            autoBackup(force = true)
            close()
            tmp.copyTo(dbFile(), overwrite = true)
            File(dbFile().path + "-wal").delete()
            File(dbFile().path + "-shm").delete()
            File(dbFile().path + "-journal").delete()
            tmp.delete()
            true
        }
    } catch (e: Exception) {
        false
    }

    /** Раз в сутки сохраняет копию во внутреннюю папку приложения (хранятся последние 7). */
    @Synchronized
    fun autoBackup(force: Boolean = false) {
        try {
            val dir = File(ctx.filesDir, "backups").apply { mkdirs() }
            val files = (dir.listFiles() ?: emptyArray()).sortedBy { it.name }
            val lastMs = files.lastOrNull()?.lastModified() ?: 0L
            if (!force && System.currentTimeMillis() - lastMs < 24L * 3600 * 1000) return
            if (!dbFile().exists()) return
            readableDatabase.close()
            dbFile().copyTo(File(dir, "planner-" + System.currentTimeMillis() + ".db"), overwrite = true)
            val all = (dir.listFiles() ?: emptyArray()).sortedBy { it.name }
            all.dropLast(7).forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val FILE = "planner.db"
    }
}
