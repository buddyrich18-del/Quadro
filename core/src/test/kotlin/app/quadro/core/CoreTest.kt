package app.quadro.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val rome = ZoneId.of("Europe/Rome")

private fun millis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0): Long =
    ZonedDateTime.of(y, mo, d, h, mi, 0, 0, rome).toInstant().toEpochMilli()

class ParsingTest {
    @Test
    fun emoncmsDataMillisecondsAndSecondsAndNulls() {
        val pts = parseEmoncmsData("[[1700000000000, 1.5],[1700000060, 2],[1700000120000, null],[1700000180000, \"3.5\"]]")
        assertEquals(3, pts.size)
        assertEquals(1_700_000_000_000L, pts[0].t)
        assertEquals(1_700_000_060_000L, pts[1].t)
        assertEquals(3.5, pts[2].v)
    }

    @Test
    fun emoncmsErrorObjectBecomesMessage() {
        val e = assertFailsWith<DataFormatException> { parseEmoncmsData("{\"success\": false, \"message\": \"Invalid key\"}") }
        assertEquals("Invalid key", e.message)
    }

    @Test
    fun emoncmsValueForms() {
        assertEquals(12.5, parseEmoncmsValue("12.5"))
        assertEquals(12.5, parseEmoncmsValue("\"12.5\""))
        assertEquals(7.0, parseEmoncmsValue("{\"time\": 1, \"value\": 7}"))
        assertFailsWith<DataFormatException> { parseEmoncmsValue("null") }
    }

    @Test
    fun feedListWithNumericAndStringIds() {
        val feeds = parseEmoncmsFeedList("[{\"id\": 3, \"name\": \"use\", \"tag\": \"house\"},{\"id\": \"4\", \"name\": \"temp\", \"unit\": \"C\"}]")
        assertEquals(2, feeds.size)
        assertEquals("3", feeds[0].id)
        assertEquals("house", feeds[0].tag)
        assertEquals("C", feeds[1].unit)
    }

    @Test
    fun linkJsonObjectsWithPathAndIsoTime() {
        val text = "{\"data\": {\"points\": [{\"ts\": \"2025-03-01T10:00:00Z\", \"kw\": 1.2},{\"ts\": \"2025-03-01T10:05:00Z\", \"kw\": 1.4}]}}"
        val pts = parseLinkJson(text, "data.points", "ts", "kw", rome)
        assertEquals(2, pts.size)
        assertEquals(300_000L, pts[1].t - pts[0].t)
        assertEquals(1.4, pts[1].v)
    }

    @Test
    fun linkJsonPairsAtRoot() {
        val pts = parseLinkJson("[[1700000000, 5],[1700000300, 6]]", "", "", "", rome)
        assertEquals(2, pts.size)
        assertEquals(6.0, pts[1].v)
    }

    @Test
    fun linkJsonObjectsNeedKeys() {
        assertFailsWith<DataFormatException> { parseLinkJson("[{\"a\":1,\"b\":2}]", "", "", "", rome) }
    }

    @Test
    fun linkJsonBadPath() {
        assertFailsWith<DataFormatException> { parseLinkJson("{\"a\": []}", "b", "", "", rome) }
    }

    @Test
    fun csvSemicolonWithDecimalCommaAndLocalTime() {
        val csv = "Data;Temperatura\n01/03/2025 10:00;21,5\n01/03/2025 10:10;21,9\n"
        val pts = parseLinkCsv(csv, "Data", "Temperatura", rome)
        assertEquals(2, pts.size)
        assertEquals(21.5, pts[0].v)
        assertEquals(millis(2025, 3, 1, 10, 0), pts[0].t)
    }

    @Test
    fun csvDefaultColumnsAndMissingColumn() {
        val csv = "time,value\n1700000000,3\n1700000060,4\n"
        assertEquals(2, parseLinkCsv(csv, "", "", rome).size)
        assertFailsWith<DataFormatException> { parseLinkCsv(csv, "time", "nope", rome) }
    }

    @Test
    fun csvQuotedFieldWithDelimiterInside() {
        assertEquals(listOf("a,b", "c"), splitCsvLine("\"a,b\",c", ','))
    }

    @Test
    fun numbers() {
        assertEquals(1234.5, parseNumber("1,234.5"))
        assertEquals(12.5, parseNumber("12,5"))
        assertNull(parseNumber("abc"))
    }
}

class StatsTest {
    @Test
    fun dailyKwhRespectsLocalDaysAndDstLength() {
        // 26 ottobre 2025: in Italia il giorno dura 25 ore (fine dell'ora legale).
        val start = millis(2025, 10, 26, 0, 0)
        val hours = (0 until 25).map { Point(start + it * 3_600_000L, 1.0) }
        val daily = dailyKwh(hours, rome)
        assertEquals(1, daily.size)
        assertEquals(25.0, daily[0].v)
        assertEquals(start, daily[0].t)
    }

    @Test
    fun dailyKwhIgnoresNegativeDeltas() {
        val start = millis(2025, 3, 1, 0, 0)
        val pts = listOf(Point(start, 2.0), Point(start + 3_600_000L, -50.0), Point(start + 7_200_000L, 1.0))
        assertEquals(3.0, dailyKwh(pts, rome)[0].v)
    }

    @Test
    fun todayKwhCountsOnlyToday() {
        val now = millis(2025, 3, 2, 15, 0)
        val pts = listOf(Point(millis(2025, 3, 1, 23, 0), 5.0), Point(millis(2025, 3, 2, 1, 0), 1.0), Point(millis(2025, 3, 2, 14, 0), 2.0))
        assertEquals(3.0, todayKwh(pts, now, rome))
    }

    @Test
    fun bucketizeAverageByDay() {
        val d = millis(2025, 3, 1, 0, 0)
        val pts = listOf(Point(d + 1000, 10.0), Point(d + 2000, 20.0), Point(d + 86_400_000L + 5000, 40.0))
        val out = bucketize(pts, Bucket.DAY, Aggregate.AVG, rome)
        assertEquals(listOf(15.0, 40.0), out.map { it.v })
    }

    @Test
    fun downsampleKeepsRoughSize() {
        val pts = (0 until 2000).map { Point(it * 1000L, it.toDouble()) }
        val out = downsample(pts, 600)
        assertTrue(out.size <= 600)
        assertTrue(out.size > 300)
    }

    @Test
    fun statsOfBasic() {
        val s = assertNotNull(statsOf(listOf(Point(1, 3.0), Point(2, 9.0), Point(3, 6.0))))
        assertEquals(3.0, s.min)
        assertEquals(9.0, s.max)
        assertEquals(6.0, s.avg)
        assertEquals(6.0, s.last)
        assertNull(statsOf(emptyList()))
    }

    @Test
    fun classifyBand() {
        val band = Band(40.0, 60.0)
        assertEquals(Comfort.LOW, classify(39.9, band))
        assertEquals(Comfort.OK, classify(40.0, band))
        assertEquals(Comfort.HIGH, classify(60.1, band))
    }

    @Test
    fun trendUpDownFlat() {
        val base = 10_000_000L
        val min = 60_000L
        val rising = (0 until 90).map { Point(base + it * min, if (it < 60) 20.0 else 22.0) }
        assertEquals(Trend.UP, trendOf(rising, 0.5))
        val falling = (0 until 90).map { Point(base + it * min, if (it < 60) 22.0 else 20.0) }
        assertEquals(Trend.DOWN, trendOf(falling, 0.5))
        val flat = (0 until 90).map { Point(base + it * min, 21.0) }
        assertEquals(Trend.FLAT, trendOf(flat, 0.5))
        assertEquals(Trend.FLAT, trendOf(listOf(Point(1, 1.0)), 0.5))
    }
}

class AlertsTest {
    private val rule = AlertRule(enabled = true, min = 40.0, max = 60.0)

    @Test
    fun disabledNeverNotifies() {
        assertEquals(AlertDecision(AlertState.OK, false), evaluateAlert(99.0, AlertRule(), AlertState.OK, 2.0))
    }

    @Test
    fun notifiesOnlyOnEntering() {
        val first = evaluateAlert(65.0, rule, AlertState.OK, 2.0)
        assertEquals(AlertDecision(AlertState.HIGH, true), first)
        val second = evaluateAlert(66.0, rule, first.state, 2.0)
        assertEquals(AlertDecision(AlertState.HIGH, false), second)
    }

    @Test
    fun hysteresisKeepsStateNearLimit() {
        assertEquals(AlertState.HIGH, evaluateAlert(59.0, rule, AlertState.HIGH, 2.0).state)
        assertEquals(AlertState.OK, evaluateAlert(57.5, rule, AlertState.HIGH, 2.0).state)
        assertEquals(AlertState.LOW, evaluateAlert(41.0, rule, AlertState.LOW, 2.0).state)
        assertEquals(AlertState.OK, evaluateAlert(43.0, rule, AlertState.LOW, 2.0).state)
    }

    @Test
    fun switchingFromLowToHighNotifies() {
        assertEquals(AlertDecision(AlertState.HIGH, true), evaluateAlert(70.0, rule, AlertState.LOW, 2.0))
    }

    @Test
    fun onlyMaxRule() {
        val r = AlertRule(enabled = true, max = 30.0)
        assertEquals(AlertState.OK, evaluateAlert(-10.0, r, AlertState.OK, 1.0).state)
        assertEquals(AlertState.HIGH, evaluateAlert(31.0, r, AlertState.OK, 1.0).state)
    }
}

class EmoncmsTest {
    @Test
    fun baseUrlNormalization() {
        assertEquals("https://emoncms.org", normalizeBaseUrl(" emoncms.org/ "))
        assertEquals("http://emonpi.local", normalizeBaseUrl("emonpi.local"))
        assertEquals("http://192.168.1.20/emoncms", normalizeBaseUrl("192.168.1.20/emoncms"))
        assertEquals("https://host.example/emoncms", normalizeBaseUrl("https://host.example/emoncms/"))
        assertFailsWith<IllegalArgumentException> { normalizeBaseUrl("  ") }
        assertFailsWith<IllegalArgumentException> { normalizeBaseUrl("ftp://x.y") }
    }

    @Test
    fun feedDataUrlUsesMillisecondsAndFlags() {
        val url = EmoncmsUrls.feedData("emoncms.org", "12", 1000L, 2000L, 60, DataMode.DELTA)
        assertEquals(
            "https://emoncms.org/feed/data.json?id=12&start=1000&end=2000&interval=60&delta=1&skipmissing=1&timeformat=unixms",
            url,
        )
        assertTrue(EmoncmsUrls.feedData("emoncms.org", "12", 1, 2, 60, DataMode.AVERAGE).contains("average=1"))
    }

    @Test
    fun suggestedIntervals() {
        assertEquals(30, suggestIntervalSeconds(Period.H3.millis))
        assertEquals(60, suggestIntervalSeconds(Period.H6.millis))
        assertEquals(300, suggestIntervalSeconds(Period.D1.millis))
        assertEquals(1800, suggestIntervalSeconds(Period.W1.millis))
        assertEquals(7200, suggestIntervalSeconds(Period.M1.millis))
    }

    @Test
    fun qrFormats() {
        val key = "0123456789abcdef0123456789abcdef"
        assertEquals(QrAccount("https://emoncms.org", key), parseAccountQr("https://emoncms.org?apikey=$key"))
        assertEquals(QrAccount("http://emonpi.local", key), parseAccountQr("emonpi.local|$key"))
        assertEquals(QrAccount("https://a.example/emoncms", key), parseAccountQr("{\"url\": \"a.example/emoncms\", \"apikey\": \"$key\"}"))
        assertEquals(QrAccount("https://a.example", key), parseAccountQr("https://a.example\n$key"))
        assertNull(parseAccountQr("ciao"))
        assertNull(parseAccountQr(""))
    }
}

class KioskTest {
    private fun frag(text: String, top: Int, left: Int, height: Int, width: Int) =
        "[\"$text\",$top,$left,$height,$width]"

    private fun json(vararg fragments: String) = fragments.joinToString(",", "[", "]")

    @Test
    fun twoColumnsLabelsAboveValues() {
        val r = parseKioskFragments(
            json(
                frag("Today", 100, 10, 14, 60), frag("This month", 100, 200, 14, 90),
                frag("12.3", 120, 10, 30, 40), frag("kWh", 132, 52, 14, 25),
                frag("340", 120, 200, 30, 40), frag("kWh", 132, 242, 14, 25),
            ),
        )
        assertEquals(listOf(KioskItem("Today", "12.3", "kWh"), KioskItem("This month", "340", "kWh")), r.items)
    }

    @Test
    fun labelAndValueOnTheSameRow() {
        val r = parseKioskFragments(
            json(frag("Potenza attuale", 50, 10, 16, 120), frag("3.2", 50, 140, 16, 30), frag("kW", 50, 172, 16, 20)),
        )
        assertEquals(listOf(KioskItem("Potenza attuale", "3.2", "kW")), r.items)
    }

    @Test
    fun valueAboveItsLabel() {
        val r = parseKioskFragments(
            json(frag("5.1", 20, 10, 30, 30), frag("kW", 32, 42, 14, 20), frag("Potenza", 55, 10, 14, 50)),
        )
        assertEquals(listOf(KioskItem("Potenza", "5.1", "kW")), r.items)
    }

    @Test
    fun unitTakenFromParenthesesInLabel() {
        val r = parseKioskFragments(json(frag("Daily yield (kWh)", 10, 10, 14, 120), frag("12.3", 30, 10, 30, 40)))
        assertEquals(listOf(KioskItem("Daily yield", "12.3", "kWh")), r.items)
    }

    @Test
    fun datesAndUnlabeledNumbersAreIgnored() {
        val r = parseKioskFragments(json(frag("2025-10-30 14:00", 10, 10, 14, 120), frag("42", 200, 10, 30, 30)))
        assertTrue(r.items.isEmpty())
        assertEquals(2, r.lines.size)
    }

    @Test
    fun invalidInputGivesEmptyResult() {
        assertTrue(parseKioskFragments("non è json").items.isEmpty())
        assertTrue(parseKioskFragments("{}").items.isEmpty())
    }

    @Test
    fun unitCategories() {
        assertEquals(UnitCategory.POWER, unitCategory("kW"))
        assertEquals(UnitCategory.ENERGY, unitCategory("MWh"))
        assertEquals(UnitCategory.OTHER, unitCategory("%"))
    }
}

class ConfigTest {
    @Test
    fun roundTripWithAllCardTypes() {
        val series = SeriesRef(label = "Temp", unit = "°C", sourceId = "s1", feedId = "5")
        val config = AppConfig(
            sources = listOf(Source("s1", "Casa", "http://emonpi.local")),
            cards = listOf(
                EnergyCard("c1", "Consumi", "s1", "1", "2", 0.25, "€"),
                ChartCard("c2", "Linea", ChartKind.LINE, listOf(series)),
                ClimateCard("c3", "Clima", series, series.copy(label = "Umidità", unit = "%"), tempAlert = AlertRule(true, 15.0, 28.0)),
                WebCard("c4", "Solare", "https://example.org/kiosk"),
            ),
            keepScreenOn = true,
        )
        val text = ConfigCodec.encode(config)
        assertEquals(config, ConfigCodec.decode(text))
    }

    @Test
    fun brokenTextGivesEmptyConfig() {
        assertEquals(AppConfig(), ConfigCodec.decode("non è json"))
    }
}
