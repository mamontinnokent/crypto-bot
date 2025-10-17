package notify

import com.pengrad.telegrambot.ExceptionHandler
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.SendMessage
import signal.Signal
import signal.Direction
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

class TelegramNotifier(token: String, private val chatId: String) {
    private val bot = TelegramBot(token)
    private val started = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(TelegramNotifier::class.java)

    fun startPolling() {
        if (!started.compareAndSet(false, true)) return
        bot.setUpdatesListener { updates ->
            updates.forEach { upd ->
                val msg = upd.message()?.text()
                val chat = upd.message()?.chat() ?: upd.channelPost()?.chat()
                if (msg != null && msg.startsWith("/ping")) {
                    chat?.id()?.let { send(it, "pong") }
                }
                if (msg != null && msg.startsWith("/id")) {
                    chat?.id()?.let { send(it, "chat_id: $it") }
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }, ExceptionHandler { ex ->
            val response = ex.response()
            if (response?.errorCode() == 409) {
                logger.warn("Telegram polling disabled: {}", response.description())
                bot.removeGetUpdatesListener()
                started.set(false)
            } else {
                logger.error("Telegram polling failed", ex)
            }
        })
    }

    fun send(text: String) = send(chatId, text)

    fun sendSignal(s: Signal) {
        val emoji = if (s.direction == Direction.LONG) "🟢" else "🔴"
        val txt = buildString {
            append("$emoji ${s.direction} ${s.symbol}\n")
            append("Reason: ${s.reason}\n")
            append("RSI 1m=${"%.1f".format(s.rsi1m)} | 5m=${"%.1f".format(s.rsi5m)} | 1h=${"%.1f".format(s.rsi1h)}")
        }
        send(chatId, txt)
    }

    private fun send(targetChatId: Long, text: String) {
        bot.execute(SendMessage(targetChatId, text))
    }

    private fun send(targetChatId: String, text: String) {
        val numeric = targetChatId.toLongOrNull()
        if (numeric != null) {
            send(numeric, text)
        } else {
            bot.execute(SendMessage(targetChatId, text))
        }
    }
}
