package stockwidget.com.stockwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.view.View
import android.widget.RemoteViews
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import stockwidget.com.stockwidget.db.AppDatabase
import stockwidget.com.stockwidget.db.Stock
import stockwidget.com.stockwidget.db.StockData
import java.math.BigDecimal


class StockWidgetProvider : AppWidgetProvider() {

    var requestQueue: RequestQueue? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val count = appWidgetIds.size
        for (i in 0 until count) {
            val widgetId = appWidgetIds[i]

            val remoteViews = RemoteViews(
                context.getPackageName(),
                R.layout.simple_widget
            )


            var networth = 0.0

            Observable.just(AppDatabase.getInstance(context))
                .subscribeOn(Schedulers.io())
                .subscribe { db -> // database operation
                    db?.userDao()?.getAll()?.forEach { stock -> networth += stock.amount * stock.value }
                    val rounded = BigDecimal(networth).setScale(0, BigDecimal.ROUND_HALF_UP).toString()
                    val networthText = "$$rounded"
                    setNetworthText(remoteViews, networthText)
                    appWidgetManager.updateAppWidget(widgetId, remoteViews)
                }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "APPWIDGET_UPDATE" || intent.action == "android.appwidget.action.APPWIDGET_ENABLED"
            || intent.action == "REFRESH_USING_LOCAL_DATA") {
            var shouldUpdate: Boolean? = true
            if(intent.action == "APPWIDGET_ENABLED" || intent.action == "REFRESH_USING_LOCAL_DATA"){
                shouldUpdate = false
            }
            val stockList = FetchStockData(context).execute().get()
            if(shouldUpdate != null && shouldUpdate) {
                requestQueue = Volley.newRequestQueue(context)
                fetchStockData(context, stockList)
            }else{
                setData(context, stockList)
            }
        }

        super.onReceive(context, intent)
    }


    fun fetchStockData(context: Context, stocks: List<Stock>){
        var symbols = ""
        stocks.forEach {stock -> symbols += stock.code+"," }
                val gson = Gson()

        val url = "https://api.iextrading.com/1.0/stock/market/batch?symbols=$symbols&types=quote"
        val jsonObjectRequest = JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->

                val jsonObject = JSONObject(response.toString())

                var networth = 0.0

                jsonObject.keys().forEach { key ->
                    val stockData = gson.fromJson(jsonObject.getJSONObject(key).toString(), StockData::class.java)
                    val curStock = stocks.find { stock -> stock.code == key }
                    curStock?.value = stockData.quote.latestPrice
                    networth += (curStock?.value!! * curStock?.amount!!.toDouble())
                }

                // Update db stocks
                Observable.just(AppDatabase.getInstance(context))
                    .subscribeOn(Schedulers.io())
                    .subscribe { db -> // database operation
                        db?.userDao()?.insertAllStocks(stocks)
                    }


                val remoteViews = RemoteViews(
                    context.getPackageName(),
                    R.layout.simple_widget
                )


                AppWidgetManager.getInstance(context).updateAppWidget(
                    ComponentName(context, StockWidgetProvider::class.java), remoteViews
                )



            },
            Response.ErrorListener { error ->
            }
        )
        requestQueue?.add(jsonObjectRequest)
    }

    fun setData(context: Context, list: List<Stock>){

        var networth = 0.0

        list.forEach { stock -> networth += stock.amount.toDouble() * stock.value.toDouble() }


        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, StockWidgetProvider::class.java!!.getName())

        onUpdate(context, appWidgetManager , appWidgetManager.getAppWidgetIds(thisAppWidget))


    }

    fun setNetworthText(remoteViews: RemoteViews, networthText: String){
        remoteViews.setTextViewText(R.id.textView, networthText)
        remoteViews.setViewVisibility(R.id.networth_loading_textView, View.GONE)
        remoteViews.setViewVisibility(R.id.textView, View.VISIBLE)
    }



    inner class FetchStockData(private val context: Context) : AsyncTask<Int, Void, List<Stock>>() {

        override fun doInBackground(vararg params: Int?): List<Stock>? {
            return AppDatabase.getInstance(context).userDao().getAll()
        }
    }
}
