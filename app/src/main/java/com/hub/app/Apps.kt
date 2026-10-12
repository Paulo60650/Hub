package com.hub.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun newId() = System.currentTimeMillis() * 1000 + (0..999).random()

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.s1)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun AddButton(onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(C.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph("plus", C.ink, 24.dp) }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.muted, fontSize = 14.sp)
    }
}

/* ───────────────────────── Notes ───────────────────────── */

@Composable
fun NotesApp(store: Store, onBack: () -> Unit) {
    val data = remember { Persisted(store, "notes", ::parseNote, ::encNote) }
    var editId by remember { mutableStateOf<Long?>(null) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH) }

    fun close() {
        val id = editId
        val n = data.items.firstOrNull { it.id == id }
        if (n != null && n.title.isBlank() && n.body.isBlank()) { data.items.remove(n); data.save() }
        editId = null
    }
    BackHandler(editId != null) { close() }

    val editing = data.items.firstOrNull { it.id == editId }
    if (editing != null) {
        AppScreen("Note", onBack = { close() }, actions = {
            IconBtn("trash", "Supprimer", C.danger) { data.items.remove(editing); data.save(); editId = null }
        }) {
            HubField(
                editing.title,
                { v ->
                    val i = data.items.indexOfFirst { it.id == editing.id }
                    data.items[i] = editing.copy(title = v, ts = System.currentTimeMillis()); data.save()
                },
                "Titre", Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            HubField(
                editing.body,
                { v ->
                    val i = data.items.indexOfFirst { it.id == editing.id }
                    data.items[i] = editing.copy(body = v, ts = System.currentTimeMillis()); data.save()
                },
                "Écrivez ici…", Modifier.fillMaxWidth().weight(1f), singleLine = false,
            )
            Spacer(Modifier.height(12.dp))
        }
    } else {
        AppScreen("Notes", onBack = onBack, actions = {
            IconBtn("plus", "Nouvelle note", C.accent) {
                val n = Note(newId(), "", "", System.currentTimeMillis())
                data.items.add(0, n); data.save(); editId = n.id
            }
        }) {
            if (data.items.isEmpty()) Empty("Aucune note. Touchez + pour en créer une.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(data.items.sortedByDescending { it.ts }, key = { it.id }) { n ->
                    Card(onClick = { editId = n.id }) {
                        Text(n.title.ifBlank { "Sans titre" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (n.body.isNotBlank()) {
                            Text(n.body, fontSize = 13.sp, color = C.sub, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                        }
                        val d = Instant.ofEpochMilli(n.ts).atZone(ZoneId.systemDefault()).toLocalDate()
                        Text(d.format(dateFmt), fontSize = 11.sp, color = C.muted, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

/* ───────────────────────── À faire ───────────────────────── */

@Composable
fun TodoApp(store: Store, onBack: () -> Unit) {
    val data = remember { Persisted(store, "tasks", ::parseTask, ::encTask) }
    var text by remember { mutableStateOf("") }
    fun add() {
        if (text.isBlank()) return
        data.items.add(0, Task(newId(), text.trim(), false)); data.save(); text = ""
    }
    AppScreen("À faire", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubField(text, { text = it }, "Nouvelle tâche", Modifier.weight(1f))
            AddButton(::add)
        }
        Spacer(Modifier.height(14.dp))
        if (data.items.isEmpty()) Empty("Rien à faire pour le moment.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.items.sortedBy { it.done }, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(if (t.done) C.accent else C.s3)
                            .clickable {
                                val i = data.items.indexOfFirst { it.id == t.id }
                                data.items[i] = t.copy(done = !t.done); data.save()
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (t.done) Glyph("check", C.ink, 16.dp) }
                    Text(
                        t.text,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp),
                        color = if (t.done) C.muted else C.text,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    )
                    IconBtn("trash", "Supprimer", C.muted) { data.items.remove(t); data.save() }
                }
            }
        }
    }
}

/* ───────────────────────── Budget ───────────────────────── */

val budgetCats = listOf(
    "Logement" to Color(0xFF9BE8C4),
    "Courses" to Color(0xFF8CC8FF),
    "Transports" to Color(0xFFFFB48A),
    "Loisirs" to Color(0xFFE3A0F0),
    "Santé" to Color(0xFFF4DC7A),
)

@Composable
fun BudgetApp(store: Store, onBack: () -> Unit) {
    val data = remember { Persisted(store, "expenses", ::parseExpense, ::encExpense) }
    var limit by remember { mutableStateOf(store.get("budget_limit")?.toDoubleOrNull() ?: 2000.0) }
    var label by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableIntStateOf(1) }
    var editLimit by remember { mutableStateOf(false) }

    val month = YearMonth.now()
    val monthItems = data.items.filter {
        YearMonth.from(Instant.ofEpochMilli(it.ts).atZone(ZoneId.systemDefault())) == month
    }
    val total = monthItems.sumOf { it.amount }
    val perCat = budgetCats.indices.map { i -> monthItems.filter { it.cat == i }.sumOf { it.amount } }
    fun eur(v: Double) = String.format(Locale.FRANCE, "%,.0f €", v)

    AppScreen("Budget", onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(onClick = { editLimit = true }) {
                    Text("Dépensé ce mois-ci", fontSize = 12.sp, color = C.muted)
                    Text(eur(total), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    val ratio = if (limit > 0) (total / limit).toFloat().coerceIn(0f, 1f) else 0f
                    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(C.s3)) {
                        Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(if (total > limit) C.danger else C.accent))
                    }
                    Text(
                        if (limit >= total) "Reste ${eur(limit - total)} sur ${eur(limit)} (toucher pour modifier)"
                        else "Dépassement de ${eur(total - limit)} sur ${eur(limit)}",
                        fontSize = 12.sp, color = C.sub, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                Card {
                    Text("Par catégorie", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        PieChart(perCat, Modifier.size(140.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            budgetCats.forEachIndexed { i, (name, color) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                                    Column(Modifier.padding(start = 10.dp)) {
                                        Text(name, fontSize = 13.sp)
                                        val pct = if (total > 0) (perCat[i] / total * 100).toInt() else 0
                                        Text("${eur(perCat[i])} · $pct %", fontSize = 11.sp, color = C.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card {
                    Text("Ajouter une dépense", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    HubField(label, { label = it }, "Libellé", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    HubField(amount, { amount = it }, "Montant (€)", Modifier.fillMaxWidth(), keyboard = KeyboardType.Decimal)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        budgetCats.forEachIndexed { i, (name, color) ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (cat == i) color else C.s3)
                                    .clickable { cat = i }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(name.take(5), fontSize = 11.sp, color = if (cat == i) C.ink else C.sub, maxLines = 1) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accent)
                            .clickable {
                                val v = amount.replace(',', '.').toDoubleOrNull()
                                if (v != null && v > 0) {
                                    data.items.add(0, Expense(newId(), label.trim().ifBlank { budgetCats[cat].first }, v, cat, System.currentTimeMillis()))
                                    data.save(); label = ""; amount = ""
                                }
                            }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Ajouter", color = C.ink, fontWeight = FontWeight.SemiBold) }
                }
            }
            items(monthItems.sortedByDescending { it.ts }, key = { it.id }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(budgetCats[e.cat].second))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(e.label)
                        Text(budgetCats[e.cat].first, fontSize = 11.sp, color = C.muted)
                    }
                    Text(String.format(Locale.FRANCE, "%.2f €", e.amount), fontWeight = FontWeight.Medium)
                    IconBtn("trash", "Supprimer", C.muted) { data.items.remove(e); data.save() }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (editLimit) {
        var v by remember { mutableStateOf(limit.toLong().toString()) }
        AlertDialog(
            onDismissRequest = { editLimit = false },
            containerColor = C.s2,
            title = { Text("Budget mensuel") },
            text = { HubField(v, { v = it }, "Montant (€)", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number) },
            confirmButton = {
                TextButton(onClick = {
                    v.replace(',', '.').toDoubleOrNull()?.let { limit = it; store.put("budget_limit", it.toString()) }
                    editLimit = false
                }) { Text("Valider", color = C.accent) }
            },
            dismissButton = { TextButton(onClick = { editLimit = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}

@Composable
fun PieChart(values: List<Double>, modifier: Modifier = Modifier) {
    val total = values.sum()
    Canvas(modifier) {
        if (total <= 0) {
            drawCircle(C.s3, style = Stroke(width = size.minDimension * 0.18f))
            return@Canvas
        }
        var start = -90f
        values.forEachIndexed { i, v ->
            if (v > 0) {
                val sweep = (v / total * 360.0).toFloat()
                drawArc(
                    color = budgetCats[i].second,
                    startAngle = start, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset.Zero, size = Size(size.width, size.height),
                )
                start += sweep
            }
        }
    }
}

/* ───────────────────────── Calculette ───────────────────────── */

@Composable
fun CalcApp(onBack: () -> Unit) {
    var disp by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(false) }

    fun cur() = disp.replace(',', '.').toDoubleOrNull() ?: 0.0
    fun fmt(d: Double): String =
        if (d == Math.rint(d) && Math.abs(d) < 1e12) d.toLong().toString()
        else java.math.BigDecimal(d).setScale(8, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
    fun reset() { disp = "0"; acc = null; op = null; fresh = false }
    fun compute(a: Double, b: Double, o: String): Double? = when (o) {
        "+" -> a + b
        "−" -> a - b
        "×" -> a * b
        else -> if (b == 0.0) null else a / b
    }

    fun press(k: String) {
        when (k) {
            "C" -> reset()
            "±" -> if (disp != "0") disp = if (disp.startsWith("-")) disp.drop(1) else "-$disp"
            "%" -> disp = fmt(cur() / 100)
            "+", "−", "×", "÷" -> {
                val a = acc; val o = op
                if (a != null && o != null && !fresh) {
                    val r = compute(a, cur(), o)
                    if (r == null) { reset(); disp = "Erreur"; return }
                    acc = r; disp = fmt(r)
                } else acc = cur()
                op = k; fresh = true
            }
            "=" -> {
                val a = acc; val o = op
                if (a != null && o != null) {
                    val r = compute(a, cur(), o)
                    if (r == null) { reset(); disp = "Erreur"; return }
                    disp = fmt(r); acc = null; op = null; fresh = true
                }
            }
            "," -> {
                if (fresh || disp == "Erreur") { disp = "0,"; fresh = false }
                else if (!disp.contains(',')) disp += ","
            }
            else -> {
                if (fresh || disp == "0" || disp == "Erreur") { disp = k; fresh = false }
                else if (disp.length < 12) disp += k
            }
        }
    }

    val rows = listOf(
        listOf("C", "±", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", ",", "="),
    )
    AppScreen("Calculette", onBack) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp), contentAlignment = Alignment.BottomEnd) {
            Text(disp, fontSize = 56.sp, fontWeight = FontWeight.Light, maxLines = 1)
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val isOp = k in listOf("+", "−", "×", "÷", "=")
                    val isTop = k in listOf("C", "±", "%")
                    Box(
                        Modifier
                            .weight(if (k == "0") 2.1f else 1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isOp) Color(0xFFC6E87A) else if (isTop) C.s3 else C.s1)
                            .clickable { press(k) },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, fontSize = 24.sp, color = if (isOp) C.ink else C.text, fontWeight = FontWeight.Medium) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/* ───────────────────────── Journal ───────────────────────── */

@Composable
fun JournalApp(store: Store, onBack: () -> Unit) {
    val data = remember { Persisted(store, "journal", ::parseEntry, ::encEntry) }
    val today = LocalDate.now().toString()
    val existing = data.items.firstOrNull { it.date == today }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var mood by remember { mutableIntStateOf(existing?.mood ?: 2) }
    val moods = listOf("😞", "😕", "😐", "🙂", "😄")
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH) }

    AppScreen("Journal", onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card {
                    Text("Aujourd'hui", fontSize = 12.sp, color = C.muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        moods.forEachIndexed { i, m ->
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (mood == i) C.s3 else C.s2).clickable { mood = i },
                                contentAlignment = Alignment.Center,
                            ) { Text(m, fontSize = 22.sp) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    HubField(text, { text = it }, "Comment s'est passée la journée ?", Modifier.fillMaxWidth().heightIn(min = 120.dp), singleLine = false)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.accent)
                            .clickable {
                                val e = Entry(existing?.id ?: newId(), today, mood, text)
                                val i = data.items.indexOfFirst { it.date == today }
                                if (i >= 0) data.items[i] = e else data.items.add(0, e)
                                data.save()
                            }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Enregistrer", color = C.ink, fontWeight = FontWeight.SemiBold) }
                }
            }
            items(data.items.filter { it.date != today }.sortedByDescending { it.date }, key = { it.id }) { e ->
                Card {
                    val d = runCatching { LocalDate.parse(e.date).format(fmt) }.getOrDefault(e.date)
                    Text("${moods.getOrElse(e.mood) { "" }}  $d", fontSize = 13.sp, color = C.sub)
                    Text(e.text, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/* ───────────────────────── Habitudes ───────────────────────── */

private fun streak(days: Set<String>): Int {
    var d = LocalDate.now()
    if (d.toString() !in days) d = d.minusDays(1)
    var n = 0
    while (d.toString() in days) { n++; d = d.minusDays(1) }
    return n
}

@Composable
fun HabitsApp(store: Store, onBack: () -> Unit) {
    val data = remember { Persisted(store, "habits", ::parseHabit, ::encHabit) }
    var text by remember { mutableStateOf("") }
    val today = LocalDate.now().toString()
    AppScreen("Habitudes", onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubField(text, { text = it }, "Nouvelle habitude", Modifier.weight(1f))
            AddButton {
                if (text.isNotBlank()) { data.items.add(Habit(newId(), text.trim(), emptySet())); data.save(); text = "" }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (data.items.isEmpty()) Empty("Ajoutez une habitude à suivre chaque jour.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(data.items.toList(), key = { it.id }) { h ->
                val done = today in h.days
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(if (done) Color(0xFFC6E87A) else C.s3)
                            .clickable {
                                val i = data.items.indexOfFirst { it.id == h.id }
                                data.items[i] = h.copy(days = if (done) h.days - today else h.days + today)
                                data.save()
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (done) Glyph("check", C.ink, 18.dp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text(h.name)
                        Text("Série : ${streak(h.days)} jour(s)", fontSize = 11.sp, color = C.muted)
                    }
                    IconBtn("trash", "Supprimer", C.muted) { data.items.remove(h); data.save() }
                }
            }
        }
    }
}
