package app.quadro.monitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import app.quadro.core.AppConfig
import app.quadro.core.Band
import app.quadro.core.Bucket
import app.quadro.core.CardConfig
import app.quadro.core.ChartCard
import app.quadro.core.ChartKind
import app.quadro.core.ClimateCard
import app.quadro.core.Comfort
import app.quadro.core.EnergyCard
import app.quadro.core.Period
import app.quadro.core.Point
import app.quadro.core.ThemeMode
import app.quadro.core.Trend
import app.quadro.core.WebCard
import app.quadro.core.bucketize
import app.quadro.core.classify
import app.quadro.core.downsample
import app.quadro.core.statsOf
import app.quadro.core.trendOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

val ColorOk = Color(0xFF2E9E5B)
val ColorLow = Color(0xFF3B82F6)
val ColorHigh = Color(0xFFE5732B)
val SeriesPalette = listOf(0xFF2E7DFFL, 0xFFE5732BL, 0xFF2E9E5BL, 0xFF9B59B6L, 0xFFD6336CL)

@Composable
fun QuadroTheme(mode: ThemeMode, textScale: Double, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7DB4FF),
            background = Color(0xFF121417),
            surface = Color(0xFF121417),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1F6FEB),
            background = Color(0xFFF7F8FA),
            surface = Color(0xFFF7F8FA),
        )
    }
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            window.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * textScale.toFloat()),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

class AppState(private val ctx: Context) {
    var config by mutableStateOf(ConfigStore.load(ctx))
        private set

    /** Scheda mostrata nella schermata principale (non viene salvata). */
    var selectedCardId by mutableStateOf<String?>(null)

    fun update(next: AppConfig) {
        config = next
        ConfigStore.save(ctx, next)
        AlertScheduler.sync(ctx, next)
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Servers : Screen
    data class EditSource(val id: String?) : Screen
    data class EditCard(val type: String, val id: String?) : Screen
}

@Composable
fun KeepScreenOn(on: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val window = ctx.findActivity()?.window
        if (on) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
fun QuadroRoot() {
    val ctx = LocalContext.current
    val state = remember { AppState(ctx.applicationContext) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    QuadroTheme(state.config.theme, state.config.textScale) {
    BackHandler(enabled = screen !is Screen.Home) {
        screen = if (screen is Screen.EditSource) Screen.Servers else Screen.Home
    }
    KeepScreenOn(state.config.keepScreenOn)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = screen) {
            Screen.Home -> HomeScreen(
                state,
                onServers = { screen = Screen.Servers },
                onNewCard = { screen = Screen.EditCard(it, null) },
                onEditCard = { c ->
                    val type = when (c) {
                        is EnergyCard -> "energy"
                        is ChartCard -> "chart"
                        is ClimateCard -> "climate"
                        is WebCard -> "web"
                    }
                    screen = Screen.EditCard(type, c.id)
                },
            )
            Screen.Servers -> ServersScreen(
                state,
                onBack = { screen = Screen.Home },
                onEdit = { screen = Screen.EditSource(it) },
            )
            is Screen.EditSource -> SourceEditor(state, s.id, onDone = { screen = Screen.Servers })
            is Screen.EditCard -> CardEditorHost(
                state,
                s,
                requestNotifications = requestNotifications,
                onDone = { screen = Screen.Home },
            )
        }
    }
    }
}

@Composable
fun HomeScreen(
    state: AppState,
    onServers: () -> Unit,
    onNewCard: (String) -> Unit,
    onEditCard: (CardConfig) -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val cfg = state.config
    Column(Modifier.fillMaxSize()) {
        Text(
            "Quadro",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { tick++ }) { Text("Aggiorna") }
            TextButton(onClick = onServers) { Text("Server") }
            androidx.compose.foundation.layout.Box {
                Button(onClick = { menu = true }) { Text("+ Scheda") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        "energy" to "Energia (potenza e kWh)",
                        "chart" to "Grafico a linee o barre",
                        "climate" to "Temperatura e umidità",
                        "web" to "Fotovoltaico / pagina web (es. FusionSolar)",
                    ).forEach { (type, text) ->
                        DropdownMenuItem(text = { Text(text) }, onClick = {
                            menu = false
                            onNewCard(type)
                        })
                    }
                }
            }
        }
        if (cfg.cards.isEmpty()) {
            EmptyHome(hasServers = cfg.sources.isNotEmpty(), onServers = onServers)
        } else {
            val current = cfg.cards.firstOrNull { it.id == state.selectedCardId } ?: cfg.cards.first()
            val index = cfg.cards.indexOfFirst { it.id == current.id }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cfg.cards.forEach { c ->
                    Button(
                        onClick = { state.selectedCardId = c.id },
                        colors = if (c.id == current.id) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    ) { Text(c.title, maxLines = 1) }
                }
            }
            key(current.id) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    val move: (Int) -> Unit = { delta ->
                        val list = cfg.cards.toMutableList()
                        val j = index + delta
                        if (j in list.indices) {
                            val item = list.removeAt(index)
                            list.add(j, item)
                            state.update(cfg.copy(cards = list))
                        }
                    }
                    CardFrame(
                        title = current.title,
                        onEdit = { onEditCard(current) },
                        onPrev = if (index > 0) ({ move(-1) }) else null,
                        onNext = if (index < cfg.cards.size - 1) ({ move(1) }) else null,
                    ) {
                        when (current) {
                            is EnergyCard -> EnergyView(current, cfg, tick)
                            is ChartCard -> ChartView(current, cfg, tick)
                            is ClimateCard -> ClimateView(current, cfg, tick)
                            is WebCard -> WebCardView(current)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHome(hasServers: Boolean, onServers: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nessuna scheda", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hasServers) "Tocca \"+ Scheda\" per aggiungere un grafico, i consumi o il clima."
            else "Per cominciare aggiungi un server Emoncms, oppure crea una scheda con un link pubblico o una pagina web.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasServers) OutlinedButton(onClick = onServers) { Text("Aggiungi un server") }
    }
}

@Composable
fun CardFrame(
    title: String,
    onEdit: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onPrev != null) TextButton(onClick = onPrev, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("←") }
                if (onNext != null) TextButton(onClick = onNext, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("→") }
                TextButton(onClick = onEdit) { Text("Modifica") }
            }
            content()
        }
    }
}

@Composable
fun PeriodRow(selected: Period, onSelect: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Period.entries.forEach { p ->
            Button(
                onClick = { onSelect(p) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
                colors = if (p == selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
            ) { Text(p.label, fontSize = 12.sp) }
        }
    }
}

@Composable
fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Carico i dati…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
fun EnergyView(card: EnergyCard, cfg: AppConfig, tick: Int) {
    val ctx = LocalContext.current
    val repo = remember { Repo(ctx.applicationContext) }
    var period by remember(card.id) { mutableStateOf(Period.D1) }
    var showCost by remember(card.id) { mutableStateOf(false) }
    val load by produceState<Load<EnergyData>>(Load.Loading, card, cfg.sources, period, tick) {
        value = loadResult { repo.energy(cfg, card, period, System.currentTimeMillis()) }
    }
    when (val l = load) {
        is Load.Loading -> LoadingRow()
        is Load.Err -> ErrorText(l.message)
        is Load.Ok -> {
            val d = l.value
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showCost, onCheckedChange = { showCost = it })
                Text("  kWh / costo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Adesso", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${fmtNum(d.powerNowW, 0)} W", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Oggi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val today = if (showCost) "${fmtNum(d.todayKwh * card.unitCost, 2)} ${card.currency}" else "${fmtNum(d.todayKwh, 1)} kWh"
                    Text(today, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            PeriodRow(period) { period = it }
            LineChart(
                series = listOf(ChartSeries("Potenza", "W", MaterialTheme.colorScheme.primary, downsample(d.power, 600))),
                yMin = 0.0,
            )
            val days = d.daily.takeLast(14)
            BarChart(
                values = days.map { if (showCost) it.v * card.unitCost else it.v },
                labels = days.map {
                    Instant.ofEpochMilli(it.t).atZone(ZoneId.systemDefault()).dayOfWeek
                        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                },
                color = MaterialTheme.colorScheme.primary,
                format = { fmtNum(it, if (showCost) 2 else 1) },
            )
        }
    }
}

private fun barLabel(t: Long, bucket: Bucket): String = when (bucket) {
    Bucket.DAY -> fmtTime(t, "dd")
    Bucket.HOUR -> fmtTime(t, "HH")
    Bucket.NONE -> fmtTime(t, "HH:mm")
}

@Composable
fun ChartView(card: ChartCard, cfg: AppConfig, tick: Int) {
    val ctx = LocalContext.current
    val repo = remember { Repo(ctx.applicationContext) }
    var period by remember(card.id) { mutableStateOf(card.defaultPeriod) }
    val load by produceState<Load<List<List<Point>>>>(Load.Loading, card, cfg.sources, period, tick) {
        value = loadResult {
            coroutineScope {
                card.series.map { s -> async { repo.series(cfg, s, period, System.currentTimeMillis()) } }.awaitAll()
            }
        }
    }
    PeriodRow(period) { period = it }
    when (val l = load) {
        is Load.Loading -> LoadingRow()
        is Load.Err -> ErrorText(l.message)
        is Load.Ok -> {
            val zone = ZoneId.systemDefault()
            val processed = l.value.map { pts ->
                if (card.bucket != Bucket.NONE) bucketize(pts, card.bucket, card.aggregate, zone) else pts
            }
            if (card.kind == ChartKind.LINE) {
                LineChart(
                    series = card.series.mapIndexed { i, s ->
                        ChartSeries(s.label, s.unit, Color(s.colorArgb), downsample(processed[i], 600))
                    },
                    yMin = card.yMin,
                    yMax = card.yMax,
                )
            } else {
                val first = processed.first()
                val pts = if (card.bucket == Bucket.NONE) downsample(first, 24)
                else first.takeLast(if (card.bucket == Bucket.HOUR) 24 else 31)
                BarChart(
                    values = pts.map { it.v },
                    labels = pts.map { barLabel(it.t, card.bucket) },
                    color = Color(card.series.first().colorArgb),
                    format = { fmtNum(it, 1) },
                )
            }
            card.series.forEachIndexed { i, s ->
                val st = statsOf(processed[i]) ?: return@forEachIndexed
                val u = if (s.unit.isBlank()) "" else " ${s.unit}"
                Text(
                    "${s.label}: min ${fmtNum(st.min)} · media ${fmtNum(st.avg)} · max ${fmtNum(st.max)}$u",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun statusColor(c: Comfort): Color = when (c) {
    Comfort.LOW -> ColorLow
    Comfort.OK -> ColorOk
    Comfort.HIGH -> ColorHigh
}

private fun trendArrow(t: Trend): String = when (t) {
    Trend.UP -> "↑"
    Trend.DOWN -> "↓"
    Trend.FLAT -> "→"
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    status: String,
    color: Color,
    trend: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                Text(trend, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status, fontSize = 13.sp, color = color)
        }
    }
}

@Composable
fun ClimateView(card: ClimateCard, cfg: AppConfig, tick: Int) {
    val ctx = LocalContext.current
    val repo = remember { Repo(ctx.applicationContext) }
    var period by remember(card.id) { mutableStateOf(Period.D1) }
    val load by produceState<Load<ClimateData>>(Load.Loading, card, cfg.sources, period, tick) {
        value = loadResult { repo.climate(cfg, card, period, System.currentTimeMillis()) }
    }
    PeriodRow(period) { period = it }
    when (val l = load) {
        is Load.Loading -> LoadingRow()
        is Load.Err -> ErrorText(l.message)
        is Load.Ok -> {
            val d = l.value
            val tu = card.temperature.unit.ifBlank { "°C" }
            val hu = card.humidity.unit.ifBlank { "%" }
            val tc = classify(d.tempNow, card.tempBand)
            val hc = classify(d.humNow, card.humBand)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricTile(
                    "Temperatura", "${fmtNum(d.tempNow)} $tu",
                    when (tc) { Comfort.LOW -> "Freddo"; Comfort.OK -> "Comfort"; Comfort.HIGH -> "Caldo" },
                    statusColor(tc), trendArrow(trendOf(d.temp, 0.3)), Modifier.weight(1f),
                )
                MetricTile(
                    "Umidità", "${fmtNum(d.humNow, 0)} $hu",
                    when (hc) { Comfort.LOW -> "Aria secca"; Comfort.OK -> "Ideale"; Comfort.HIGH -> "Umido" },
                    statusColor(hc), trendArrow(trendOf(d.hum, 2.0)), Modifier.weight(1f),
                )
            }
            if (card.tempAlert.enabled || card.humAlert.enabled) {
                Text(
                    "Avvisi attivi (controllo ogni 15 minuti circa)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Temperatura", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LineChart(
                series = listOf(ChartSeries(card.temperature.label, tu, Color(card.temperature.colorArgb), downsample(d.temp, 600))),
                height = 130.dp,
                bands = listOf(bandOf(card.tempBand)),
            )
            Text("Umidità", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LineChart(
                series = listOf(ChartSeries(card.humidity.label, hu, Color(card.humidity.colorArgb), downsample(d.hum, 600))),
                height = 130.dp,
                bands = listOf(bandOf(card.humBand)),
            )
        }
    }
}

private fun bandOf(b: Band) = ChartBand(b.min, b.max, ColorOk.copy(alpha = 0.14f))

@Composable
fun WebCardView(card: WebCard) {
    if (card.extract) {
        KioskView(card)
        return
    }
    val ctx = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(460.dp),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(card.url)
            }
        },
    )
    OutlinedButton(onClick = {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text("Apri nel browser") }
}
