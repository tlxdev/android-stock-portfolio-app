package stockwidget.com.stockwidget.db

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity
data class Stock(
    @PrimaryKey @ColumnInfo(name = "code") var code: String,
    @ColumnInfo(name = "name") var name: String,
    @ColumnInfo(name = "amount") var amount: Int,
    @ColumnInfo(name = "value") var value: Float
    )
