package feed

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class MexcV3JsonWs(
    private val endpoints: List<String> = listOf("wss://wbs-api.mexc.com/ws", "wss://wbs.mexc.com/ws")
) {
    private val log = LoggerFactory.getLogger("MexcV3JsonWs")
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val lastWindowStart = ConcurrentHashMap<String, Long>()
    private val lastCandle = ConcurrentHashMap<String, Candle>()
    private var restJob: Job? = null
    private var wsJob: Job? = null

    suspend fun connectAndSubscribe(
        symbols: List<String>,
        interval: String = "Min1",
        onClosedBar: (symbol: String, bar: Candle) -> Unit
    ) = coroutineScope {
        wsJob?.cancel()
        restJob?.cancel()
        wsJob = launch {
            var epIdx = 0
            while (isActive) {
                val ep = endpoints[epIdx % endpoints.size]
                try {
                    runSession(ep, symbols, interval, onClosedBar)
                } catch (e: Exception) {
                    log.warn("WS session error on {}: {}", ep, e.message)
                }
                epIdx++
                delay(2_000)
            }
        }
    }

    fun close() { wsJob?.cancel(); restJob?.cancel() }

    private suspend fun runSession(
        endpoint: String,
        symbols: List<String>,
        interval: String,
        onClosedBar: (String, Candle) -> Unit
    ) {
        client.webSocket(urlString = endpoint) {
            val topics = symbols.map { sym -> "spot@public.kline.v3.api.pb@${sym.uppercase()}@${interval}" }
            val sub = buildJsonObject {
                put("id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
                put("method", "SUBSCRIPTION")
                put("params", JsonArray(topics.map { JsonPrimitive(it) }))
            }
            send(Frame.Text(sub.toString()))
            log.info("Subscribed: {}", topics.joinToString(","))

            var lastFrameTs = System.currentTimeMillis()

            val pingJob = launch {
                while (isActive) {
                    delay(15_000)
                    val ping = buildJsonObject { put("id", 1); put("method", "PING") }.toString()
                    send(Frame.Text(ping))
                }
            }
            val watchdog = launch {
                while (isActive) {
                    delay(30_000)
                    if (System.currentTimeMillis() - lastFrameTs > 30_000) {
                        throw CancellationException("No frames for 30s, reconnect")
                    }
                }
            }

            val rest = MexcRestClient()
            val restLast = ConcurrentHashMap<String, Long>()

            suspend fun ensureRestFallback() {
                if (restJob?.isActive == true) return
                log.warn("Binary frames detected. Enabling REST 1m fallback for closed bars.")
                restJob = launch {
                    while (isActive) {
                        try {
                            symbols.forEach { sym ->
                                val kl = rest.fetchKlines1m(sym, 2)
                                if (kl.isNotEmpty()) {
                                    val last = kl.last()
                                    val prevTs = restLast.put(sym, last.openTime) ?: 0L
                                    if (last.openTime > prevTs) onClosedBar(sym, last)
                                }
                            }
                        } catch (_: Exception) {}
                        delay(60_000)
                    }
                }
            }

            try {
                for (frame in incoming) {
                    lastFrameTs = System.currentTimeMillis()
                    when (frame) {
                        is Frame.Text -> handleText(frame.readText(), onClosedBar)
                        is Frame.Binary -> ensureRestFallback()
                        else -> {}
                    }
                }
            } finally {
                pingJob.cancel()
                watchdog.cancel()
            }
        }
    }

    private fun handleText(txt: String, onClosedBar: (String, Candle) -> Unit) {
        try {
            val root = Json.parseToJsonElement(txt).jsonObject
            if ("code" in root && "msg" in root) return
            if (root["method"]?.jsonPrimitive?.content == "PONG") return

            val channel = root["c"]?.jsonPrimitive?.content
                ?: root["channel"]?.jsonPrimitive?.content ?: return
            if (!channel.startsWith("spot@public.kline.v3.api")) return

            val parts = channel.split("@")
            val symbol = parts.lastOrNull { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() } }
                ?: root["symbol"]?.jsonPrimitive?.content ?: return

            val container = root["d"]?.jsonObject ?: root["data"]?.jsonObject ?: root
            val kObj = container["k"]?.jsonObject
                ?: container["kline"]?.jsonObject
                ?: container["publicspotkline"]?.jsonObject ?: return

            val ws = kObj["windowstart"]?.jsonPrimitive?.long ?: kObj["t"]?.jsonPrimitive?.long ?: return
            val open = parseNum(kObj, "openingprice", "o")
            val close = parseNum(kObj, "closingprice", "c")
            val high = parseNum(kObj, "highestprice", "h")
            val low = parseNum(kObj, "lowestprice", "l")
            val vol = parseNum(kObj, "volume", "v")
            if (open.isNaN() || close.isNaN() || high.isNaN() || low.isNaN()) return

            val candle = Candle(ws * 1000, open, high, low, close, vol)
            val prevWs = lastWindowStart.put(symbol, ws)
            val prevC = lastCandle.put(symbol, candle)
            if (prevWs != null && prevWs != ws && prevC != null) onClosedBar(symbol, prevC)
        } catch (_: Exception) {}
    }

    private fun parseNum(obj: JsonObject, vararg keys: String): Double {
        for (k in keys) {
            val el = obj[k] ?: continue
            val p = el.jsonPrimitive
            return if (p.isString) p.content.toDoubleOrNull() ?: Double.NaN else p.double
        }
        return Double.NaN
    }
}
