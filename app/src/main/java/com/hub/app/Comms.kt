package com.hub.app

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone as PhoneKind
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ───────────────────────── Appels ───────────────────────── */

data class CallEntry(val number: String, val name: String?, val type: Int, val date: Long)

fun loadCalls(ctx: Context): List<CallEntry> {
    val out = mutableListOf<CallEntry>()
    ctx.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
        null, null, "${CallLog.Calls.DATE} DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 60) {
            out.add(CallEntry(c.getString(0) ?: "", c.getString(1), c.getInt(2), c.getLong(3)))
        }
    }
    return out
}

fun placeCall(ctx: Context, number: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        toast(ctx, "Appel impossible")
    }
}

private val dayFmt = SimpleDateFormat("d MMM HH:mm", Locale.FRENCH)

@Composable
fun PhoneApp(onBack: () -> Unit) {
    val ctx = LocalContext.current
    AppScreen("Appels", onBack) {
        PermissionGate(
            listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
            "Autoriser les appels",
            "Pour composer un numéro et afficher votre journal d'appels.",
        ) {
            var tab by remember { mutableIntStateOf(0) }
            var number by remember { mutableStateOf("") }
            Tabs(listOf("Clavier", "Récents"), tab) { tab = it }
            if (tab == 0) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(number.ifEmpty { " " }, fontSize = 36.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    }
                    val rows = listOf(
                        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"),
                    )
                    rows.forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            r.forEach { d ->
                                Box(
                                    Modifier.size(72.dp).clip(CircleShape).background(C.s1).clickable { number += d },
                                    contentAlignment = Alignment.Center,
                                ) { Text(d, fontSize = 26.sp) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp))
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF8FE0A5))
                                .clickable { if (number.isNotEmpty()) placeCall(ctx, number) },
                            contentAlignment = Alignment.Center,
                        ) { Glyph("phone", C.ink, 30.dp) }
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).clickable { number = number.dropLast(1) },
                            contentAlignment = Alignment.Center,
                        ) { Glyph("erase", C.sub, 26.dp) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                val calls by rememberAsync(initial = emptyList<CallEntry>()) { loadCalls(ctx) }
                if (calls.isEmpty()) Empty("Aucun appel récent.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(calls) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                                .clickable { placeCall(ctx, c.number) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name?.ifBlank { null } ?: c.number.ifBlank { "Inconnu" },
                                    color = if (c.type == CallLog.Calls.MISSED_TYPE) C.danger else C.text,
                                )
                                val kind = when (c.type) {
                                    CallLog.Calls.INCOMING_TYPE -> "Reçu"
                                    CallLog.Calls.OUTGOING_TYPE -> "Émis"
                                    CallLog.Calls.MISSED_TYPE -> "Manqué"
                                    else -> "Appel"
                                }
                                Text("$kind · ${dayFmt.format(Date(c.date))}", fontSize = 11.sp, color = C.muted)
                            }
                            Glyph("phone", Color(0xFF8FE0A5), 20.dp)
                        }
                    }
                }
            }
        }
    }
}

/* ───────────────────────── Contacts ───────────────────────── */

data class Contact(val id: Long, val name: String, val number: String)

fun loadContacts(ctx: Context): List<Contact> {
    val seen = HashSet<Long>()
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        PhoneKind.CONTENT_URI,
        arrayOf(PhoneKind.CONTACT_ID, PhoneKind.DISPLAY_NAME, PhoneKind.NUMBER),
        null, null, "${PhoneKind.DISPLAY_NAME} COLLATE NOCASE ASC",
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            if (seen.add(id)) out.add(Contact(id, c.getString(1) ?: "", c.getString(2) ?: ""))
        }
    }
    return out
}

fun addContact(ctx: Context, name: String, number: String) {
    val ops = ArrayList<ContentProviderOperation>()
    ops.add(
        ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
    )
    ops.add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
    )
    ops.add(
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            .build(),
    )
    ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
}

@Composable
fun ContactsApp(onBack: () -> Unit, go: (String, String?) -> Unit) {
    val ctx = LocalContext.current
    AppScreen("Contacts", onBack) {
        PermissionGate(
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.CALL_PHONE),
            "Autoriser les contacts",
            "Pour afficher, ajouter et appeler vos contacts.",
        ) {
            var refresh by remember { mutableIntStateOf(0) }
            val all by rememberAsync(refresh, initial = emptyList<Contact>()) { loadContacts(ctx) }
            var query by remember { mutableStateOf("") }
            var selected by remember { mutableStateOf<Contact?>(null) }
            var adding by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubField(query, { query = it }, "Rechercher", Modifier.weight(1f))
                AddButton { adding = true }
            }
            Spacer(Modifier.height(12.dp))
            val shown = all.filter { it.name.contains(query, true) || it.number.contains(query) }
            if (shown.isEmpty()) Empty("Aucun contact.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                            .clickable { selected = c }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF8CC8FF)),
                            contentAlignment = Alignment.Center,
                        ) { Text(c.name.take(1).uppercase(), color = C.ink, fontWeight = FontWeight.SemiBold) }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.name)
                            Text(c.number, fontSize = 12.sp, color = C.muted)
                        }
                    }
                }
            }

            selected?.let { c ->
                AlertDialog(
                    onDismissRequest = { selected = null },
                    containerColor = C.s2,
                    title = { Text(c.name) },
                    text = { Text(c.number, color = C.sub) },
                    confirmButton = {
                        Row {
                            TextButton(onClick = { placeCall(ctx, c.number); selected = null }) { Text("Appeler", color = C.accent) }
                            TextButton(onClick = { selected = null; go("messages", c.number) }) { Text("Message", color = C.accent) }
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                try {
                                    ctx.contentResolver.delete(
                                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id), null, null,
                                    )
                                } catch (e: Exception) { toast(ctx, "Suppression impossible") }
                                selected = null; refresh++
                            }) { Text("Supprimer", color = C.danger) }
                            TextButton(onClick = { selected = null }) { Text("Fermer", color = C.sub) }
                        }
                    },
                )
            }
            if (adding) {
                var n by remember { mutableStateOf("") }
                var p by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { adding = false },
                    containerColor = C.s2,
                    title = { Text("Nouveau contact") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubField(n, { n = it }, "Nom", Modifier.fillMaxWidth())
                            HubField(p, { p = it }, "Téléphone", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (n.isNotBlank() && p.isNotBlank()) {
                                try { addContact(ctx, n.trim(), p.trim()) } catch (e: Exception) { toast(ctx, "Ajout impossible") }
                                adding = false; refresh++
                            }
                        }) { Text("Ajouter", color = C.accent) }
                    },
                    dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler", color = C.sub) } },
                )
            }
        }
    }
}

/* ───────────────────────── Messages (SMS) ───────────────────────── */

data class Sms(val address: String, val body: String, val date: Long, val sent: Boolean)

fun parseSms(o: JSONObject) = Sms(o.getString("a"), o.getString("b"), o.getLong("d"), true)
fun encSms(s: Sms) = JSONObject().put("a", s.address).put("b", s.body).put("d", s.date)

private fun smsKey(a: String): String = a.filter { it.isDigit() }.takeLast(9).ifEmpty { a }

fun loadSms(ctx: Context): List<Sms> {
    val out = mutableListOf<Sms>()
    ctx.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
        null, null, "${Telephony.Sms.DATE} DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 3000) {
            out.add(
                Sms(
                    c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2),
                    c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX,
                ),
            )
        }
    }
    return out
}

fun contactName(ctx: Context, number: String): String? {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return try {
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }
}

fun sendSms(ctx: Context, to: String, text: String) {
    val sm: SmsManager =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
    sm.sendMultipartTextMessage(to, null, sm.divideMessage(text), null, null)
}

@Composable
fun MessagesApp(store: Store, initialAddress: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var thread by remember { mutableStateOf(initialAddress) }
    BackHandler(thread != null) { thread = null }
    val outbox = remember { Persisted(store, "sms_out", ::parseSms, ::encSms) }
    var refresh by remember { mutableIntStateOf(0) }
    var newDialog by remember { mutableStateOf(false) }

    val t = thread
    AppScreen(
        if (t == null) "Messages" else (contactName(ctx, t) ?: t),
        onBack = { if (t != null) thread = null else onBack() },
        actions = {
            if (t == null) IconBtn("plus", "Nouveau", C.accent) { newDialog = true }
            else IconBtn("check", "Actualiser", C.sub) { refresh++ }
        },
    ) {
        PermissionGate(
            listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS),
            "Autoriser les SMS",
            "Pour lire vos SMS et en envoyer depuis Hub. (Les RCS / WhatsApp ne sont pas accessibles.)",
        ) {
            val sms by rememberAsync(refresh, initial = emptyList<Sms>()) { loadSms(ctx) }
            val all = (sms + outbox.items).sortedByDescending { it.date }

            if (t == null) {
                val threads = all.groupBy { smsKey(it.address) }.values.map { it.first() }.take(80)
                val names by rememberAsync(threads.size, refresh, initial = emptyMap<String, String?>()) {
                    threads.associate { it.address to contactName(ctx, it.address) }
                }
                if (threads.isEmpty()) Empty("Aucune conversation.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(threads, key = { smsKey(it.address) }) { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                                .clickable { thread = m.address }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF8CC8FF)),
                                contentAlignment = Alignment.Center,
                            ) { Glyph("messages", C.ink, 20.dp) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(names[m.address] ?: m.address, fontWeight = FontWeight.Medium)
                                Text(m.body, fontSize = 12.sp, color = C.muted, maxLines = 1)
                            }
                        }
                    }
                }
            } else {
                val key = smsKey(t)
                val msgs = all.filter { smsKey(it.address) == key }
                var text by remember { mutableStateOf("") }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(msgs) { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.sent) Arrangement.End else Arrangement.Start) {
                            Box(
                                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (m.sent) C.accent else C.s2).padding(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(m.body, color = if (m.sent) C.ink else C.text, fontSize = 14.sp) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HubField(text, { text = it }, "Message", Modifier.weight(1f))
                    AddButton {
                        if (text.isNotBlank()) {
                            try {
                                sendSms(ctx, t, text.trim())
                                outbox.items.add(Sms(t, text.trim(), System.currentTimeMillis(), true))
                                outbox.save()
                                text = ""
                            } catch (e: Exception) {
                                toast(ctx, "Envoi impossible")
                            }
                        }
                    }
                }
            }
        }
    }

    if (newDialog) {
        var num by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newDialog = false },
            containerColor = C.s2,
            title = { Text("Nouveau message") },
            text = { HubField(num, { num = it }, "Numéro", Modifier.fillMaxWidth(), keyboard = KeyboardType.Phone) },
            confirmButton = {
                TextButton(onClick = { if (num.isNotBlank()) thread = num.trim(); newDialog = false }) {
                    Text("Écrire", color = C.accent)
                }
            },
            dismissButton = { TextButton(onClick = { newDialog = false }) { Text("Annuler", color = C.sub) } },
        )
    }
}
