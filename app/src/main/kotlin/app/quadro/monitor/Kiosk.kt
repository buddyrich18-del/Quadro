package app.quadro.monitor

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.quadro.core.KioskItem
import app.quadro.core.UnitCategory
import app.quadro.core.WebCard
import app.quadro.core.parseKioskFragments
import app.quadro.core.unitCategory
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Legge i testi visibili della pagina con la loro posizione: [testo, alto, sinistra, altezza, larghezza]. */
private const val KIOSK_SCRIPT = """
(function(){
  try {
    var out = [];
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var n;
    while ((n = walker.nextNode())) {
      var t = (n.nodeValue || '').replace(/\s+/g, ' ').trim();
      if (!t) continue;
      var p = n.parentElement;
      if (!p) continue;
      var tag = p.tagName;
      if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'NOSCRIPT') continue;
      var cs = window.getComputedStyle(p);
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      var range = document.createRange();
      range.selectNodeContents(n);
      var r = range.getBoundingClientRect();
      if (r.width <= 0 || r.height <= 0) continue;
      out.push([t, Math.round(r.top + window.scrollY), Math.round(r.left), Math.round(r.height), Math.round(r.width)]);
    }
    return JSON.stringify(out);
  } catch (e) {
    return '[]';
  }
})()
"""

private fun unquoteJs(raw: String?): String? {
    if (raw == null || raw == "null") return null
    return try {
        JSONTokener(raw).nextValue() as? String
    } catch (e: Exception) {
        null
    }
}

private suspend fun readPage(web: WebView): String? = suspendCancellableCoroutine { cont ->
    web.evaluateJavascript(KIOSK_SCRIPT) { raw -> cont.resume(unquoteJs(raw)) }
}

@Composable
private fun KioskTile(item: KioskItem, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.label, fontSize = 12.sp, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.value, style = MaterialTheme.typography.headlineSmall)
                if (item.unit.isNotBlank()) {
                    Text(item.unit, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun KioskGroup(title: String, items: List<KioskItem>) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { KioskTile(it, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * Mostra i valori letti da una pagina web (per esempio il link pubblico di FusionSolar) a riquadri.
 * La pagina si carica in una vista web, ne viene letto il testo visibile e, se serve, si può
 * mostrare la pagina originale. Dipende da come è scritta la pagina: se Huawei la cambia può servire una taratura.
 */
@Composable
fun KioskView(card: WebCard) {
    val ctx = LocalContext.current
    var items by remember(card.url) { mutableStateOf<List<KioskItem>>(emptyList()) }
    var lines by remember(card.url) { mutableStateOf<List<String>>(emptyList()) }
    var updated by remember(card.url) { mutableStateOf<Long?>(null) }
    var gaveUp by remember(card.url) { mutableStateOf(false) }
    var showPage by remember(card.url) { mutableStateOf(false) }
    var showText by remember(card.url) { mutableStateOf(false) }

    val webView = remember(card.url) {
        WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            loadUrl(card.url)
        }
    }
    DisposableEffect(webView) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    LaunchedEffect(webView) {
        while (true) {
            var last = ""
            var found = false
            for (attempt in 0 until 14) {
                delay(2500)
                val json = readPage(webView) ?: continue
                val parsed = parseKioskFragments(json)
                lines = parsed.lines
                if (parsed.items.isNotEmpty()) {
                    items = parsed.items
                    updated = System.currentTimeMillis()
                    found = true
                    gaveUp = false
                    val signature = parsed.items.joinToString("|") { it.label + it.value + it.unit }
                    if (signature == last) break
                    last = signature
                }
            }
            if (!found && items.isEmpty()) gaveUp = true
            delay(300_000)
            webView.reload()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) {
            if (gaveUp) {
                Text(
                    "Non riconosco valori in questa pagina. Apri la pagina originale e guarda il testo letto: se serve, mandami una schermata.",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Leggo i dati dalla pagina…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            KioskGroup("Potenza", items.filter { unitCategory(it.unit) == UnitCategory.POWER })
            KioskGroup("Energia", items.filter { unitCategory(it.unit) == UnitCategory.ENERGY })
            KioskGroup("Altro", items.filter { unitCategory(it.unit) == UnitCategory.OTHER })
            updated?.let {
                Text(
                    "Letto alle ${fmtTime(it, "HH:mm")} · si aggiorna ogni 5 minuti",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { showPage = !showPage }) {
                Text(if (showPage) "Nascondi la pagina originale" else "Mostra la pagina originale")
            }
            TextButton(onClick = { showText = !showText }) {
                Text(if (showText) "Nascondi il testo letto" else "Testo letto")
            }
        }
        if (showText) {
            Text(
                if (lines.isEmpty()) "Nessun testo letto finora." else lines.take(80).joinToString("\n"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // La vista web resta sempre presente (anche nascosta): serve per leggere i dati.
        Column(
            Modifier
                .fillMaxWidth()
                .height(if (showPage) 460.dp else 0.dp)
                .clipToBounds(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().requiredHeight(460.dp),
                factory = { webView },
            )
        }
    }
}
