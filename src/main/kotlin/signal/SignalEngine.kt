package signal

import ta.SeriesBuilder
import ta.MultiTfContext

data class Signal(
    val symbol: String,
    val direction: Direction,
    val reason: String,
    val rsi1m: Double,
    val rsi5m: Double,
    val rsi1h: Double
)
enum class Direction { LONG, SHORT }

class SignalEngine(
    private val buyRsi: Double = 30.0,
    private val sellRsi: Double = 70.0
) {
    fun check(ctx: MultiTfContext): Signal? {
        val i1 = SeriesBuilder.calcIndicators(ctx.series1m)
        val i5 = SeriesBuilder.calcIndicators(ctx.series5m)
        val iH = SeriesBuilder.calcIndicators(ctx.series1h)

        if (!i1.rsi.isNaN() && i1.rsi < buyRsi && i1.bullish && i1.momentumPct3 >= 0) {
            val reason = "RSI1m=${"%.1f".format(i1.rsi)} < $buyRsi, Bull Engulf, mom3=${"%.2f".format(i1.momentumPct3)}%"
            return Signal(ctx.symbol, Direction.LONG, reason, i1.rsi, i5.rsi, iH.rsi)
        }
        if (!i1.rsi.isNaN() && i1.rsi > sellRsi && i1.bearish && i1.momentumPct3 <= 0) {
            val reason = "RSI1m=${"%.1f".format(i1.rsi)} > $sellRsi, Bear Engulf, mom3=${"%.2f".format(i1.momentumPct3)}%"
            return Signal(ctx.symbol, Direction.SHORT, reason, i1.rsi, i5.rsi, iH.rsi)
        }
        return null
    }
}
