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

```bash
  export TELEGRAM_TOKEN="7881022054:AAFQ7xetVVcd-TBpzH6BCRTMqEC5kdVjrQg"
  export TELEGRAM_CHAT_ID="1781660400"
```

## Телеграм

- Бот реагирует на `/ping` и `/id`, но поллинг автоматически отключается, если Telegram вернёт `409 Conflict` (обычно это происходит, когда тот же токен используется ещё где-то). Убедитесь, что не запущены другие экземпляры бота или сервисы с тем же токеном, прежде чем повторно запускать процесс.
