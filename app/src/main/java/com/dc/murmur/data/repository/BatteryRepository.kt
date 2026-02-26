package com.dc.murmur.data.repository

import com.dc.murmur.core.util.BatteryUtil
import com.dc.murmur.data.local.dao.BatteryLogDao
import com.dc.murmur.data.local.entity.BatteryLogEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class BatteryRepository(
    private val batteryLogDao: BatteryLogDao,
    private val batteryUtil: BatteryUtil
) {
    suspend fun logEvent(log: BatteryLogEntity) = batteryLogDao.insert(log)

    fun getTodayLogs(): Flow<List<BatteryLogEntity>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return batteryLogDao.getToday(startOfDay)
    }

    fun getSessionLogs(sessionId: String): Flow<List<BatteryLogEntity>> =
        batteryLogDao.getBySession(sessionId)

    suspend fun getAvgDrainPerHour(): Float {
        val since = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return batteryLogDao.getAvgDrainPerHour(since)
    }

    fun getCurrentLevel(): Int = batteryUtil.getBatteryLevel()
    fun isCharging(): Boolean = batteryUtil.isCharging()
}
