package stockwidget.com.stockwidget

import android.app.AlertDialog
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.widget.Filter
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.BasicNetwork
import com.android.volley.toolbox.DiskBasedCache
import com.android.volley.toolbox.HurlStack
import com.android.volley.toolbox.JsonObjectRequest
import com.arlib.floatingsearchview.FloatingSearchView
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.gson.Gson
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.json.JSONObject
import stockwidget.com.stockwidget.db.AppDatabase
import stockwidget.com.stockwidget.db.Stock
import stockwidget.com.stockwidget.db.StockData
import stockwidget.com.stockwidget.db.StockDataQuote
import java.io.BufferedReader
import java.util.Collections


class MainActivity : AppCompatActivity() {


    interface OnFindSuggestionsListener {
        fun onResults(results: List<HoldingSuggestion>)
    }


    private var holdingSuggestions: ArrayList<HoldingSuggestion> = ArrayList()
    private var ownedHoldings: ArrayList<HoldingSuggestion> = ArrayList()
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>? = null
    private var requestQueue: RequestQueue? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        setupAutocomplete()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        startAds()

        adapter = RecyclerAdapter()
        recycler_view.adapter = adapter

        if(isFirstTime()) {
            showApiLimitation()
        }

        startObservables()
        setupNetwork()

        layoutManager = LinearLayoutManager(this)
        recycler_view.layoutManager = layoutManager

        addSearchListeners()

    }

    fun setupNetwork() {

        val cache = DiskBasedCache(cacheDir, 1024 * 1024) // 1MB cap
        val network = BasicNetwork(HurlStack())
        requestQueue = RequestQueue(cache, network).apply {
            start()
        }
    }

    fun startAds (){
        MobileAds.initialize(this, "ca-app-pub-4018170482873185~2898941953")
        val mAdView: AdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    fun startObservables() {
        var liveDataStocks: LiveData<List<Stock>>

        // Update networth text on db change

        io.reactivex.Observable.just(AppDatabase.getInstance(applicationContext))
            .subscribeOn(Schedulers.io())
            .subscribe { db ->
                liveDataStocks = db.userDao().getAllLivedata()


                liveDataStocks.observe(this, Observer {
                        stockList ->
                    val text= findViewById<TextView>(R.id.networth_text)
                    var totalValue = 0.0f
                    stockList?.forEach{ stock ->
                        totalValue += stock.value * stock.amount.toFloat()
                    }
                    if(totalValue > 0) {
                        (adapter as RecyclerAdapter).networth = totalValue
                        (adapter as RecyclerAdapter).notifyDataSetChanged()
                        this.updateWidget(false)
                    }
                })

            }

        this.updateWidget(true)

        io.reactivex.Observable.just(AppDatabase.getInstance(applicationContext))
            .subscribeOn(Schedulers.io())
            .take(1)
            .subscribe { db -> // database operation
                var symbols: String = ""
                db?.userDao()?.getAll()?.forEach { stock ->
                    symbols += stock.code + ","
                    val suggestion = HoldingSuggestion(stock.code, stock.name)
                    suggestion.setAmount(stock.amount)
                    suggestion.setData(StockData(StockDataQuote(stock.code, stock.value)))
                    (adapter as RecyclerAdapter).suggestions.add(suggestion)
                }
                (adapter as RecyclerAdapter).notifyDataSetChanged()
                if(symbols != "") {
                    fetchStockData(symbols)
                }


            }
    }

    fun addSearchListeners() {
        val mSearchView = findViewById<FloatingSearchView>(R.id.floating_search_view)
        mSearchView.clearSuggestions()
        mSearchView.setOnQueryChangeListener { oldQuery: String, newQuery: String ->
            if (!oldQuery.equals("") && newQuery.equals("")) {
                mSearchView.clearSuggestions()
            } else {
                mSearchView.showProgress()
                findSuggestions(this, newQuery, 5, 100, object: OnFindSuggestionsListener{
                    override fun onResults(results: List<HoldingSuggestion>) {
                        mSearchView.swapSuggestions(results)
                        mSearchView.hideProgress()
                    }
                })
            }
        }

        mSearchView.setOnSearchListener(object : FloatingSearchView.OnSearchListener {
            override fun onSearchAction(currentQuery: String?) {
            }

            override fun onSuggestionClicked(searchSuggestion: SearchSuggestion) {
                addStock(searchSuggestion)

                if(!hasAddedWidget()){
                    showWidgetGuide()
                }
            }})
    }

    fun setupAutocomplete() {
        var names = readRawTextFile(applicationContext, R.raw.nyse)
        var lines = names.split("\n")
        for(name in lines){
            val name = name.split("\t");
            if(name.size == 2) {
                holdingSuggestions.add(HoldingSuggestion(name[0], name[1]))
            }
        }
    }

    fun updateWidget(refetchData: Boolean){
        val myIntent = Intent(applicationContext, StockUpdaterService::class.java)
        this.startService(myIntent)
    }

    fun fetchStockData(symbols: String) {
        val gson = Gson()

        val url = "https://api.iextrading.com/1.0/stock/market/batch?symbols=$symbols&types=quote"
        val jsonObjectRequest: JsonObjectRequest = JsonObjectRequest(Request.Method.GET, url, null,
            Response.Listener { response ->
                val jsonObject = JSONObject(response.toString())

                jsonObject.keys().forEach { key ->
                    val stockData = gson.fromJson(jsonObject.getJSONObject(key).toString(), StockData::class.java)

                    val holdingSuggestion = (adapter as RecyclerAdapter)
                        .suggestions
                        .findLast{ suggestion -> suggestion.getCode() == key }
                    holdingSuggestion
                        ?.setData(stockData)
                }


                val stocks: List<Stock> = (adapter as RecyclerAdapter)
                    .suggestions.map { suggestion -> suggestion.toStockDao() }


                io.reactivex.Observable.just(AppDatabase.getInstance(applicationContext))
                    .subscribeOn(Schedulers.io())
                    .subscribe { db -> // database operation
                        db?.userDao()?.insertAllStocks(stocks)
                    }

                (adapter as RecyclerAdapter).notifyDataSetChanged()


            },
            Response.ErrorListener { error ->
            }
        )
        requestQueue?.add(jsonObjectRequest)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_legal_notice -> {
                showLegalNotice()
                return true
            }
            R.id.action_clear_data -> {

                io.reactivex.Observable.just(AppDatabase.getInstance(applicationContext))
                    .subscribeOn(Schedulers.io())
                    .subscribe { db ->
                        AppDatabase.getInstance(applicationContext).userDao().deleteAll()
                }

                (adapter as RecyclerAdapter).networth = 0f
                (adapter as RecyclerAdapter).suggestions.clear()
                (adapter as RecyclerAdapter).notifyDataSetChanged()
                this.updateWidget(false)

                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun addStock(searchSuggestion: SearchSuggestion) {

        val mSearchView = findViewById<FloatingSearchView>(R.id.floating_search_view)
        mSearchView.clearSuggestions()

        val holdingSuggestion = searchSuggestion as HoldingSuggestion
        val existingSuggestion = (adapter as RecyclerAdapter).suggestions.filter { suggestion -> suggestion.getCode() == holdingSuggestion.getCode() }
        println("Existingsuggestion length " + existingSuggestion.size)
        if(existingSuggestion.isEmpty()){
            holdingSuggestion.setAmount(1)
            (adapter as RecyclerAdapter).suggestions.add(holdingSuggestion)
            var symbols = ""
            (adapter as RecyclerAdapter).suggestions.forEach {
                symbols += it.getCode() + ",";
            }

            symbols = symbols.substring(0, symbols.length - 1)

            mSearchView.clearSearchFocus()
            mSearchView.clearQuery()
            fetchStockData(symbols)
            (adapter as RecyclerAdapter).notifyDataSetChanged()
        } else {
            existingSuggestion.get(0).setAmount(existingSuggestion.get(0).getAmount() + 1)
            mSearchView.clearSearchFocus()
            mSearchView.clearQuery()

            val stocks: List<Stock> = (adapter as RecyclerAdapter)
                .suggestions.map { suggestion -> suggestion.toStockDao() }
            io.reactivex.Observable.just(AppDatabase.getInstance(applicationContext))
                .subscribeOn(Schedulers.io())
                .subscribe { db -> // database operation
                    db?.userDao()?.insertAllStocks(stocks)
                }
            (adapter as RecyclerAdapter).notifyDataSetChanged()
        }
    }

    fun showApiLimitation() {

        val builder = AlertDialog.Builder(this)
        builder?.setMessage("Only data for US stocks is available at the moment due to API limitations :(")
            ?.setTitle("Note")


        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            showLegalNotice()
        }

        builder.show()

    }

    fun showLegalNotice() {

        val builder = AlertDialog.Builder(this)
        builder?.setMessage("This software is provided as is without an warranty of any kind. The data shown is for reference purposes only, can be wrong or missing, and is not guaranteed to be up-to-date should never be used for making any financial or other decisions. The authors take no legal responsibility over any claim, damages or other liabilities caused by the use of this software.")
            ?.setTitle("Legal notice")

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
        }
        builder.show()
    }

    fun isFirstTime(): Boolean {
        val preferences = getPreferences(MODE_PRIVATE)
        val ranBefore = preferences.getBoolean("RanBefore", false);
        if (!ranBefore) {
            // first time
            val editor = preferences.edit();
            editor.putBoolean("RanBefore", true);
            editor.commit();
        }
        return !ranBefore;
    }


    fun hasAddedWidget(): Boolean {
        val preferences = getPreferences(MODE_PRIVATE)
        val ranBefore = preferences.getBoolean("AddedWidget", false);
        return ranBefore;
    }


    fun showWidgetGuide(){
        val builder = AlertDialog.Builder(this)
        builder?.setMessage("You can now add the networth widget onto your home screen.")
            ?.setTitle("Info")


        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            val preferences = getPreferences(MODE_PRIVATE)
            val editor = preferences.edit();
            editor.putBoolean("AddedWidget", true);
            editor.commit()
        }

        builder.show()

    }



    fun findSuggestions(
        context: Context, query: String, limit: Int, simulatedDelay: Long,
        listener: OnFindSuggestionsListener?
    ) {
        object : Filter() {

            override fun performFiltering(constraint: CharSequence?): FilterResults {

                try {
                    Thread.sleep(simulatedDelay)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                val suggestionList = ArrayList<HoldingSuggestion>()
                if (!(constraint == null || constraint.length == 0)) {

                    for (suggestion in holdingSuggestions) {
                        if (suggestion.body.toUpperCase()
                                .startsWith(constraint.toString().toUpperCase())
                        ) {

                            suggestionList.add(suggestion)
                            if (limit != -1 && suggestionList.size == limit) {
                                break
                            }
                        }
                    }
                }


                val results = FilterResults()
                Collections.sort(suggestionList, object : Comparator<HoldingSuggestion> {
                    override fun compare(lhs: HoldingSuggestion, rhs: HoldingSuggestion): Int {
                        return 0
                    }
                })
                results.values = suggestionList
                results.count = suggestionList.size

                return results
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {

                if (listener != null) {
                    listener.onResults(results.values as List<HoldingSuggestion>)
                }
            }
        }.filter(query)

    }

    fun readRawTextFile(ctx: Context, resId: Int): String {
        val inputStream = ctx.resources.openRawResource(resId)

        return inputStream.bufferedReader().use(BufferedReader::readText)
    }

}
