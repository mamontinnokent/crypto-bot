# Патч WS

- Исправлена строковая интерполяция тем `topics` (раньше были `$it`/`$interval` в логе).
- Добавлен `id` в SUBSCRIPTION и регулярный `PING` каждые 15 сек.
- Принудительный `uppercase()` для символов.
- Более устойчивый парсинг payload (d/data/kline/publicspotkline).

Подмена файлов:
- замените `src/main/kotlin/feed/MexcV3JsonWs.kt`
- `src/main/resources/logback.xml` (если хотите дефолтный INFO)

Дополнительно можно включить подробный лог входящих фреймов:
```
WS_DEBUG=true ./gradlew run
```
