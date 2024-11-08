package me.cunzai.plugin.customeconomy.config

import com.cronutils.model.Cron
import org.bukkit.inventory.ItemStack
import taboolib.common5.RandomList

class ShopConfig(
    val refresh: Cron,
    val roundAmount: Int,
    val refreshLogic: RefreshLogic?,
    val permissionRequired: String?
) {
    lateinit var goodsRandomList: RandomList<GoodConfig>
    val goodsMap = HashMap<String, GoodConfig>()
}

data class GoodConfig(
    val internalName: String,
    val icon: ItemStack,
    val commands: List<String>,
    val weight: Int,
    val price: Int,
    val buyLimit: Int = -1,
)

data class RefreshLogic(
    val cost: CostType,
    val value: Int,
    val maxLimit: Int
)

enum class CostType(val display: String) {
    COINS("金币"), POINTS("点券"), CUSTOM("你猜")
}
