package ta

import feed.Candle
import org.ta4j.core.*
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import java.time.Duration
import java.time.Instant

data class MultiTfContext(
    val symbol: String,
    val series1m: BarSeries,
    val series5m: BarSeries,
    val series1h: BarSeries
)

object SeriesBuilder {

    fun buildAll(symbol: String, candles1m: List<Candle>): MultiTfContext {
        val s1m = buildSeriesFrom1m(symbol, candles1m)
        val s5m = aggregateSeries(s1m, Duration.ofMinutes(5))
        val s1h = aggregateSeries(s1m, Duration.ofHours(1))
        return MultiTfContext(symbol, s1m, s5m, s1h)
    }

    fun buildSeriesFrom1m(symbol: String, candles: List<Candle>): BarSeries {
        val series = BaseBarSeriesBuilder().withName("${symbol}-1m").build()
        for (c in candles) {
            val endTime = Instant.ofEpochMilli(c.openTime + 60_000)
            val bar = series.barBuilder()
                .timePeriod(Duration.ofMinutes(1))
                .endTime(endTime)
                .openPrice(c.open).highPrice(c.high).lowPrice(c.low).closePrice(c.close)
                .volume(c.volume).build()
            series.addBar(bar, false)
        }
        series.maximumBarCount = 600
        return series
    }

    fun aggregateSeries(series1m: BarSeries, period: Duration): BarSeries {
        val factor = (period.toMinutes()).toInt()
        val out = BaseBarSeriesBuilder().withName("${series1m.name}-${period}").build()
        val n = series1m.barCount
        if (n == 0) return out

        var i = 0
        while (i < n) {
            val j = minOf(i + factor, n)
            val slice = (i until j).map { idx -> series1m.getBar(idx) }
            if (slice.isEmpty()) break

            val open = slice.first().openPrice.doubleValue()
            val close = slice.last().closePrice.doubleValue()
            var high = Double.NEGATIVE_INFINITY
            var low = Double.POSITIVE_INFINITY
            var vol = 0.0
            for (b in slice) {
                high = kotlin.math.max(high, b.highPrice.doubleValue())
                low = kotlin.math.min(low, b.lowPrice.doubleValue())
                vol += b.volume.doubleValue()
            }
            val endTime = slice.last().endTime
            val bar = out.barBuilder()
                .timePeriod(period)
                .endTime(endTime)
                .openPrice(open).highPrice(high).lowPrice(low).closePrice(close).volume(vol).build()
            out.addBar(bar, false)
            i = j
        }
        out.maximumBarCount = 600
        return out
    }

    data class Indicators(val rsi: Double, val bullish: Boolean, val bearish: Boolean, val momentumPct3: Double)

    fun calcIndicators(series: BarSeries, rsiPeriod: Int = 14): Indicators {
        val last = series.endIndex
        if (last < kotlin.math.max(rsiPeriod, 3)) return Indicators(Double.NaN, false, false, 0.0)

        val close = ClosePriceIndicator(series)
        val rsi = RSIIndicator(close, rsiPeriod).getValue(last).doubleValue()

        val bull = BullishEngulfingIndicator(series).getValue(last)
        val bear = BearishEngulfingIndicator(series).getValue(last)

        val cNow = close.getValue(last).doubleValue()
        val c3 = close.getValue(last - 3).doubleValue()
        val momentumPct3 = if (c3 != 0.0) (cNow - c3) / c3 * 100.0 else 0.0

        return Indicators(rsi = rsi, bullish = bull, bearish = bear, momentumPct3 = momentumPct3)
    }
}
