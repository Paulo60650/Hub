package com.hub.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

data class MailCfg(val email: String, val pass: String, val imap: String, val smtp: String, val smtpPort: Int)
data class MailItem(val num: Int, val from: String, val subject: String, val date: Long, val seen: Boolean)

fun loadMailCfg(store: Store): MailCfg? {
    val e = store.get("mail_email") ?: return null
    val p = store.get("mail_pass") ?: return null
    return try {
        MailCfg(
            e, Crypto.dec(p), store.get("mail_imap") ?: "", store.get("mail_smtp") ?: "",
            store.get("mail_port")?.toIntOrNull() ?: 465,
        )
    } catch (ex: Exception) { null }
}

fun saveMailCfg(store: Store, c: MailCfg) {
    store.put("mail_email", c.email)
    store.put("mail_pass", Crypto.enc(c.pass))
    store.put("mail_imap", c.imap)
    store.put("mail_smtp", c.smtp)
    store.put("mail_port", c.smtpPort.toString())
}

/** Réglages usuels selon le domaine de l'adresse. */
fun guessMail(email: String): Triple<String, String, Int> {
    val d = email.substringAfter('@', "").lowercase()
    return when {
        d == "gmail.com" || d == "googlemail.com" -> Triple("imap.gmail.com", "smtp.gmail.com", 465)
        d.startsWith("outlook.") || d.startsWith("hotmail.") || d.startsWith("live.") ->
            Triple("outlook.office365.com", "smtp-mail.outlook.com", 587)
        d.startsWith("yahoo.") -> Triple("imap.mail.yahoo.com", "smtp.mail.yahoo.com", 465)
        d == "icloud.com" || d == "me.com" -> Triple("imap.mail.me.com", "smtp.mail.me.com", 587)
        d == "orange.fr" -> Triple("imap.orange.fr", "smtp.orange.fr", 465)
        d == "free.fr" -> Triple("imap.free.fr", "smtp.free.fr", 465)
        d.isNotEmpty() -> Triple("imap.$d", "smtp.$d", 465)
        else -> Triple("", "", 465)
    }
}

private fun <T> withImap(c: MailCfg, block: (Folder) -> T): T {
    val props = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", c.imap)
        put("mail.imaps.port", "993")
        put("mail.imaps.connectiontimeout", "10000")
        put("mail.imaps.timeout", "20000")
    }
    val store = Session.getInstance(props).getStore("imaps")
    store.connect(c.imap, c.email, c.pass)
    try {
        val folder = store.getFolder("INBOX")
        folder.open(Folder.READ_ONLY)
        try {
            return block(folder)
        } finally {
            folder.close(false)
        }
    } finally {
        store.close()
    }
}

fun fetchInbox(c: MailCfg): List<MailItem> = withImap(c) { f ->
    val n = f.messageCount
    if (n == 0) emptyList() else {
        val msgs = f.getMessages(maxOf(1, n - 29), n)
        msgs.map { m ->
            MailItem(
                m.messageNumber,
                (m.from?.firstOrNull() as? InternetAddress)?.let { it.personal ?: it.address } ?: "?",
                m.subject ?: "(sans objet)",
                m.sentDate?.time ?: 0L,
                m.flags.contains(javax.mail.Flags.Flag.SEEN),
            )
        }.reversed()
    }
}

private fun textOf(p: Part): String {
    if (p.isMimeType("text/plain")) return p.content as String
    if (p.isMimeType("text/html")) {
        return (p.content as String).replace(Regex("<(style|script)[\\s\\S]*?</\\1>"), "")
            .replace(Regex("<br\\s*/?>|</p>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    }
    if (p.isMimeType("multipart/*")) {
        val mp = p.content as Multipart
        var html = ""
        for (i in 0 until mp.count) {
            val part = mp.getBodyPart(i)
            if (part.isMimeType("text/plain")) return part.content as String
            val t = textOf(part)
            if (t.isNotBlank() && html.isBlank()) html = t
        }
        return html
    }
    return ""
}

fun fetchBody(c: MailCfg, num: Int): String = withImap(c) { f -> textOf(f.getMessage(num)).trim() }

fun sendMail(c: MailCfg, to: String, subject: String, body: String) {
    val props = Properties().apply {
        put("mail.smtp.host", c.smtp)
        put("mail.smtp.port", c.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.connectiontimeout", "10000")
        put("mail.smtp.timeout", "20000")
        if (c.smtpPort == 465) put("mail.smtp.ssl.enable", "true") else put("mail.smtp.starttls.enable", "true")
    }
    val session = Session.getInstance(props)
    val msg = MimeMessage(session).apply {
        setFrom(InternetAddress(c.email))
        setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        setSubject(subject, "UTF-8")
        setText(body, "UTF-8")
        sentDate = Date()
    }
    val t = session.getTransport("smtp")
    t.connect(c.smtp, c.smtpPort, c.email, c.pass)
    try { t.sendMessage(msg, msg.allRecipients) } finally { t.close() }
}

@Composable
fun MailApp(store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(loadMailCfg(store)) }
    var editingCfg by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var openItem by remember { mutableStateOf<MailItem?>(null) }
    var composeTo by remember { mutableStateOf<Triple<String, String, String>?>(null) } // à, objet, corps
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale.FRENCH) }

    BackHandler(openItem != null || composeTo != null || editingCfg) {
        when {
            composeTo != null -> composeTo = null
            openItem != null -> openItem = null
            else -> editingCfg = false
        }
    }

    val c = cfg
    if (c == null || editingCfg) {
        MailSetup(store, c, onBack = { if (c == null) onBack() else editingCfg = false }) { cfg = it; editingCfg = false }
        return
    }

    val ct = composeTo
    if (ct != null) {
        var to by remember { mutableStateOf(ct.first) }
        var subject by remember { mutableStateOf(ct.second) }
        var body by remember { mutableStateOf(ct.third) }
        var sending by remember { mutableStateOf(false) }
        AppScreen("Nouveau mail", onBack = { composeTo = null }) {
            HubField(to, { to = it }, "À", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
            Spacer(Modifier.height(8.dp))
            HubField(subject, { subject = it }, "Objet", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            HubField(body, { body = it }, "Message", Modifier.fillMaxWidth().weight(1f), singleLine = false)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(if (sending) "Envoi…" else "Envoyer", Modifier.fillMaxWidth()) {
                if (sending || to.isBlank()) return@PrimaryButton
                sending = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { runCatching { sendMail(c, to, subject, body) }.isSuccess }
                    sending = false
                    if (ok) { toast(ctx, "Mail envoyé"); composeTo = null } else toast(ctx, "Échec de l'envoi (vérifiez les réglages)")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        return
    }

    val item = openItem
    if (item != null) {
        val body by rememberAsync(item.num, initial = "Chargement…") { fetchBody(c, item.num) }
        AppScreen("Mail", onBack = { openItem = null }, actions = {
            IconBtn("messages", "Répondre", C.accent) {
                composeTo = Triple(item.from, "Re: " + item.subject, "\n\n> " + body.replace("\n", "\n> "))
            }
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(item.subject, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${item.from} · ${fmt.format(Date(item.date))}", fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(vertical = 8.dp))
                Text(body, fontSize = 14.sp, color = C.sub)
            }
        }
        return
    }

    AppScreen("Mail", onBack, actions = {
        IconBtn("settings", "Réglages", C.sub) { editingCfg = true }
        IconBtn("plus", "Écrire", C.accent) { composeTo = Triple("", "", "") }
    }) {
        var error by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(true) }
        var inbox by remember { mutableStateOf(emptyList<MailItem>()) }
        LaunchedEffect(refresh) {
            loading = true; error = null
            val r = withContext(Dispatchers.IO) { runCatching { fetchInbox(c) } }
            r.onSuccess { inbox = it }.onFailure { error = it.message ?: "Connexion impossible" }
            loading = false
        }
        Text(c.email, fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(bottom = 8.dp))
        if (loading) Empty("Chargement…")
        error?.let { Empty("Erreur : $it") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(inbox, key = { it.num }) { m ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.s1)
                        .clickable { openItem = m }.padding(14.dp),
                ) {
                    Text(m.from, fontSize = 12.sp, color = C.muted, maxLines = 1)
                    Text(m.subject, fontWeight = if (m.seen) FontWeight.Normal else FontWeight.SemiBold, maxLines = 1)
                    Text(fmt.format(Date(m.date)), fontSize = 11.sp, color = C.muted)
                }
            }
        }
    }
}

@Composable
private fun MailSetup(store: Store, current: MailCfg?, onBack: () -> Unit, onSaved: (MailCfg) -> Unit) {
    var email by remember { mutableStateOf(current?.email ?: "") }
    var pass by remember { mutableStateOf(current?.pass ?: "") }
    var imap by remember { mutableStateOf(current?.imap ?: "") }
    var smtp by remember { mutableStateOf(current?.smtp ?: "") }
    var port by remember { mutableStateOf((current?.smtpPort ?: 465).toString()) }
    AppScreen("Compte mail", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Saisissez votre adresse et un « mot de passe d'application » (Gmail, Outlook, iCloud et Yahoo " +
                    "l'exigent : à créer dans les réglages de sécurité du compte). Il est stocké chiffré sur le téléphone.",
                fontSize = 13.sp, color = C.sub,
            )
            HubField(email, {
                email = it
                val g = guessMail(it)
                imap = g.first; smtp = g.second; port = g.third.toString()
            }, "Adresse mail", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email)
            HubField(pass, { pass = it }, "Mot de passe d'application", Modifier.fillMaxWidth())
            HubField(imap, { imap = it }, "Serveur IMAP (réception)", Modifier.fillMaxWidth())
            HubField(smtp, { smtp = it }, "Serveur SMTP (envoi)", Modifier.fillMaxWidth())
            HubField(port, { port = it }, "Port SMTP (465 ou 587)", Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
            Spacer(Modifier.height(6.dp))
            PrimaryButton("Enregistrer", Modifier.fillMaxWidth()) {
                if (email.isNotBlank() && pass.isNotBlank() && imap.isNotBlank()) {
                    val c = MailCfg(email.trim(), pass.trim(), imap.trim(), smtp.trim(), port.toIntOrNull() ?: 465)
                    saveMailCfg(store, c)
                    onSaved(c)
                }
            }
        }
    }
}
