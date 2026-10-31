package app.quadro.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un punto di una serie: t in millisecondi dall'epoca, v valore. */
data class Point(val t: Long, val v: Double)

@Serializable
data class Source(val id: String, val name: String, val baseUrl: String)

@Serializable
enum class LinkFormat { JSON, CSV }

@Serializable
enum class ChartKind { LINE, BAR }

@Serializable
enum class Bucket { NONE, HOUR, DAY }

@Serializable
enum class Aggregate { AVG, SUM, MIN, MAX, LAST }

@Serializable
enum class Period(val label: String, val millis: Long) {
    H3("3H", 3L * 3_600_000L),
    H6("6H", 6L * 3_600_000L),
    D1("D", 24L * 3_600_000L),
    W1("W", 7L * 24L * 3_600_000L),
    M1("M", 30L * 24L * 3_600_000L),
}

/** Dove prendere una serie: un feed di un server Emoncms oppure un link pubblico JSON/CSV. */
@Serializable
data class SeriesRef(
    val label: String,
    val unit: String = "",
    val colorArgb: Long = 0xFF7B68A6L,
    val sourceId: String? = null,
    val feedId: String? = null,
    val linkUrl: String? = null,
    val linkFormat: LinkFormat = LinkFormat.JSON,
    val arrayPath: String = "",
    val timeKey: String = "",
    val valueKey: String = "",
) {
    val isEmoncms: Boolean get() = sourceId != null && feedId != null
    val isLink: Boolean get() = !linkUrl.isNullOrBlank()
}

@Serializable
data class Band(val min: Double, val max: Double)

@Serializable
data class AlertRule(
    val enabled: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
)

@Serializable
sealed interface CardConfig {
    val id: String
    val title: String
}

@Serializable
@SerialName("chart")
data class ChartCard(
    override val id: String,
    override val title: String,
    val kind: ChartKind = ChartKind.LINE,
    val series: List<SeriesRef>,
    val defaultPeriod: Period = Period.D1,
    val bucket: Bucket = Bucket.NONE,
    val aggregate: Aggregate = Aggregate.AVG,
    val yMin: Double? = null,
    val yMax: Double? = null,
) : CardConfig

@Serializable
@SerialName("energy")
data class EnergyCard(
    override val id: String,
    override val title: String,
    val sourceId: String,
    val powerFeedId: String,
    val kwhFeedId: String,
    val unitCost: Double = 0.0,
    val currency: String = "€",
    val kwhIsWh: Boolean = false,
    val colorArgb: Long = 0xFF7B68A6L,
) : CardConfig

@Serializable
@SerialName("climate")
data class ClimateCard(
    override val id: String,
    override val title: String,
    val temperature: SeriesRef,
    val humidity: SeriesRef,
    val tempBand: Band = Band(18.0, 26.0),
    val humBand: Band = Band(40.0, 60.0),
    val tempAlert: AlertRule = AlertRule(),
    val humAlert: AlertRule = AlertRule(),
) : CardConfig

@Serializable
@SerialName("web")
data class WebCard(
    override val id: String,
    override val title: String,
    val url: String,
    /** Se vero, l'app legge i valori mostrati dalla pagina e li impagina a schede. */
    val extract: Boolean = true,
) : CardConfig

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class ScreensaverMode { CLOCK, CARDS }

@Serializable
enum class AppLanguage { SYSTEM, IT, EN }

@Serializable
data class AppConfig(
    val sources: List<Source> = emptyList(),
    val cards: List<CardConfig> = emptyList(),
    val keepScreenOn: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Moltiplicatore della dimensione del testo (1.0 = normale). */
    val textScale: Double = 1.0,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Minuti senza tocchi prima del salvaschermo (0 = disattivato). */
    val screensaverMinutes: Int = 0,
    val screensaverMode: ScreensaverMode = ScreensaverMode.CLOCK,
    /** Luminosità dello schermo durante il salvaschermo, da 1 a 100 per cento. */
    val screensaverBrightness: Int = 10,
)

object ConfigCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(config: AppConfig): String = json.encodeToString(AppConfig.serializer(), config)

    fun decode(text: String): AppConfig =
        try {
            json.decodeFromString(AppConfig.serializer(), text)
        } catch (e: Exception) {
            AppConfig()
        }
}
