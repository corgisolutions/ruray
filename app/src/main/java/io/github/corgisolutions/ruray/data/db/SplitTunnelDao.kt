package io.github.corgisolutions.ruray.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitTunnelDao {
    @Query("SELECT * FROM split_tunnel_apps")
    fun getAll(): Flow<List<SplitTunnelApp>>

    @Query("SELECT packageName FROM split_tunnel_apps")
    suspend fun getAllList(): List<String>

    @Query("SELECT packageName FROM split_tunnel_apps WHERE isAutoAdded = 1")
    suspend fun getAutoAddedList(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(app: SplitTunnelApp)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<SplitTunnelApp>)

    @Delete
    suspend fun delete(app: SplitTunnelApp)

    @Query("DELETE FROM split_tunnel_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("SELECT isAutoAdded FROM split_tunnel_apps WHERE packageName = :packageName")
    suspend fun isAutoAdded(packageName: String): Boolean?
}