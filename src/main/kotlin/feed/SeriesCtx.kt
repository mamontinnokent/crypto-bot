package feed

import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

data class SeriesCtx(
    val series1m: BarSeries,
    val series5m: BarSeries,
    val series1h: BarSeries
)

object SeriesUtil {

    fun buildSeriesFrom1m(symbol: String, candles: List<Candle>): BarSeries {
        val s = BaseBarSeriesBuilder().withName("${symbol}-1m").build()
        for (c in candles) {
            val endTime = Instant.ofEpochMilli(c.openTime + 60_000)
            val bar = s.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(endTime)
                .openPrice(c.open)
                .highPrice(c.high)
                .lowPrice(c.low)
                .closePrice(c.close)
                .volume(c.volume)
                .build()
            s.addBar(bar, false)        // важный фикс: явный replace=false
        }
        s.setMaximumBarCount(600)       // можно и s.maximumBarCount = 600, но так надежнее
        return s
    }

    fun aggregate(series1m: BarSeries, period: Duration, name: String): BarSeries {
        val factor = period.toMinutes().toInt()
        val out = BaseBarSeriesBuilder().withName(name).build()
        val n = series1m.barCount
        var i = 0
        while (i < n) {
            val j = min(i + factor, n)
            val slice = (i until j).map { idx -> series1m.getBar(idx) }
            if (slice.isEmpty()) break

            val open = slice.first().openPrice.doubleValue()
            val close = slice.last().closePrice.doubleValue()
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            var vol = 0.0
            slice.forEach { b ->
                high = max(high, b.highPrice.doubleValue())
                low = min(low, b.lowPrice.doubleValue())
                vol += b.volume.doubleValue()
            }

            val bar = out.barBuilder()
                .timePeriod(period)
                .endTime(slice.last().endTime)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(vol)
                .build()
            out.addBar(bar, false)       // тоже с replace=false
            i = j
        }
        out.setMaximumBarCount(600)
        return out
    }

    fun makeCtx(symbol: String, candles1m: List<Candle>): SeriesCtx {
        val s1m = buildSeriesFrom1m(symbol, candles1m)
        val s5m = aggregate(s1m, Duration.ofMinutes(5), "${symbol}-5m")
        val s1h = aggregate(s1m, Duration.ofHours(1), "${symbol}-1h")
        return SeriesCtx(s1m, s5m, s1h)
    }
}
