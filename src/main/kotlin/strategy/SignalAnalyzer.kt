package strategy

import feed.SeriesCtx
import kotlin.math.max
import kotlin.math.min

enum class Direction { LONG, SHORT }

data class Signal(
    val direction: Direction,
    val reason: String
)

class SignalAnalyzer {

    fun checkSignal(symbol: String, ctx: SeriesCtx): Signal? {
        val ind = IndicatorsUtil.compute(ctx)
        val pat = IndicatorsUtil.detectPatterns(ctx)

        // Trend filter: prefer LONG above EMA, SHORT below EMA (1m & 5m agree)
        val trendUp = ctx.series1m.getBar(ctx.series1m.endIndex).closePrice.doubleValue() > ind.ema1m &&
                      ctx.series5m.getBar(ctx.series5m.endIndex).closePrice.doubleValue() > ind.ema5m
        val trendDn = ctx.series1m.getBar(ctx.series1m.endIndex).closePrice.doubleValue() < ind.ema1m &&
                      ctx.series5m.getBar(ctx.series5m.endIndex).closePrice.doubleValue() < ind.ema5m

        // Adaptive RSI thresholds
        val rsiBuyThr = if (trendUp) 45.0 else 30.0
        val rsiSellThr = if (trendDn) 55.0 else 70.0

        val lastClose = ctx.series1m.getBar(ctx.series1m.endIndex).closePrice.doubleValue()
        val reasonParts = mutableListOf<String>()

        // LONG setup
        val longOk =
            (ind.rsi1m <= rsiBuyThr) &&
            (pat.bullishPinBar || pat.bullishEngulf) &&
            (ind.volSpike || trendUp)

        if (longOk && trendUp) reasonParts.add("trend↑ EMA filter ok")
        if (longOk && ind.volSpike) reasonParts.add("volume spike")
        if (longOk && pat.bullishPinBar) reasonParts.add("bullish pin-bar")
        if (longOk && pat.bullishEngulf) reasonParts.add("bullish engulfing")
        if (longOk && ind.rsi1m <= rsiBuyThr) reasonParts.add("RSI oversold/adaptive")

        if (longOk) {
            return Signal(Direction.LONG, reasonParts.joinToString(", "))
        }

        // SHORT setup
        val shortOk =
            (ind.rsi1m >= rsiSellThr) &&
            (pat.bearishPinBar || pat.bearishEngulf) &&
            (ind.volSpike || trendDn)

        reasonParts.clear()
        if (shortOk && trendDn) reasonParts.add("trend↓ EMA filter ok")
        if (shortOk && ind.volSpike) reasonParts.add("volume spike")
        if (shortOk && pat.bearishPinBar) reasonParts.add("bearish pin-bar")
        if (shortOk && pat.bearishEngulf) reasonParts.add("bearish engulfing")
        if (shortOk && ind.rsi1m >= rsiSellThr) reasonParts.add("RSI overbought/adaptive")

        if (shortOk) {
            return Signal(Direction.SHORT, reasonParts.joinToString(", "))
        }

        return null
    }
}
