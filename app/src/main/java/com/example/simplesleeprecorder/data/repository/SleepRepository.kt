package com.example.simplesleeprecorder.data.repository

import com.example.simplesleeprecorder.data.local.SleepDao
import com.example.simplesleeprecorder.data.local.SleepSessionEntity
import com.example.simplesleeprecorder.data.local.SleepSessionWithStages
import com.example.simplesleeprecorder.data.local.SleepStageRecordEntity
import com.example.simplesleeprecorder.domain.model.SleepSession
import com.example.simplesleeprecorder.domain.model.SleepStageRecord
import com.example.simplesleeprecorder.domain.model.SleepStageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SleepRepository(private val dao: SleepDao) {

    val allSessions: Flow<List<SleepSession>> = dao.getAllSessionsWithStages().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun saveSession(session: SleepSession): Long {
        val entity = SleepSessionEntity(
            startTime = session.startTime,
            endTime = session.endTime,
            alarmTime = session.alarmTime,
            sleepOnsetTime = session.sleepOnsetTime,
            audioUri = session.audioUri,
        )
        val sessionId = dao.insertSession(entity)
        val stageEntities = session.stageRecords.map { record ->
            SleepStageRecordEntity(
                sessionId = sessionId,
                stageType = record.stageType.name,
                startTime = record.startTime,
                endTime = record.endTime,
            )
        }
        if (stageEntities.isNotEmpty()) dao.insertStageRecords(stageEntities)
        return sessionId
    }

    suspend fun getSessionById(id: Long): SleepSession? =
        dao.getSessionWithStagesById(id)?.toDomain()

    suspend fun deleteSession(session: SleepSession) {
        dao.deleteSession(
            SleepSessionEntity(
                id = session.id,
                startTime = session.startTime,
                endTime = session.endTime,
                alarmTime = session.alarmTime,
                sleepOnsetTime = session.sleepOnsetTime,
                audioUri = session.audioUri,
            )
        )
    }

    private fun SleepSessionWithStages.toDomain(): SleepSession {
        val records = stageRecords.map { it.toDomain(session.id) }
        return SleepSession(
            id = session.id,
            startTime = session.startTime,
            endTime = session.endTime,
            alarmTime = session.alarmTime,
            sleepOnsetTime = session.sleepOnsetTime,
            audioUri = session.audioUri,
            stageRecords = records,
        )
    }

    private fun SleepStageRecordEntity.toDomain(sessionId: Long) = SleepStageRecord(
        id = id,
        sessionId = sessionId,
        stageType = SleepStageType.valueOf(stageType),
        startTime = startTime,
        endTime = endTime,
    )
}
