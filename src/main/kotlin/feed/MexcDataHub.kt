package feed

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class MexcDataHub(private val useWs: Boolean = true) {

    private val rest = MexcRestClient()
    private val ws = if (useWs) MexcV3JsonWs() else null

    private val buffers = ConcurrentHashMap<String, MutableList<Candle>>()
    private var onClosed: ((String, Candle, SeriesCtx) -> Unit)? = null

    fun onBarClosed(cb: (symbol: String, bar: Candle, ctx: SeriesCtx) -> Unit) {
        onClosed = cb
    }

    fun bootstrap(symbols: List<String>) = runBlocking {
        coroutineScope {
            symbols.map { sym ->
                async(Dispatchers.IO) {
                    val hist = rest.fetchKlines1m(sym, limit = 200)
                    buffers.computeIfAbsent(sym) { mutableListOf() }.apply {
                        clear(); addAll(hist.takeLast(200))
                    }
                }
            }.awaitAll()
        }
        if (useWs && ws != null) {
            ws.connectAndSubscribe(symbols) { symbol, bar ->
                val buf = buffers.computeIfAbsent(symbol) { mutableListOf() }
                buf.add(bar)
                while (buf.size > 600) buf.removeAt(0)
                onClosed?.let { it(symbol, bar, SeriesUtil.makeCtx(symbol, buf)) }
            }
        }
    }
}
