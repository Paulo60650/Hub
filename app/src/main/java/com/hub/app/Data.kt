package com.hub.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Stockage local (SharedPreferences). Rien ne quitte le téléphone. */
class Store(ctx: Context) {
    private val appCtx: Context = ctx.applicationContext
    private val p = appCtx.getSharedPreferences("hub", Context.MODE_PRIVATE)

    fun get(k: String): String? = p.getString(k, null)
    fun put(k: String, v: String) { p.edit().putString(k, v).apply() }

    fun hasPin(): Boolean = get("pin_hash") != null

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        put("pin_salt", salt)
        put("pin_hash", hash(salt + pin))
    }

    fun checkPin(pin: String): Boolean =
        get("pin_hash") == hash((get("pin_salt") ?: "") + pin)

    /** « Code oublié » : efface tout, y compris les données. */
    fun resetAll() {
        p.edit().clear().apply()
        appCtx.filesDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun <T> loadList(key: String, parse: (JSONObject) -> T): List<T> {
        val raw = get(key) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun <T> saveList(key: String, items: List<T>, enc: (T) -> JSONObject) {
        val arr = JSONArray()
        items.forEach { arr.put(enc(it)) }
        put(key, arr.toString())
    }
}

/** Liste observable par Compose, sauvegardée à la demande. */
class Persisted<T>(
    private val store: Store,
    private val key: String,
    parse: (JSONObject) -> T,
    private val enc: (T) -> JSONObject,
) {
    val items = mutableStateListOf<T>().also { it.addAll(store.loadList(key, parse)) }
    fun save() { store.saveList(key, items.toList(), enc) }
}

data class Note(val id: Long, val title: String, val body: String, val ts: Long)
data class Task(val id: Long, val text: String, val done: Boolean)
data class Expense(val id: Long, val label: String, val amount: Double, val cat: Int, val ts: Long)
data class Entry(val id: Long, val date: String, val mood: Int, val text: String)
data class Habit(val id: Long, val name: String, val days: Set<String>)

fun parseNote(o: JSONObject) = Note(o.getLong("id"), o.getString("title"), o.getString("body"), o.getLong("ts"))
fun encNote(n: Note) = JSONObject().put("id", n.id).put("title", n.title).put("body", n.body).put("ts", n.ts)

fun parseTask(o: JSONObject) = Task(o.getLong("id"), o.getString("text"), o.getBoolean("done"))
fun encTask(t: Task) = JSONObject().put("id", t.id).put("text", t.text).put("done", t.done)

fun parseExpense(o: JSONObject) =
    Expense(o.getLong("id"), o.getString("label"), o.getDouble("amount"), o.getInt("cat"), o.getLong("ts"))
fun encExpense(e: Expense) =
    JSONObject().put("id", e.id).put("label", e.label).put("amount", e.amount).put("cat", e.cat).put("ts", e.ts)

fun parseEntry(o: JSONObject) = Entry(o.getLong("id"), o.getString("date"), o.getInt("mood"), o.getString("text"))
fun encEntry(e: Entry) = JSONObject().put("id", e.id).put("date", e.date).put("mood", e.mood).put("text", e.text)

fun parseHabit(o: JSONObject): Habit {
    val arr = o.getJSONArray("days")
    return Habit(o.getLong("id"), o.getString("name"), (0 until arr.length()).map { arr.getString(it) }.toSet())
}
fun encHabit(h: Habit): JSONObject {
    val arr = JSONArray()
    h.days.forEach { arr.put(it) }
    return JSONObject().put("id", h.id).put("name", h.name).put("days", arr)
}
