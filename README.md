# mexc-scalp-bot (Spot v3, PB WS + REST fallback)

- Подписка на `spot@public.kline.v3.api.pb@SYMBOL@Min1` через Ktor WS
- Пинг каждые 15 сек, авто‑переподключение на `wbs-api`/`wbs`
- Если сервер шлёт только бинарные фреймы (protobuf) — включается REST fallback и каждую минуту берётся закрытая 1m свеча
- TA4J 0.18: серии через `BaseBarSeriesBuilder`, `addBar(bar, false)`

## Быстрый старт
```bash
export TELEGRAM_BOT_TOKEN=xxx
export TELEGRAM_CHAT_ID=123456789
export PAIRS=BTCUSDT,ETHUSDT,SOLUSDT
export USE_WS=true     # если не приходит WS, поставь false — работать будет на REST

./gradlew run   # либо gradle run
```

Включить подробный WS‑лог фреймов:
```bash
export WS_DEBUG=true
./gradlew run
```

## Файлы
- `feed/MexcV3JsonWs.kt` — WS клиент с .pb топиками, рест‑фолбэк при бинарных фреймах
- `feed/MexcRest.kt` — 1m klines
- `ta/SeriesBuilder.kt` — сборка/агрегация серий, RSI + паттерны
- `signal/SignalEngine.kt` — простая логика сигналов
- `notify/TelegramNotifier.kt` — Telegram polling, /ping и /id
- `app/Main.kt` — оркестрация
