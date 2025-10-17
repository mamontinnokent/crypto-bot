package trading

import feed.SeriesCtx
import strategy.Signal
import strategy.Direction
import notify.TelegramNotifier
import openai.OpenAIClient
import kotlin.math.max
import kotlin.math.min
import java.time.Instant

data class Position(
    val exchange: String,
    val symbol: String,
    var qty: Double,
    val entry: Double,
    var stop: Double,
    var tp: Double,
    val openedAt: Long,
    var trailOn: Boolean,
    var trailDistance: Double,
    var partialLeft: Boolean = true
)

class TradeManager(
    private val riskPct: Double,
    private val atrTP: Double,
    private val atrSL: Double,
    private val trailOn: Boolean,
    private val maxHoldMinutes: Long,
    private val balanceUSDT: Double,
    private val notifier: TelegramNotifier,
    private val executor: TradeExecutor,
    private val ai: OpenAIClient? = null
) {
    private val positions = mutableMapOf<String, Position>() // key = exchange:symbol

    fun onSignal(exchange: String, symbol: String, signal: Signal, ctx: SeriesCtx) {
        val key = "$exchange:$symbol"
        if (positions.containsKey(key)) return // already in trade

        // Compute ATR-based SL/TP on 5m
        val last5 = ctx.series5m.endIndex
        val lastClose = ctx.series1m.getBar(ctx.series1m.endIndex).closePrice.doubleValue()
        val atr = org.ta4j.core.indicators.ATRIndicator(ctx.series5m, 14).getValue(last5).doubleValue().coerceAtLeast(1e-8)

        val stop = if (signal.direction == Direction.LONG) lastClose - atr * atrSL else lastClose + atr * atrSL
        val tp   = if (signal.direction == Direction.LONG) lastClose + atr * atrTP else lastClose - atr * atrTP

        // Risk-based position sizing (approx)
        val riskPerTrade = balanceUSDT * (riskPct / 100.0)
        val riskPerUnit = kotlin.math.abs(lastClose - stop)
        val qty = if (riskPerUnit > 0) (riskPerTrade / riskPerUnit) else 0.0

        val placed = executor.placeMarket(exchange, symbol, if (signal.direction==Direction.LONG) "BUY" else "SELL", qty)
        if (!placed) return

        val pos = Position(
            exchange = exchange, symbol = symbol, qty = qty,
            entry = lastClose, stop = stop, tp = tp,
            openedAt = System.currentTimeMillis(),
            trailOn = trailOn, trailDistance = atr * atrSL
        )
        positions[key] = pos

        val reason = signal.reason
        val aiExplain = ai?.explainSignal(symbol, signal.direction.name, lastClose, reason) ?: ""
        val explainText = if (aiExplain.isNotBlank()) aiExplain else reason

        notifier.send(
            "🟢🔴".let { if (signal.direction==Direction.LONG) "🟢" else "🔴" } +
            " ${signal.direction} $symbol @ ${"%.4f".format(lastClose)}" +
            "SL ${"%.4f".format(stop)} | TP ${"%.4f".format(tp)} | qty ${"%.4f".format(qty)}" +
            "Почему: $explainText"
        )
    }

    // Вызывать на каждый закрытый 1m бар для сопровождения открытых позиций
    fun onBar(exchange: String, symbol: String, lastPrice: Double) {
        val key = "$exchange:$symbol"
        val p = positions[key] ?: return

        // Trailing stop
        if (p.trailOn) {
            if (p.qty > 0 && p.entry < lastPrice) {
                val newStop = lastPrice - p.trailDistance
                if (newStop > p.stop) p.stop = newStop
            }
            if (p.qty < 0 && p.entry > lastPrice) {
                val newStop = lastPrice + p.trailDistance
                if (newStop < p.stop) p.stop = newStop
            }
        }

        // Partial take-profit at 50% of TP distance
        val targetHalf = p.entry + (p.tp - p.entry) * 0.5
        if (p.partialLeft) {
            val reachedHalf = (p.qty > 0 && lastPrice >= targetHalf) || (p.qty < 0 && lastPrice <= targetHalf)
            if (reachedHalf) {
                val closeQty = kotlin.math.abs(p.qty) * 0.5
                executor.closeMarket(p.exchange, p.symbol, closeQty)
                p.qty = if (p.qty > 0) p.qty - closeQty else p.qty + closeQty * 1.0
                p.partialLeft = false
                notifier.send("📈 Partial TP 50% ${p.symbol} @ ${"%.4f".format(lastPrice)}")
            }
        }

        // Exit by SL/TP
        val hitSL = (p.qty > 0 && lastPrice <= p.stop) || (p.qty < 0 && lastPrice >= p.stop)
        val hitTP = (p.qty > 0 && lastPrice >= p.tp) || (p.qty < 0 && lastPrice <= p.tp)
        val timedOut = (System.currentTimeMillis() - p.openedAt) >= maxHoldMinutes * 60_000

        if (hitSL || hitTP || timedOut) {
            executor.closeMarket(p.exchange, p.symbol, kotlin.math.abs(p.qty))
            positions.remove(key)
            val tag = when {
                hitTP -> "✅ TP"
                hitSL -> "🔻 SL"
                else -> "⏱ TIME"
            }
            notifier.send("$tag ${p.symbol} @ ${"%.4f".format(lastPrice)} (entry ${"%.4f".format(p.entry)})")
        }
    }
}
