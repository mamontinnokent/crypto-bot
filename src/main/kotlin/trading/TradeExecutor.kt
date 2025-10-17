package trading

import feed.Candle

/**
 * Исполнение ордеров. По умолчанию DRY_RUN (paper).
 * Для реальной торговли реализуй вызовы REST/WS биржи здесь.
 */
class TradeExecutor(private val dryRun: Boolean = true) {

    fun placeMarket(exchange: String, symbol: String, side: String, qty: Double): Boolean {
        if (dryRun) return true
        // TODO: вызвать реальный ордер на бирже
        return false
    }

    fun closeMarket(exchange: String, symbol: String, qty: Double): Boolean {
        if (dryRun) return true
        // TODO
        return false
    }
}
