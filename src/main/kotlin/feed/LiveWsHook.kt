package feed

interface LiveWsHook {
    fun subscribe1m(symbols: List<String>, onBarClosed: (symbol: String, bar: Candle) -> Unit)
    fun close()
}
