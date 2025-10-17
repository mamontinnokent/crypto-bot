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
    private val endpoint: String = "wss://wbs-api.mexc.com/ws"
) {
    private val log = LoggerFactory.getLogger("MexcV3JsonWs")
    private val client = HttpClient(CIO) { install(WebSockets) }
    private val lastWindowStart = ConcurrentHashMap<String, Long>()
    private val lastCandle = ConcurrentHashMap<String, Candle>()

    suspend fun connectAndSubscribe(
        symbols: List<String>,
        interval: String = "Min1",
        onClosedBar: (symbol: String, bar: Candle) -> Unit
    ) = coroutineScope {
        client.webSocket(urlString = endpoint) {
            // нормальная интерполяция и uppercase символов
            val topics = symbols.map { sym -> "spot@public.kline.v3.api@${sym.uppercase()}@${interval}" }
            val sub = buildJsonObject {
                put("id", (System.currentTimeMillis() % Int.MAX_VALUE).toInt())
                put("method", "SUBSCRIPTION")
                put("params", JsonArray(topics.map { JsonPrimitive(it) }))
            }
            send(Frame.Text(sub.toString()))
            log.info("Subscribed: {}", topics.joinToString(","))

            // ping (некоторые регионы дропают без пинга)
            launch {
                while (true) {
                    delay(15_000)
                    val ping = buildJsonObject { put("id", 1); put("method", "PING") }.toString()
                    send(Frame.Text(ping))
                }
            }

            // при желании можно включить подробный лог входящих кадров
            val verbose = System.getenv("WS_DEBUG")?.equals("true", ignoreCase = true) == true

            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val txt = frame.readText()
                        if (verbose) log.info("WS RX: {}", txt)
                        handleMessage(txt, onClosedBar)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleMessage(txt: String, onClosedBar: (String, Candle) -> Unit) {
        try {
            val root = Json.parseToJsonElement(txt).jsonObject

            // ack или ping-pong
            if ("code" in root && "msg" in root) return
            if (root["method"]?.jsonPrimitive?.content == "PONG") return

            val channel = root["c"]?.jsonPrimitive?.content
                ?: root["channel"]?.jsonPrimitive?.content
                ?: return
            if (!channel.startsWith("spot@public.kline.v3.api@")) return

            // symbol из канала
            val parts = channel.split("@")
            val symbol = parts.getOrNull(2) ?: root["symbol"]?.jsonPrimitive?.content ?: return

            // payload размещают по-разному: d/data/kline/publicspotkline
            val container = root["d"]?.jsonObject ?: root["data"]?.jsonObject ?: root
            val kObj = container["k"]?.jsonObject
                ?: container["kline"]?.jsonObject
                ?: container["publicspotkline"]?.jsonObject
                ?: return

            val ws = kObj["windowstart"]?.jsonPrimitive?.long
                ?: kObj["t"]?.jsonPrimitive?.long
                ?: return
            val open = parseNum(kObj, "openingprice", "o")
            val close = parseNum(kObj, "closingprice", "c")
            val high = parseNum(kObj, "highestprice", "h")
            val low = parseNum(kObj, "lowestprice", "l")
            val vol = parseNum(kObj, "volume", "v")

            if (open.isNaN() || close.isNaN() || high.isNaN() || low.isNaN()) return

            val candle = Candle(
                openTime = ws * 1000,
                open = open, high = high, low = low, close = close, volume = vol
            )
            val prevWs = lastWindowStart.put(symbol, ws)
            val prevC = lastCandle.put(symbol, candle)
            if (prevWs != null && prevWs != ws && prevC != null) {
                onClosedBar(symbol, prevC)
            }
        } catch (e: Exception) {
            log.debug("WS parse fail: {}", e.message)
        }
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
