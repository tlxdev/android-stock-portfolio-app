package stockwidget.com.stockwidget.db

data class StockDataQuote(
    var symbol: String,
    var latestPrice: Float
)

data class StockData(
    var quote: StockDataQuote
)
