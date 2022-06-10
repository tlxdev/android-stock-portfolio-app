package stockwidget.com.stockwidget

import android.app.IntentService
import android.app.Service
import android.content.Intent
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import stockwidget.com.stockwidget.db.AppDatabase
import stockwidget.com.stockwidget.db.StockData


class StockUpdaterService : IntentService("StockUpdaterService") {

    override fun onCreate() {
                super.onCreate()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
                super.onStartCommand(intent, startId, startId)
        return Service.START_STICKY
    }

    override fun onHandleIntent(intent: Intent?) {
        try {

            Observable.just(AppDatabase.getInstance(applicationContext))
                .subscribeOn(Schedulers.io())
                .subscribe { db -> // database operation
                    var data = ArrayList<Pair<String, Int>>()
                    db?.userDao()?.getAll()?.forEach { stock -> data.add(Pair(stock.code, stock.amount))}
                    fetchStockData(data)
                }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

    }


    fun fetchStockData(data: ArrayList<Pair<String, Int>>) {

        if(data.isEmpty()){
            updateWidgetData(0f)
        }else {


            val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap

            val network = BasicNetwork(HurlStack())

            val requestQueue = RequestQueue(cache, network).apply {
                start()
            }

            var symbols = ""
            data.forEach { stock -> symbols += stock.first + "," }
            val gson = Gson()

            val url = "https://api.iextrading.com/1.0/stock/market/batch?symbols=$symbols&types=quote"
            val jsonObjectRequest = JsonObjectRequest(
                Request.Method.GET, url, null,
                Response.Listener { response ->

                    val jsonObject = JSONObject(response.toString())

                    var networth = 0.0f

                    jsonObject.keys().forEach { key ->
                        val stockData = gson.fromJson(jsonObject.getJSONObject(key).toString(), StockData::class.java)
                        networth += data.find { stock -> stock.first == key }!!.second * stockData.quote.latestPrice;
                    }

                    updateWidgetData(networth)

                },
                Response.ErrorListener { error ->
                    // TODO: Handle error
                }
            )
            requestQueue?.add(jsonObjectRequest)
        }
    }

    fun updateWidgetData(networth: Float){
        val intent = Intent(this, StockWidgetProvider::class.java)
        intent.action = "REFRESH_USING_LOCAL_DATA"
        intent.putExtra("networth", networth)
        sendBroadcast(intent)
    }
}