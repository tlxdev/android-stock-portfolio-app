package stockwidget.com.stockwidget

import android.os.Parcel
import android.os.Parcelable
import com.arlib.floatingsearchview.suggestions.model.SearchSuggestion
import stockwidget.com.stockwidget.db.Stock
import stockwidget.com.stockwidget.db.StockData

class HoldingSuggestion() : SearchSuggestion {


    private var name : String = "";
    private var code : String = "";

    private var data: StockData? = null
    private var amount: Int = 0

    constructor(parcel: Parcel) : this() {
        code = parcel.readString();
        name = parcel.readString()
    }

    constructor(code: String, name: String) : this() {
        this.code = code
        this.name = name
    }

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(code)
        dest?.writeString(name)
    }

    override fun describeContents(): Int {
        return name.hashCode()
    }

    override fun getBody(): String {
        return name
    }

    fun getCode(): String {
        return code
    }

    fun setData(data: StockData): HoldingSuggestion{
        this.data = data
        return this;
    }



    fun getPrice(): String {
        if(this.data != null){
            return "$" + this.data?.quote?.latestPrice.toString()
        }
        return ""
    }

    fun getAmount(): Int {
        return this.amount
    }

    fun getAmountText(): String {
        return "Owned: " + this.amount
    }

    fun getWorth(): String {
        if(this.data != null) {
            val amt = this.data?.quote?.latestPrice!! * this.amount
            return "Worth $$amt"
        }
        return ""
    }

    fun setAmount(amount: Int){
        this.amount = amount
    }
    
    companion object CREATOR : Parcelable.Creator<HoldingSuggestion> {
        override fun createFromParcel(parcel: Parcel): HoldingSuggestion {
            return HoldingSuggestion(parcel)
        }

        override fun newArray(size: Int): Array<HoldingSuggestion?> {
                        return arrayOfNulls(size)
        }
    }

    fun toStockDao(): Stock {
        var price: Float = 0.0f
        if(this.data != null) {
            price = this.data?.quote?.latestPrice!!// * this.amount
        }
        return Stock(this.code, this.name, this.amount, price)
    }

}