package app

import kotlinx.coroutines.*
import notify.TelegramNotifier
import feed.Candle
import feed.MexcRestClient
import feed.MexcV3JsonWs
import ta.SeriesBuilder
import signal.SignalEngine

fun env(name: String, default: String? = null): String =
    System.getenv(name) ?: default ?: error("Env var $name is required")

fun main() = runBlocking {
    val token = "7881022054:AAFQ7xetVVcd-TBpzH6BCRTMqEC5kdVjrQg"
    val chatId = "1781660400"
    val pairs = env("PAIRS", "BTCUSDT,ETHUSDT").split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
    val useWs = env("USE_WS", "true").toBoolean()

    val notifier = TelegramNotifier(token, chatId).apply { startPolling() }
    val rest = MexcRestClient()
    val engine = SignalEngine()

    val buffers = pairs.associateWith { mutableListOf<Candle>() }.toMutableMap()
    coroutineScope {
        pairs.map { sym ->
            async(Dispatchers.IO) {
                val hist = rest.fetchKlines1m(sym, 180)
                buffers[sym]!!.addAll(hist.takeLast(180))
            }
        }.awaitAll()
    }

    if (useWs) {
        val ws = MexcV3JsonWs()
        ws.connectAndSubscribe(pairs, "Min1") { symbol, k ->
            val buf = buffers[symbol] ?: return@connectAndSubscribe
            buf.add(k)
            if (buf.size > 600) buf.removeAt(0)
            val ctx = SeriesBuilder.buildAll(symbol, buf)
            engine.check(ctx)?.let { notifier.sendSignal(it) }
        }
        while (true) delay(60_000)
    } else {
        suspend fun tick() = coroutineScope {
            pairs.map { sym ->
                async(Dispatchers.IO) {
                    val latest = rest.fetchKlines1m(sym, 2).last()
                    val buf = buffers[sym]!!
                    if (buf.isEmpty() || latest.openTime > buf.last().openTime) {
                        buf.add(latest)
                        if (buf.size > 600) buf.removeAt(0)
                        val ctx = SeriesBuilder.buildAll(sym, buf)
                        engine.check(ctx)?.let { notifier.sendSignal(it) }
                    }
                }
            }.awaitAll()
        }
        while (true) { tick(); delay(60_000) }
    }
}
