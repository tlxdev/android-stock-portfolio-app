package stockwidget.com.stockwidget

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import io.reactivex.schedulers.Schedulers
import stockwidget.com.stockwidget.db.AppDatabase

class RecyclerAdapter : RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {

    val suggestions: ArrayList<HoldingSuggestion> = ArrayList()

    var networth = 0.0f
    set

    inner class CardViewHolder(itemView: View) : ViewHolder(itemView) {

        var itemTitle: TextView
        var itemDetail: TextView
        var itemPrice: TextView
        var itemAmount: TextView
        var itemWorth: TextView

        var layout: RelativeLayout

        init {
                itemTitle = itemView.findViewById(R.id.item_title)
                itemDetail = itemView.findViewById(R.id.item_detail)
                itemPrice = itemView.findViewById(R.id.item_price)
                itemAmount = itemView.findViewById(R.id.item_amount)
                itemWorth = itemView.findViewById(R.id.item_worth)

                layout = itemView.findViewById(R.id.item_layout)
        }
    }


    open inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }


    open inner class NetworthViewHolder(itemView: View) : ViewHolder(itemView) {
        var networthValue: TextView

        init {
            networthValue = itemView.findViewById(R.id.networth_text)
        }
    }



    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        if(i == 0){
            val v = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.holding_recyclerview_header, viewGroup, false)
            return NetworthViewHolder(v)
        }else {
            val v = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.holding_card_layout, viewGroup, false)
            return CardViewHolder(v)
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerAdapter.ViewHolder, i: Int) {
                if(i > 0) {
            val viewHolder = viewHolder as CardViewHolder
            val suggestion = suggestions[i-1]
            viewHolder.itemTitle.text = suggestion.getCode()
            viewHolder.itemDetail.text = suggestion.body
            viewHolder.itemPrice.text = suggestion.getPrice()
            viewHolder.itemAmount.text = suggestion.getAmountText()
            viewHolder.itemWorth.text = suggestion.getWorth()

            viewHolder.layout.setOnClickListener { click ->
                suggestion.setAmount(suggestion.getAmount() + 1)
                this.notifyItemChanged(i)

                io.reactivex.Observable.just(AppDatabase.getInstance(viewHolder.itemTitle.context))
                    .subscribeOn(Schedulers.io())
                    .subscribe { db ->
                        // database operation
                        db?.userDao()?.insertStock(suggestion.toStockDao())
                    }
            }
        }else{
            val viewHolder = viewHolder as NetworthViewHolder
                        viewHolder.networthValue.text = "Your net worth is: $" + networth.toString()
        }
    }


    override fun getItemViewType(position: Int): Int {
        if(position == 0){
            return 0
        }
        return 1
    }

    override fun getItemCount(): Int {
        return suggestions.size+1
    }



}
