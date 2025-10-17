package telegram

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.request.SendMessage

class TelegramNotifier(token: String, private val chatId: String) {
    private val bot = TelegramBot(token)

    fun startPolling() {
        bot.setUpdatesListener { updates ->
            updates.forEach { upd ->
                val txt = upd.message()?.text()
                if (txt != null && txt.startsWith("/ping")) {
                    bot.execute(SendMessage(chatId, "pong"))
                }
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }

    fun send(text: String) {
        bot.execute(SendMessage(chatId, text))
    }
}
