package com.miaom.schedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.miaom.schedule.data.db.entity.ReminderTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderTaskDao {
    @Query("SELECT * FROM reminder_tasks ORDER BY enabled DESC, minutesBefore ASC")
    fun observeAll(): Flow<List<ReminderTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ReminderTaskEntity)

    @Query("DELETE FROM reminder_tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: String)
}

