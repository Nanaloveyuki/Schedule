package com.miaom.schedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miaom.schedule.data.db.entity.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots ORDER BY startTime, endTime")
    fun observeAll(): Flow<List<TimeSlotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: TimeSlotEntity)

    @Query("DELETE FROM time_slots WHERE id = :slotId")
    suspend fun deleteById(slotId: String)
}

