package me.cunzai.plugin.customeconomy.data

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.misc.getLastAndNextExecutionTime
import java.io.Serializable
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerShopData: Serializable {
    companion object {
        @JvmStatic
        val cache = ConcurrentHashMap<UUID, PlayerShopData>()
    }

    @Transient
    lateinit var name: String

    val goodMap = HashMap<String, GoodsInformation>()

    class GoodsInformation {

        var lastRefreshed = -1L
        val goods = ArrayList<GoodData>()
        var refreshed = 0

        fun shouldRefresh(name: String): Boolean {
            val shopConfig = ConfigLoader.shopConfigs[name] ?: return true
            val (last, _) = shopConfig.refresh.getLastAndNextExecutionTime()
            val lastTimestamp = last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (lastRefreshed <= lastTimestamp) {
                return true
            }

            return false
        }
    }

    data class GoodData(
        val name: String,
        var remaining: Int,
    )

}