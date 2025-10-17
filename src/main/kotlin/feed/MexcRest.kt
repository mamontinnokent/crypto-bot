package feed

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class MexcRestClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
        engine { requestTimeout = 15_000 }
    }

    suspend fun fetchKlines1m(symbol: String, limit: Int = 120): List<Candle> {
        val url = "https://api.mexc.com/api/v3/klines?symbol=${symbol}&interval=1m&limit=${limit}"
        val txt: String = client.get(url).body()
        val arr = Json.parseToJsonElement(txt).jsonArray
        return arr.map { e ->
            val a = e.jsonArray
            Candle(
                openTime = a[0].jsonPrimitive.long,
                open = a[1].jsonPrimitive.content.toDouble(),
                high = a[2].jsonPrimitive.content.toDouble(),
                low = a[3].jsonPrimitive.content.toDouble(),
                close = a[4].jsonPrimitive.content.toDouble(),
                volume = a[5].jsonPrimitive.content.toDouble()
            )
        }
    }
}
