package com.eaor.coffeefee.data

import androidx.room.*

@Dao
interface CoffeeShopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoffeeShop(coffeeShop: CoffeeShopEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoffeeShops(coffeeShops: List<CoffeeShopEntity>)

    @Query("SELECT * FROM CoffeeShops ORDER BY name ASC")
    suspend fun getAllCoffeeShops(): List<CoffeeShopEntity>

    @Query("SELECT * FROM CoffeeShops WHERE placeId = :placeId")
    suspend fun getCoffeeShopById(placeId: String): CoffeeShopEntity?

    @Query("SELECT * FROM CoffeeShops WHERE name LIKE '%' || :query || '%'")
    suspend fun searchCoffeeShops(query: String): List<CoffeeShopEntity>

    @Query("DELETE FROM CoffeeShops")
    suspend fun deleteAllCoffeeShops()

    @Query("DELETE FROM CoffeeShops WHERE placeId = :placeId")
    suspend fun deleteCoffeeShop(placeId: String)
} 