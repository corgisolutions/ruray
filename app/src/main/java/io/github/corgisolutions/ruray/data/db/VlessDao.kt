package io.github.corgisolutions.ruray.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VlessDao {
    @Query("SELECT * FROM vless_hosts ORDER BY CASE WHEN latency > 0 THEN 0 ELSE 1 END, latency ASC")
    fun getAllHosts(): Flow<List<VlessHost>>
    
    @Query("SELECT * FROM vless_hosts ORDER BY CASE WHEN latency > 0 THEN 0 ELSE 1 END, latency ASC")
    suspend fun getAllHostsList(): List<VlessHost>

    @Query("SELECT * FROM vless_hosts WHERE isWorking = 1 AND link != :excludeLink ORDER BY latency ASC LIMIT 1")
    suspend fun getNextBestHost(excludeLink: String): VlessHost?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(host: VlessHost)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(hosts: List<VlessHost>)

    @Delete
    suspend fun delete(host: VlessHost)
    
    @Query("UPDATE vless_hosts SET isWorking = :isWorking, latency = :latency, lastChecked = :timestamp, failureCount = 0 WHERE link = :link")
    suspend fun updateSuccess(link: String, isWorking: Boolean, latency: Long, timestamp: Long)
    
    @Query("UPDATE vless_hosts SET failureCount = failureCount + 1, lastChecked = :timestamp WHERE link = :link")
    suspend fun updateFailure(link: String, timestamp: Long)
    
    @Query("DELETE FROM vless_hosts WHERE failureCount >= :threshold")
    suspend fun deleteFailed(threshold: Int)
    
    @Query("UPDATE vless_hosts SET countryCode = :countryCode WHERE link = :link")
    suspend fun updateCountry(link: String, countryCode: String)
}
