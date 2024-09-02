package me.cunzai.plugin.customeconomy.data

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.database.MySQLHandler.save
import me.cunzai.plugin.customeconomy.misc.getLastAndNextExecutionTime
import taboolib.common.platform.function.submitAsync
import java.time.ZoneId

data class PlayerClaimData(
    val playerName: String,
    val economyType: String,
) {
    companion object {
        val cache = HashMap<String, HashMap<String, PlayerClaimData>>()
    }

    val claimed = HashSet<String>()
    var lastRefresh = -1L

    fun doRefresh() {
        val cron = ConfigLoader.economyCleanCron[economyType] ?: return
        val (last, next) = cron.getLastAndNextExecutionTime()
        val lastTimestamp = last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (lastRefresh < lastTimestamp) {
            lastRefresh = System.currentTimeMillis()
            claimed.clear()
            submitAsync {
                save()
            }
        }
    }
}
