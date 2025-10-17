package feed

import kotlinx.coroutines.*

class BybitDataHub {
    suspend fun bootstrap(symbols: List<String>) { /* TODO: fill if needed */ }
    suspend fun tick(symbols: List<String>, onClosed: (String, Candle, SeriesCtx) -> Unit) {
        // TODO: fetch 1m klines via Bybit public REST and call onClosed for the last closed bar
    }
}

class OkxDataHub {
    suspend fun bootstrap(symbols: List<String>) { /* TODO: fill if needed */ }
    suspend fun tick(symbols: List<String>, onClosed: (String, Candle, SeriesCtx) -> Unit) {
        // TODO: fetch 1m klines via OKX public REST and call onClosed for the last closed bar
    }
}
