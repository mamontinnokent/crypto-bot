package notify

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.SendMessage
import signal.Signal
import signal.Direction

class TelegramNotifier(token: String, private val chatId: String) {
    private val bot = TelegramBot(token)

    fun startPolling() {
        bot.setUpdatesListener { updates ->
            updates.forEach { upd ->
                val msg = upd.message()?.text()
                val chat = upd.message()?.chat() ?: upd.channelPost()?.chat()
                if (msg != null && msg.startsWith("/ping")) {
                    bot.execute(SendMessage(chat!!.id(), "pong"))
                }
                if (msg != null && msg.startsWith("/id")) {
                    bot.execute(SendMessage(chat!!.id(), "chat_id: ${chat.id()}"))
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }

    fun sendSignal(s: Signal) {
        val emoji = if (s.direction == Direction.LONG) "🟢" else "🔴"
        val txt = buildString {
            append("$emoji ${s.direction} ${s.symbol}\n")
            append("Reason: ${s.reason}\n")
            append("RSI 1m=${"%.1f".format(s.rsi1m)} | 5m=${"%.1f".format(s.rsi5m)} | 1h=${"%.1f".format(s.rsi1h)}")
        }
        bot.execute(SendMessage(chatId, txt))
    }
}
