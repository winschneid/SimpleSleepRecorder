package com.example.simplesleeprecorder.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SleepSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStageRecords(records: List<SleepStageRecordEntity>)

    @Query("UPDATE sleep_sessions SET endTime = :endTime, sleepOnsetTime = :sleepOnsetTime WHERE id = :sessionId")
    suspend fun updateSession(sessionId: Long, endTime: Long, sleepOnsetTime: Long?)

    @Query("DELETE FROM sleep_stage_records WHERE sessionId = :sessionId")
    suspend fun deleteStageRecordsBySession(sessionId: Long)

    @Query("DELETE FROM sleep_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Transaction
    suspend fun updateSessionWithStages(
        sessionId: Long,
        endTime: Long,
        sleepOnsetTime: Long?,
        stages: List<SleepStageRecordEntity>,
    ) {
        updateSession(sessionId, endTime, sleepOnsetTime)
        deleteStageRecordsBySession(sessionId)
        if (stages.isNotEmpty()) insertStageRecords(stages)
    }

    @Transaction
    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    fun getAllSessionsWithStages(): Flow<List<SleepSessionWithStages>>

    @Transaction
    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun getSessionWithStagesById(id: Long): SleepSessionWithStages?

    @Delete
    suspend fun deleteSession(session: SleepSessionEntity)
}
