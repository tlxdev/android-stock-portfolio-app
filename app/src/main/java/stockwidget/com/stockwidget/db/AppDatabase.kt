package stockwidget.com.stockwidget.db

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context


@Database(entities = arrayOf(Stock::class), version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): StockDao

    companion object {


        private var INSTANCE: AppDatabase? = null

        private val sLock = Any()


        fun getInstance(context: Context): AppDatabase {
        synchronized(sLock) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase::class.java, "stock-db"
                ).build()
            }
            return this.INSTANCE!!
        }
    }
    }


}