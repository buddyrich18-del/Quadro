package app.quadro.monitor

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.quadro.core.AppConfig
import app.quadro.core.ClimateCard
import app.quadro.core.ConfigCodec
import app.quadro.core.DataFormatException
import app.quadro.core.DataMode
import app.quadro.core.EmoncmsUrls
import app.quadro.core.EnergyCard
import app.quadro.core.FeedInfo
import app.quadro.core.LinkFormat
import app.quadro.core.Period
import app.quadro.core.Point
import app.quadro.core.SeriesRef
import app.quadro.core.Source
import app.quadro.core.dailyKwh
import app.quadro.core.parseEmoncmsData
import app.quadro.core.parseEmoncmsFeedList
import app.quadro.core.parseEmoncmsValue
import app.quadro.core.parseLinkCsv
import app.quadro.core.parseLinkJson
import app.quadro.core.startOfDayMillis
import app.quadro.core.suggestIntervalSeconds
import app.quadro.core.todayKwh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZoneId

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Err(val message: String) : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
}

fun errorText(e: Throwable): String = when (e) {
    is DataFormatException -> e.message ?: "Dati non validi"
    is UnknownHostException -> "Server non raggiungibile"
    is SocketTimeoutException -> "Tempo scaduto"
    is IOException -> e.message ?: "Errore di rete"
    else -> e.message ?: "Errore"
}

suspend fun <T> loadResult(block: suspend () -> T): Load<T> =
    try {
        Load.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Err(errorText(e))
    }

object Net {
    private const val MAX_BYTES = 8 * 1024 * 1024

    /** GET con la chiave nell'header Authorization, mai nell'indirizzo. */
    fun get(url: String, apiKey: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json, text/csv, */*")
            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            val code = conn.responseCode
            if (code == 401 || code == 403) throw IOException("Accesso negato: controlla la chiave API")
            if (code !in 200..299) throw IOException("Errore del server ($code)")
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) throw IOException("Risposta troppo grande")
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}

/** Chiavi API in archivio cifrato; non finiscono mai nel JSON di configurazione. */
object KeyVault {
    @Volatile
    private var cached: android.content.SharedPreferences? = null

    private fun create(ctx: Context): android.content.SharedPreferences {
        val app = ctx.applicationContext
        val master = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            app,
            "quadro_keys",
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun prefs(ctx: Context): android.content.SharedPreferences =
        cached ?: synchronized(this) {
            cached ?: create(ctx).also { cached = it }
        }

    fun get(ctx: Context, sourceId: String): String? = prefs(ctx).getString(sourceId, null)

    fun set(ctx: Context, sourceId: String, key: String) {
        prefs(ctx).edit().putString(sourceId, key).apply()
    }

    fun remove(ctx: Context, sourceId: String) {
        prefs(ctx).edit().remove(sourceId).apply()
    }
}

object ConfigStore {
    private const val FILE = "quadro_config"
    private const val KEY = "config"

    fun load(ctx: Context): AppConfig {
        val text = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return AppConfig()
        return ConfigCodec.decode(text)
    }

    fun save(ctx: Context, config: AppConfig) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY, ConfigCodec.encode(config)).apply()
    }
}

data class EnergyData(
    val powerNowW: Double,
    val todayKwh: Double,
    val power: List<Point>,
    val daily: List<Point>,
)

data class ClimateData(
    val temp: List<Point>,
    val hum: List<Point>,
    val tempNow: Double,
    val humNow: Double,
)

class Repo(private val ctx: Context) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun source(cfg: AppConfig, id: String?): Source =
        cfg.sources.firstOrNull { it.id == id }
            ?: throw DataFormatException("Server non trovato: controlla la scheda")

    private fun fetch(src: Source, url: String): String = Net.get(url, KeyVault.get(ctx, src.id))

    private fun linkPoints(ref: SeriesRef): List<Point> {
        val text = Net.get(ref.linkUrl!!, null)
        return when (ref.linkFormat) {
            LinkFormat.JSON -> parseLinkJson(text, ref.arrayPath, ref.timeKey, ref.valueKey, zone)
            LinkFormat.CSV -> parseLinkCsv(text, ref.timeKey, ref.valueKey, zone)
        }
    }

    suspend fun series(cfg: AppConfig, ref: SeriesRef, period: Period, nowMs: Long): List<Point> =
        withContext(Dispatchers.IO) {
            if (ref.isEmoncms) {
                val src = source(cfg, ref.sourceId)
                val interval = suggestIntervalSeconds(period.millis)
                val url = EmoncmsUrls.feedData(
                    src.baseUrl, ref.feedId!!, nowMs - period.millis, nowMs, interval, DataMode.AVERAGE,
                )
                parseEmoncmsData(fetch(src, url))
            } else if (ref.isLink) {
                val from = nowMs - period.millis
                val pts = linkPoints(ref).filter { it.t >= from }
                if (pts.isEmpty()) throw DataFormatException("Nessun dato nel periodo scelto")
                pts
            } else {
                throw DataFormatException("Serie non configurata")
            }
        }

    /** Ultimo valore. Per i link si usa l'ultimo punto, scaricando i dati solo se non già noti. */
    suspend fun latest(cfg: AppConfig, ref: SeriesRef, known: List<Point>? = null): Double =
        withContext(Dispatchers.IO) {
            if (ref.isEmoncms) {
                val src = source(cfg, ref.sourceId)
                parseEmoncmsValue(fetch(src, EmoncmsUrls.feedValue(src.baseUrl, ref.feedId!!)))
            } else if (ref.isLink) {
                (known ?: linkPoints(ref)).last().v
            } else {
                throw DataFormatException("Serie non configurata")
            }
        }

    suspend fun energy(cfg: AppConfig, card: EnergyCard, period: Period, nowMs: Long): EnergyData =
        withContext(Dispatchers.IO) {
            val src = source(cfg, card.sourceId)
            val powerNow = parseEmoncmsValue(fetch(src, EmoncmsUrls.feedValue(src.baseUrl, card.powerFeedId)))
            val power = parseEmoncmsData(
                fetch(
                    src,
                    EmoncmsUrls.feedData(
                        src.baseUrl, card.powerFeedId, nowMs - period.millis, nowMs,
                        suggestIntervalSeconds(period.millis), DataMode.AVERAGE,
                    ),
                ),
            )
            val start = startOfDayMillis(nowMs, zone, 13)
            val raw = parseEmoncmsData(
                fetch(
                    src,
                    EmoncmsUrls.feedData(src.baseUrl, card.kwhFeedId, start, nowMs, 3600, DataMode.DELTA),
                ),
            )
            val scale = if (card.kwhIsWh) 0.001 else 1.0
            val hourly = raw.map { Point(it.t, it.v * scale) }
            EnergyData(powerNow, todayKwh(hourly, nowMs, zone), power, dailyKwh(hourly, zone))
        }

    suspend fun climate(cfg: AppConfig, card: ClimateCard, period: Period, nowMs: Long): ClimateData =
        coroutineScope {
            val t = async { series(cfg, card.temperature, period, nowMs) }
            val h = async { series(cfg, card.humidity, period, nowMs) }
            val temp = t.await()
            val hum = h.await()
            val tNow = latest(cfg, card.temperature, temp)
            val hNow = latest(cfg, card.humidity, hum)
            ClimateData(temp, hum, tNow, hNow)
        }

    suspend fun feedList(cfg: AppConfig, sourceId: String?): List<FeedInfo> =
        withContext(Dispatchers.IO) {
            val src = source(cfg, sourceId)
            parseEmoncmsFeedList(fetch(src, EmoncmsUrls.feedList(src.baseUrl)))
        }

    suspend fun feedListRaw(baseUrl: String, key: String?): List<FeedInfo> =
        withContext(Dispatchers.IO) {
            parseEmoncmsFeedList(Net.get(EmoncmsUrls.feedList(baseUrl), key))
        }
}
