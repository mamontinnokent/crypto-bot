package feed

import org.ta4j.core.BarSeries
import ta.SeriesBuilder

data class SeriesCtx(
    val series1m: BarSeries,
    val series5m: BarSeries,
    val series1h: BarSeries
)

object SeriesUtil {
    fun makeCtx(symbol: String, candles1m: List<Candle>): SeriesCtx {
        val multi = SeriesBuilder.buildAll(symbol, candles1m)
        return SeriesCtx(
            series1m = multi.series1m,
            series5m = multi.series5m,
            series1h = multi.series1h
        )
    }
}
