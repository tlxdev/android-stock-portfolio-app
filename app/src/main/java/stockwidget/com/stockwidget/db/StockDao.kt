package stockwidget.com.stockwidget.db

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.*


@Dao
interface StockDao {
    @Query("SELECT * FROM stock")
    fun getAllLivedata(): LiveData<List<Stock>>

    @Query("SELECT * FROM stock")
    fun getAll(): List<Stock>

    @Query("SELECT * FROM stock WHERE code LIKE :code " +
            "LIMIT 1")
    fun findByNameLivedata(code: String): LiveData<Stock>

    @Query("SELECT * FROM stock WHERE code LIKE :code " +
            "LIMIT 1")
    fun findByName(code: String): Stock


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStock(order: Stock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllStocks(order: List<Stock>)

    @Query("DELETE FROM stock")
    fun deleteAll()

    @Delete
    fun delete(stock: Stock)
}