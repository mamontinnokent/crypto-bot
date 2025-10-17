# mexc-scalp-bot (fixed WS)

Рабочий адаптер под mexc-client 0.2.0 с публичным subscribe(pair, interval).
JDK 17 подтягивается через Gradle toolchains.

## Быстрый старт
```bash
export TELEGRAM_BOT_TOKEN=xxx
export TELEGRAM_CHAT_ID=123456789
export MEXC_PAIRS=BTCUSDT,ETHUSDT
export RSI_BUY=30
export RSI_SELL=70
export USE_WS=true

./gradlew run
```
