package app

import java.time.Duration

data class BotConfig(
    val telegramToken: String,
    val telegramChatId: String,
    val pairs: List<String>,
    val useWebsocket: Boolean,
    val pollInterval: Duration,
    val initialHistory: Int,
    val bufferSize: Int
) {
    companion object {
        fun fromEnv(env: (String) -> String? = System::getenv): BotConfig {
            fun require(name: String): String =
                env(name)?.takeIf { it.isNotBlank() }
                    ?: error("Env var $name is required")

            fun optional(name: String, default: String): String =
                env(name)?.takeIf { it.isNotBlank() } ?: default

            fun optionalInt(name: String, default: Int): Int =
                env(name)?.toIntOrNull() ?: default

            fun optionalDuration(name: String, defaultSeconds: Long): Duration =
                env(name)?.toLongOrNull()?.let { Duration.ofSeconds(it) } ?: Duration.ofSeconds(defaultSeconds)

            fun optionalBoolean(name: String, default: Boolean): Boolean =
                env(name)?.lowercase()?.let {
                    when (it) {
                        "true", "1", "yes", "y" -> true
                        "false", "0", "no", "n" -> false
                        else -> default
                    }
                } ?: default

            val token = "7881022054:AAFQ7xetVVcd-TBpzH6BCRTMqEC5kdVjrQg"
            val chatId = "1781660400"
            val pairs = optional("PAIRS", "BTCUSDT,ETHUSDT")
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .ifEmpty { error("PAIRS env var must contain at least one symbol") }

            return BotConfig(
                telegramToken = token,
                telegramChatId = chatId,
                pairs = pairs,
                useWebsocket = optionalBoolean("USE_WS", true),
                pollInterval = optionalDuration("POLL_INTERVAL_SECONDS", 60),
                initialHistory = optionalInt("INITIAL_HISTORY", 180),
                bufferSize = optionalInt("BUFFER_SIZE", 600)
            )
        }
    }
}
