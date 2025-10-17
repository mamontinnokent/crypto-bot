package strategy

import org.ta4j.core.*
import org.ta4j.core.indicators.*
import org.ta4j.core.indicators.candles.*
import org.ta4j.core.indicators.helpers.*
import feed.SeriesCtx
import org.ta4j.core.indicators.averages.EMAIndicator
import org.ta4j.core.indicators.averages.SMAIndicator

object IndicatorsUtil {

    data class Values(
        val rsi1m: Double,
        val rsi5m: Double,
        val ema1m: Double,
        val ema5m: Double,
        val atr5m: Double,
        val volSpike: Boolean
    )

    fun compute(ctx: SeriesCtx): Values {
        val s1m = ctx.series1m
        val s5m = ctx.series5m
        val last1m = s1m.endIndex
        val last5m = s5m.endIndex

        val close1m = ClosePriceIndicator(s1m)
        val rsi1m = RSIIndicator(close1m, 14).getValue(last1m).doubleValue()

        val close5m = ClosePriceIndicator(s5m)
        val rsi5m = RSIIndicator(close5m, 14).getValue(last5m).doubleValue()

        val ema1m = EMAIndicator(close1m, 50).getValue(last1m).doubleValue()
        val ema5m = EMAIndicator(close5m, 50).getValue(last5m).doubleValue()

        val atr5m = ATRIndicator(s5m, 14).getValue(last5m).doubleValue()

        // volume spike: volume > 2 * SMA(volume, 20) on 1m
        val volInd = VolumeIndicator(s1m)
        val volSma = SMAIndicator(volInd, 20).getValue(last1m).doubleValue()
        val volNow = volInd.getValue(last1m).doubleValue()
        val volSpike = volSma > 0.0 && volNow > 2.0 * volSma

        return Values(rsi1m, rsi5m, ema1m, ema5m, atr5m, volSpike)
    }

    data class Patterns(val bullishPinBar: Boolean, val bearishPinBar: Boolean, val bullishEngulf: Boolean, val bearishEngulf: Boolean)

    fun detectPatterns(ctx: SeriesCtx): Patterns {
        val s1m = ctx.series1m
        val i = s1m.endIndex
        if (i < 1) return Patterns(
            bullishPinBar = false,
            bearishPinBar = false,
            bullishEngulf = false,
            bearishEngulf = false
        )

        // Engulfing using TA4J built-ins
        val bullEng = BullishEngulfingIndicator(s1m).getValue(i)
        val bearEng = BearishEngulfingIndicator(s1m).getValue(i)

        // Pin bar (custom): small body, long tail
        val b = s1m.getBar(i)
        val open = b.openPrice.doubleValue()
        val close = b.closePrice.doubleValue()
        val high = b.highPrice.doubleValue()
        val low = b.lowPrice.doubleValue()
        val body = kotlin.math.abs(close - open)
        val range = high - low
        val upperTail = high - maxOf(open, close)
        val lowerTail = minOf(open, close) - low

        val smallBody = body <= 0.3 * range
        val bullishPin = smallBody && lowerTail >= 2.0 * body && lowerTail >= 0.6 * range
        val bearishPin = smallBody && upperTail >= 2.0 * body && upperTail >= 0.6 * range

        return Patterns(bullishPin, bearishPin, bullEng, bearEng)
    }
}
