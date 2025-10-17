package app

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val config = BotConfig.fromEnv()
    BotApplication(config).run()
    return@runBlocking
}
