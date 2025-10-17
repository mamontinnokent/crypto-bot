package app

import feed.Candle
import feed.MexcRestClient
import feed.MexcV3JsonWs
import kotlinx.coroutines.*
import notify.TelegramNotifier
import signal.SignalEngine
import ta.SeriesBuilder
import kotlin.collections.ArrayDeque

class BotApplication(
    private val config: BotConfig,
    private val restClient: MexcRestClient = MexcRestClient(),
    private val signalEngine: SignalEngine = SignalEngine(),
    notifierFactory: (BotConfig) -> TelegramNotifier =
        { TelegramNotifier(it.telegramToken, it.telegramChatId) },
    private val wsClient: MexcV3JsonWs? = if (config.useWebsocket) MexcV3JsonWs() else null
) {

    private val notifier: TelegramNotifier = notifierFactory(config).also { it.startPolling() }
    private val buffers: MutableMap<String, ArrayDeque<Candle>> =
        config.pairs.associateWith { ArrayDeque<Candle>() }.toMutableMap()

    suspend fun run() = coroutineScope {
        preloadHistory()
        if (config.useWebsocket && wsClient != null) {
            launch { runWebsocketLoop(wsClient) }
        } else {
            launch { runPollingLoop() }
        }
    }

    private suspend fun preloadHistory() = coroutineScope {
        config.pairs.map { symbol ->
            async(Dispatchers.IO) {
                val candles = restClient.fetchKlines1m(symbol, config.initialHistory)
                val deque = buffers.getValue(symbol)
                deque.clear()
                deque.addAll(candles.takeLast(config.bufferSize))
            }
        }.awaitAll()
    }

    private suspend fun runWebsocketLoop(ws: MexcV3JsonWs) {
        ws.connectAndSubscribe(config.pairs, "Min1") { symbol, candle ->
            val buffer = buffers[symbol] ?: return@connectAndSubscribe
            appendCandle(buffer, candle)
            evaluate(symbol, buffer)
        }
    }

    private suspend fun runPollingLoop() {
        while (currentCoroutineContext().isActive) {
            fetchLatest()
            delay(config.pollInterval.toMillis())
        }
    }

    private suspend fun fetchLatest() = coroutineScope {
        config.pairs.map { symbol ->
            async(Dispatchers.IO) {
                val latest = restClient.fetchKlines1m(symbol, limit = 2).last()
                val buffer = buffers[symbol] ?: return@async
                if (buffer.isEmpty() || latest.openTime > buffer.last().openTime) {
                    appendCandle(buffer, latest)
                    evaluate(symbol, buffer)
                }
            }
        }.awaitAll()
    }

    private fun appendCandle(buffer: ArrayDeque<Candle>, candle: Candle) {
        buffer.addLast(candle)
        while (buffer.size > config.bufferSize) {
            buffer.removeFirst()
        }
    }

    private fun evaluate(symbol: String, buffer: ArrayDeque<Candle>) {
        val context = SeriesBuilder.buildAll(symbol, buffer.toList())
        signalEngine.check(context)?.let { signal ->
            notifier.sendSignal(signal)
        }
    }
}
