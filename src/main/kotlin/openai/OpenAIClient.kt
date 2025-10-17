package openai

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class OpenAIClient(private val apiKey: String, private val model: String) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Serializable data class Message(val role: String, val content: String)
    @Serializable data class ChatReq(val model: String, val messages: List<Message>)
    @Serializable data class Choice(val index: Int, val message: Message)
    @Serializable data class ChatResp(val choices: List<Choice> = emptyList())

    suspend fun chat(prompt: String): String {
        val req = ChatReq(
            model = model,
            messages = listOf(
                Message("system", "Ты кратко и понятно объясняешь торговые сигналы для крипто-скальпинга."),
                Message("user", prompt)
            )
        )
        val resp = client.post("https://api.openai.com/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(req)
        }
        val body = resp.bodyAsText()
        return try {
            Json.decodeFromString(ChatResp.serializer(), body).choices.firstOrNull()?.message?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun explainSignalAsync(symbol: String, direction: String, price: Double, reason: String): String {
        val txt = "Инструмент: $symbol. Сигнал: $direction по цене $price. Факторы: $reason. Объясни кратко почему это разумный вход и какие риски."
        return chat(txt)
    }

    // sync facade for simpler use from TradeManager (fire-and-forget acceptable for demo)
    fun explainSignal(symbol: String, direction: String, price: Double, reason: String): String {
        return try {
            kotlinx.coroutines.runBlocking { explainSignalAsync(symbol, direction, price, reason) }
        } catch (e: Exception) {
            ""
        }
    }
}
