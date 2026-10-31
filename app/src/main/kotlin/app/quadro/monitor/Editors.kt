package app.quadro.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.quadro.core.AlertRule
import app.quadro.core.Aggregate
import app.quadro.core.AppConfig
import app.quadro.core.Band
import app.quadro.core.Bucket
import app.quadro.core.CardConfig
import app.quadro.core.ChartCard
import app.quadro.core.ChartKind
import app.quadro.core.ClimateCard
import app.quadro.core.EnergyCard
import app.quadro.core.FeedInfo
import app.quadro.core.LinkFormat
import app.quadro.core.Period
import app.quadro.core.SeriesRef
import app.quadro.core.Source
import app.quadro.core.AppLanguage
import app.quadro.core.ThemeMode
import app.quadro.core.WebCard
import app.quadro.core.normalizeBaseUrl
import app.quadro.core.parseAccountQr
import app.quadro.core.parseNumber
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.util.UUID

// ---------- elementi comuni ----------

@Composable
fun EditorFrame(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    error: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Indietro") }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = onSave) { Text("Salva") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (onDelete != null) OutlinedButton(onClick = onDelete) { Text("Elimina") }
        }
    }
}

@Composable
fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

/** Campo numerico: il testo resta com'è scritto, il valore viene letto con parseNumber. */
@Composable
fun NumField(label: String, initial: Double?, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { fmtNum(it, 1).trimEnd('0').trimEnd('.', ',') } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(parseNumber(it))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun Picker(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(options.firstOrNull { it.first == selected }?.second ?: label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    onSelect(key)
                    open = false
                })
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text("  $label")
    }
}

@Composable
fun ColorRow(selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SeriesPalette.forEach { c ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (c == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(c) },
            )
        }
    }
}

private fun feedLabel(f: FeedInfo): String {
    val tag = if (f.tag.isBlank()) "" else " (${f.tag})"
    return "${f.name}$tag #${f.id}"
}

@Composable
fun FeedPicker(cfg: AppConfig, sourceId: String?, feedId: String?, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var feeds by remember { mutableStateOf<List<FeedInfo>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = sourceId != null && !busy, onClick = {
            busy = true
            msg = null
            scope.launch {
                when (val r = loadResult { repo.feedList(cfg, sourceId) }) {
                    is Load.Ok -> {
                        feeds = r.value
                        if (r.value.isEmpty()) msg = "Nessun feed trovato"
                    }
                    is Load.Err -> msg = r.message
                    else -> {}
                }
                busy = false
            }
        }) { Text(if (busy) "Carico…" else "Carica elenco feed") }
        if (feeds.isNotEmpty()) {
            Picker("Scegli un feed", feeds.map { it.id to feedLabel(it) }, feedId) { onChange(it) }
        }
        Field("ID del feed", feedId ?: "") { onChange(it.trim()) }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Una serie di dati: feed di un server Emoncms oppure link pubblico JSON/CSV. */
@Composable
fun SeriesBox(
    index: Int,
    ref: SeriesRef,
    cfg: AppConfig,
    onChange: (SeriesRef) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var mode by remember { mutableStateOf(if (ref.isLink) "link" else "emoncms") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Serie ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Rimuovi") }
            }
            Field("Nome", ref.label) { onChange(ref.copy(label = it)) }
            Field("Unità (es. °C, %, W)", ref.unit) { onChange(ref.copy(unit = it)) }
            Text("Colore della linea", style = MaterialTheme.typography.titleSmall)
            ColorRow(ref.colorArgb) { onChange(ref.copy(colorArgb = it)) }
            Picker(
                "Origine",
                listOf("emoncms" to "Server Emoncms", "link" to "Link pubblico (JSON o CSV)"),
                mode,
            ) {
                mode = it
                onChange(
                    if (it == "link") ref.copy(sourceId = null, feedId = null)
                    else ref.copy(linkUrl = null),
                )
            }
            if (mode == "emoncms") {
                if (cfg.sources.isEmpty()) {
                    Text("Aggiungi prima un server dalla schermata Server.", color = MaterialTheme.colorScheme.error)
                } else {
                    Picker("Scegli il server", cfg.sources.map { it.id to it.name }, ref.sourceId) {
                        onChange(ref.copy(sourceId = it))
                    }
                    FeedPicker(cfg, ref.sourceId, ref.feedId) { onChange(ref.copy(feedId = it)) }
                }
            } else {
                Field("Indirizzo del link (https://…)", ref.linkUrl ?: "") { onChange(ref.copy(linkUrl = it.trim())) }
                Picker(
                    "Formato",
                    listOf("JSON" to "JSON", "CSV" to "CSV"),
                    ref.linkFormat.name,
                ) { onChange(ref.copy(linkFormat = LinkFormat.valueOf(it))) }
                if (ref.linkFormat == LinkFormat.JSON) {
                    Field("Percorso dell'elenco (es. data.points, vuoto se è la radice)", ref.arrayPath) {
                        onChange(ref.copy(arrayPath = it.trim()))
                    }
                }
                Field(
                    if (ref.linkFormat == LinkFormat.JSON) "Chiave del tempo (o posizione 0, 1…)" else "Colonna del tempo (nome o posizione)",
                    ref.timeKey,
                ) { onChange(ref.copy(timeKey = it.trim())) }
                Field(
                    if (ref.linkFormat == LinkFormat.JSON) "Chiave del valore (o posizione 0, 1…)" else "Colonna del valore (nome o posizione)",
                    ref.valueKey,
                ) { onChange(ref.copy(valueKey = it.trim())) }
            }
        }
    }
}

private fun newSeries(index: Int, label: String = "Serie ${index + 1}", unit: String = ""): SeriesRef =
    SeriesRef(label = label, unit = unit, colorArgb = SeriesPalette[index % SeriesPalette.size])

private fun seriesError(s: SeriesRef, n: Int): String? {
    if (s.isLink) {
        val url = s.linkUrl.orEmpty()
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return "Serie $n: l'indirizzo deve iniziare con https://"
        return null
    }
    if (!s.isEmoncms) return "Serie $n: scegli un server e un feed, oppure un link"
    return null
}

// ---------- scheda: grafico ----------

@Composable
fun ChartEditor(initial: ChartCard?, cfg: AppConfig, onSave: (CardConfig) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "Nuovo grafico") }
    var kind by remember { mutableStateOf(initial?.kind ?: ChartKind.LINE) }
    var series by remember { mutableStateOf(initial?.series ?: listOf(newSeries(0))) }
    var period by remember { mutableStateOf(initial?.defaultPeriod ?: Period.D1) }
    var bucket by remember { mutableStateOf(initial?.bucket ?: Bucket.NONE) }
    var aggregate by remember { mutableStateOf(initial?.aggregate ?: Aggregate.AVG) }
    var yMin by remember { mutableStateOf(initial?.yMin) }
    var yMax by remember { mutableStateOf(initial?.yMax) }
    var error by remember { mutableStateOf<String?>(null) }
    val shown = if (kind == ChartKind.BAR) series.take(1) else series

    EditorFrame(
        title = if (initial == null) "Nuovo grafico" else "Modifica grafico",
        onBack = onBack,
        onDelete = onDelete,
        error = error,
        onSave = {
            val bad = shown.mapIndexedNotNull { i, s -> seriesError(s, i + 1) }.firstOrNull()
            if (title.isBlank()) {
                error = "Scrivi un titolo"
            } else if (bad != null) {
                error = bad
            } else {
                onSave(
                    ChartCard(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        kind = kind,
                        series = shown,
                        defaultPeriod = period,
                        bucket = bucket,
                        aggregate = aggregate,
                        yMin = yMin,
                        yMax = yMax,
                    ),
                )
            }
        },
    ) {
        Field("Titolo", title) { title = it }
        Picker("Tipo", listOf("LINE" to "Linee", "BAR" to "Barre"), kind.name) { kind = ChartKind.valueOf(it) }
        shown.forEachIndexed { i, s ->
            SeriesBox(
                index = i,
                ref = s,
                cfg = cfg,
                onChange = { updated -> series = series.toMutableList().also { l -> l[i] = updated } },
                onRemove = if (kind == ChartKind.LINE && series.size > 1) ({ series = series.filterIndexed { j, _ -> j != i } }) else null,
            )
        }
        if (kind == ChartKind.LINE && series.size < 4) {
            OutlinedButton(onClick = { series = series + newSeries(series.size) }) { Text("Aggiungi una serie") }
        }
        Picker("Periodo iniziale", Period.entries.map { it.name to it.label }, period.name) { period = Period.valueOf(it) }
        Picker(
            "Raggruppa i dati",
            listOf("NONE" to "Nessun raggruppamento", "HOUR" to "Per ora", "DAY" to "Per giorno"),
            bucket.name,
        ) { bucket = Bucket.valueOf(it) }
        if (bucket != Bucket.NONE) {
            Picker(
                "Come combinare i valori",
                listOf("AVG" to "Media", "SUM" to "Somma", "MIN" to "Minimo", "MAX" to "Massimo", "LAST" to "Ultimo"),
                aggregate.name,
            ) { aggregate = Aggregate.valueOf(it) }
        }
        if (kind == ChartKind.LINE) {
            NumField("Minimo dell'asse (vuoto = automatico)", yMin) { yMin = it }
            NumField("Massimo dell'asse (vuoto = automatico)", yMax) { yMax = it }
        }
    }
}

// ---------- scheda: clima ----------

@Composable
fun ClimateEditor(initial: ClimateCard?, cfg: AppConfig, onSave: (CardConfig) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "Clima") }
    var temp by remember { mutableStateOf(initial?.temperature ?: newSeries(0, "Temperatura", "°C")) }
    var hum by remember { mutableStateOf(initial?.humidity ?: newSeries(1, "Umidità", "%")) }
    var tMin by remember { mutableStateOf<Double?>(initial?.tempBand?.min ?: 18.0) }
    var tMax by remember { mutableStateOf<Double?>(initial?.tempBand?.max ?: 26.0) }
    var hMin by remember { mutableStateOf<Double?>(initial?.humBand?.min ?: 40.0) }
    var hMax by remember { mutableStateOf<Double?>(initial?.humBand?.max ?: 60.0) }
    var tAlertOn by remember { mutableStateOf(initial?.tempAlert?.enabled ?: false) }
    var tAlertMin by remember { mutableStateOf(initial?.tempAlert?.min) }
    var tAlertMax by remember { mutableStateOf(initial?.tempAlert?.max) }
    var hAlertOn by remember { mutableStateOf(initial?.humAlert?.enabled ?: false) }
    var hAlertMin by remember { mutableStateOf(initial?.humAlert?.min) }
    var hAlertMax by remember { mutableStateOf(initial?.humAlert?.max) }
    var error by remember { mutableStateOf<String?>(null) }

    EditorFrame(
        title = if (initial == null) "Nuova scheda clima" else "Modifica scheda clima",
        onBack = onBack,
        onDelete = onDelete,
        error = error,
        onSave = {
            val bad = seriesError(temp, 1)?.replace("Serie 1", "Temperatura")
                ?: seriesError(hum, 2)?.replace("Serie 2", "Umidità")
            if (title.isBlank()) {
                error = "Scrivi un titolo"
            } else if (bad != null) {
                error = bad
            } else if (tMin == null || tMax == null || hMin == null || hMax == null || tMin!! >= tMax!! || hMin!! >= hMax!!) {
                error = "Le fasce comfort devono avere minimo e massimo, con il minimo più basso"
            } else if (tAlertOn && tAlertMin == null && tAlertMax == null) {
                error = "Avviso temperatura: indica almeno un limite"
            } else if (hAlertOn && hAlertMin == null && hAlertMax == null) {
                error = "Avviso umidità: indica almeno un limite"
            } else {
                onSave(
                    ClimateCard(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        temperature = temp,
                        humidity = hum,
                        tempBand = Band(tMin!!, tMax!!),
                        humBand = Band(hMin!!, hMax!!),
                        tempAlert = AlertRule(tAlertOn, tAlertMin, tAlertMax),
                        humAlert = AlertRule(hAlertOn, hAlertMin, hAlertMax),
                    ),
                )
            }
        },
    ) {
        Field("Titolo", title) { title = it }
        Text("Temperatura", style = MaterialTheme.typography.titleSmall)
        SeriesBox(0, temp, cfg, { temp = it }, null)
        Text("Umidità", style = MaterialTheme.typography.titleSmall)
        SeriesBox(1, hum, cfg, { hum = it }, null)
        Text("Fasce comfort (zona verde nei grafici)", style = MaterialTheme.typography.titleSmall)
        NumField("Temperatura: minimo comfort", tMin) { tMin = it }
        NumField("Temperatura: massimo comfort", tMax) { tMax = it }
        NumField("Umidità: minimo comfort", hMin) { hMin = it }
        NumField("Umidità: massimo comfort", hMax) { hMax = it }
        Text("Avvisi", style = MaterialTheme.typography.titleSmall)
        Text(
            "Il controllo avviene circa ogni 15 minuti, quando il telefono è in rete. Si riceve una notifica solo quando il valore esce dai limiti.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Avviso temperatura", tAlertOn) { tAlertOn = it }
        if (tAlertOn) {
            NumField("Avvisa sotto (°C)", tAlertMin) { tAlertMin = it }
            NumField("Avvisa sopra (°C)", tAlertMax) { tAlertMax = it }
        }
        SwitchRow("Avviso umidità", hAlertOn) { hAlertOn = it }
        if (hAlertOn) {
            NumField("Avvisa sotto (%)", hAlertMin) { hAlertMin = it }
            NumField("Avvisa sopra (%)", hAlertMax) { hAlertMax = it }
        }
    }
}

// ---------- scheda: energia ----------

@Composable
fun EnergyEditor(initial: EnergyCard?, cfg: AppConfig, onSave: (CardConfig) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "Consumi") }
    var sourceId by remember { mutableStateOf(initial?.sourceId) }
    var powerFeed by remember { mutableStateOf(initial?.powerFeedId) }
    var kwhFeed by remember { mutableStateOf(initial?.kwhFeedId) }
    var isWh by remember { mutableStateOf(initial?.kwhIsWh ?: false) }
    var cost by remember { mutableStateOf<Double?>(initial?.unitCost ?: 0.0) }
    var currency by remember { mutableStateOf(initial?.currency ?: "€") }
    var color by remember { mutableStateOf(initial?.colorArgb ?: SeriesPalette[0]) }
    var error by remember { mutableStateOf<String?>(null) }

    EditorFrame(
        title = if (initial == null) "Nuova scheda energia" else "Modifica scheda energia",
        onBack = onBack,
        onDelete = onDelete,
        error = error,
        onSave = {
            if (title.isBlank()) {
                error = "Scrivi un titolo"
            } else if (sourceId == null || powerFeed.isNullOrBlank() || kwhFeed.isNullOrBlank()) {
                error = "Scegli il server e i due feed"
            } else {
                onSave(
                    EnergyCard(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        sourceId = sourceId!!,
                        powerFeedId = powerFeed!!,
                        kwhFeedId = kwhFeed!!,
                        unitCost = cost ?: 0.0,
                        currency = currency.ifBlank { "€" },
                        kwhIsWh = isWh,
                        colorArgb = color,
                    ),
                )
            }
        },
    ) {
        Field("Titolo", title) { title = it }
        if (cfg.sources.isEmpty()) {
            Text("Aggiungi prima un server dalla schermata Server.", color = MaterialTheme.colorScheme.error)
        } else {
            Picker("Scegli il server", cfg.sources.map { it.id to it.name }, sourceId) { sourceId = it }
        }
        Text("Feed potenza (watt)", style = MaterialTheme.typography.titleSmall)
        FeedPicker(cfg, sourceId, powerFeed) { powerFeed = it }
        Text("Feed energia cumulativa (per le barre giornaliere)", style = MaterialTheme.typography.titleSmall)
        FeedPicker(cfg, sourceId, kwhFeed) { kwhFeed = it }
        SwitchRow("Il feed è in Wh (non in kWh)", isWh) { isWh = it }
        NumField("Costo per kWh", cost) { cost = it }
        Field("Simbolo della valuta", currency) { currency = it }
        Text("Colore della linea", style = MaterialTheme.typography.titleSmall)
        ColorRow(color) { color = it }
    }
}

// ---------- scheda: pagina web ----------

@Composable
fun WebEditor(initial: WebCard?, onSave: (CardConfig) -> Unit, onDelete: (() -> Unit)?, onBack: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "Fotovoltaico") }
    var url by remember { mutableStateOf(initial?.url ?: "https://") }
    var extract by remember { mutableStateOf(initial?.extract ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorFrame(
        title = if (initial == null) "Nuova scheda fotovoltaico / web" else "Modifica scheda fotovoltaico / web",
        onBack = onBack,
        onDelete = onDelete,
        error = error,
        onSave = {
            val u = url.trim()
            if (title.isBlank()) {
                error = "Scrivi un titolo"
            } else if (!u.startsWith("https://") || u.length < 12) {
                error = "Usa un indirizzo che inizia con https://"
            } else {
                onSave(WebCard(initial?.id ?: UUID.randomUUID().toString(), title.trim(), u, extract))
            }
        },
    ) {
        Field("Titolo", title) { title = it }
        Field("Indirizzo della pagina (es. il link pubblico di FusionSolar)", url) { url = it }
        SwitchRow("Leggi i valori dalla pagina e mostrali a riquadri", extract) { extract = it }
        Text(
            if (extract) "L'app apre la pagina in background, legge i testi che mostra (potenza, energia, ecc.) e li ordina per potenza ed energia. Se la pagina cambia o non viene riconosciuta, puoi sempre vedere la pagina originale e il testo letto."
            else "Mostra la pagina così com'è dentro l'app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- scelta dell'editor ----------

@Composable
fun CardEditorHost(
    state: AppState,
    s: Screen.EditCard,
    requestNotifications: () -> Unit,
    onDone: () -> Unit,
) {
    val cfg = state.config
    val existing = cfg.cards.firstOrNull { it.id == s.id }
    val save: (CardConfig) -> Unit = { card ->
        val cards = cfg.cards
        val next = if (cards.any { it.id == card.id }) cards.map { if (it.id == card.id) card else it } else cards + card
        state.update(cfg.copy(cards = next))
        state.selectedCardId = card.id
        if (card is ClimateCard && (card.tempAlert.enabled || card.humAlert.enabled)) requestNotifications()
        onDone()
    }
    val delete: (() -> Unit)? = existing?.let { e ->
        {
            state.update(cfg.copy(cards = cfg.cards.filter { it.id != e.id }))
            onDone()
        }
    }
    when (s.type) {
        "energy" -> EnergyEditor(existing as? EnergyCard, cfg, save, delete, onDone)
        "chart" -> ChartEditor(existing as? ChartCard, cfg, save, delete, onDone)
        "climate" -> ClimateEditor(existing as? ClimateCard, cfg, save, delete, onDone)
        else -> WebEditor(existing as? WebCard, save, delete, onDone)
    }
}

// ---------- server ----------

@Composable
fun ServersScreen(state: AppState, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val ctx = LocalContext.current
    val cfg = state.config
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Indietro") }
            Text("Server e impostazioni", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server Emoncms", style = MaterialTheme.typography.titleSmall)
            if (cfg.sources.isEmpty()) {
                Text("Nessun server. Puoi usare anche solo link pubblici e pagine web.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            cfg.sources.forEach { src ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(src.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(src.name, style = MaterialTheme.typography.titleSmall)
                        Text(src.baseUrl, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = { onEdit(null) }) { Text("Aggiungi un server") }
            Text("Aspetto", style = MaterialTheme.typography.titleSmall)
            Picker(
                "Sfondo",
                listOf(
                    "SYSTEM" to "Automatico (come il telefono)",
                    "LIGHT" to "Sfondo chiaro",
                    "DARK" to "Sfondo scuro",
                ),
                cfg.theme.name,
            ) { state.update(cfg.copy(theme = ThemeMode.valueOf(it))) }
            val scaleChoices = listOf(0.85, 1.0, 1.2, 1.4)
            val scaleNow = scaleChoices.minByOrNull { kotlin.math.abs(it - cfg.textScale) } ?: 1.0
            Picker(
                "Dimensione del testo",
                listOf(
                    "0.85" to "Piccolo",
                    "1.0" to "Normale",
                    "1.2" to "Grande",
                    "1.4" to "Molto grande",
                ),
                scaleNow.toString(),
            ) { state.update(cfg.copy(textScale = it.toDouble())) }
            Picker(
                "Lingua",
                listOf(
                    "SYSTEM" to "Automatica (come il telefono)",
                    "IT" to "Italiano",
                    "EN" to "English",
                ),
                cfg.language.name,
            ) { state.update(cfg.copy(language = AppLanguage.valueOf(it))) }
            SwitchRow("Schermo sempre acceso", cfg.keepScreenOn) { state.update(cfg.copy(keepScreenOn = it)) }
            Text("Quadro ${appVersion(ctx)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SourceEditor(state: AppState, id: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repo(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    val cfg = state.config
    val existing = cfg.sources.firstOrNull { it.id == id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var url by remember { mutableStateOf(existing?.baseUrl ?: "") }
    var key by remember {
        mutableStateOf(
            existing?.let { try { KeyVault.get(ctx, it.id) } catch (e: Exception) { null } } ?: "",
        )
    }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val scanOptions = remember {
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    }

    EditorFrame(
        title = if (existing == null) "Nuovo server" else "Modifica server",
        onBack = onDone,
        error = null,
        onDelete = existing?.let { e ->
            {
                try { KeyVault.remove(ctx, e.id) } catch (ex: Exception) { }
                state.update(cfg.copy(sources = cfg.sources.filter { it.id != e.id }))
                onDone()
            }
        },
        onSave = {
            val normalized = try { normalizeBaseUrl(url) } catch (e: IllegalArgumentException) { null }
            if (name.isBlank() || normalized == null || key.isBlank()) {
                ok = false
                msg = "Servono nome, indirizzo valido e chiave API"
            } else {
                val sid = existing?.id ?: UUID.randomUUID().toString()
                try {
                    KeyVault.set(ctx, sid, key.trim())
                    val src = Source(sid, name.trim(), normalized)
                    val list = if (existing == null) cfg.sources + src else cfg.sources.map { if (it.id == sid) src else it }
                    state.update(cfg.copy(sources = list))
                    onDone()
                } catch (e: Exception) {
                    ok = false
                    msg = "Impossibile salvare la chiave in modo sicuro su questo telefono"
                }
            }
        },
    ) {
        Field("Nome (es. Casa)", name) { name = it }
        Field("Indirizzo (es. emonpi.local oppure emoncms.org)", url) { url = it }
        Field("Chiave API di sola lettura", key, password = true) { key = it }
        if (url.trim().startsWith("http://") || (url.isNotBlank() && !url.contains("://") && runCatching { normalizeBaseUrl(url).startsWith("http://") }.getOrDefault(false))) {
            Text(
                "Connessione non cifrata (HTTP): la chiave viaggia in chiaro. Usala solo in rete locale.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = {
            val act = ctx.findActivity() ?: return@OutlinedButton
            GmsBarcodeScanning.getClient(act, scanOptions).startScan()
                .addOnSuccessListener { b ->
                    val acc = parseAccountQr(b.rawValue ?: "")
                    if (acc != null) {
                        url = acc.baseUrl
                        key = acc.apiKey
                        ok = true
                        msg = "QR letto"
                    } else {
                        ok = false
                        msg = "QR non riconosciuto: inserisci i dati a mano"
                    }
                }
                .addOnFailureListener {
                    ok = false
                    msg = "Scansione non riuscita"
                }
        }) { Text("Scansiona il QR della pagina Account di Emoncms") }
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            msg = null
            scope.launch {
                when (val r = loadResult { repo.feedListRaw(normalizeBaseUrl(url), key.trim()) }) {
                    is Load.Ok -> {
                        ok = true
                        msg = "Connessione riuscita: ${r.value.size} feed trovati"
                    }
                    is Load.Err -> {
                        ok = false
                        msg = r.message
                    }
                    else -> {}
                }
                busy = false
            }
        }) { Text(if (busy) "Provo…" else "Prova la connessione") }
        msg?.let { Text(it, color = if (ok) ColorOk else MaterialTheme.colorScheme.error) }
    }
}
