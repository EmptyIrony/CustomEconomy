package me.cunzai.plugin.customeconomy.ui

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.config.CostType
import me.cunzai.plugin.customeconomy.config.ShopConfig
import me.cunzai.plugin.customeconomy.data.PlayerShopData
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import me.cunzai.plugin.customeconomy.database.MySQLHandler.update
import org.black_ixx.playerpoints.PlayerPoints
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.common5.util.replace
import taboolib.library.xseries.getItemStack
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getStringColored
import taboolib.module.configuration.util.getStringListColored
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.compat.getBalance
import taboolib.platform.compat.withdrawBalance
import taboolib.platform.util.buildItem
import taboolib.platform.util.replaceLore
import taboolib.platform.util.sendLang

object ShopUI {

    @Config("shop.yml")
    lateinit var config: Configuration

    fun open(player: Player, ecoType: String) {
        val data = PlayerShopData.cache[player.uniqueId] ?: return
        val shopConfig = ConfigLoader.shopConfigs[ecoType] ?: return

        val goodInformation = data.goodMap.getOrPut(ecoType) {
            getNewGoodsInformation(shopConfig)
        }.let {
            if (it.shouldRefresh(ecoType)) {
                getNewGoodsInformation(shopConfig).apply {
                    this.refreshed = it.refreshed
                    data.goodMap[ecoType] = this
                }
            } else {
                it
            }
        }

        submitAsync {
            data.update()
        }

        player.openMenu<PageableChest<PlayerShopData.GoodData>>(title = config.getStringColored("title")!!.replace("{name}", ecoType)) {
            map(*config.getStringList("format").toTypedArray())
            elements {
                goodInformation.goods
            }
            slots(getSlots('!'))
            onGenerate { _, element, _, _ ->
                val goodConfig = shopConfig.goodsMap[element.name]!!
                buildItem(goodConfig.icon.clone()) {
                    lore += config.getStringListColored("good_lore_add")
                        .replace(
                            "{price}" to goodConfig.price,
                            "{currency}" to ecoType,
                            "{bought}" to goodConfig.buyLimit - element.remaining,
                            "{max_limit}" to goodConfig.buyLimit
                        )
                }
            }
            onClick { event, element ->
                event.isCancelled = true
                val goodConfig = shopConfig.goodsMap[element.name]!!
                if (element.remaining <= 0) {
                    player.sendLang("good_sold_out")
                    return@onClick
                }
                if ((MySQLHandler.getValue(player.name, ecoType) ?: 0) <= goodConfig.price) {
                    player.sendLang("no_money", ecoType)
                    return@onClick
                }

                element.remaining -= 1
                submitAsync {
                    data.update()
                }

                for (command in goodConfig.commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.name))
                }
                player.sendLang("buy_success")
                open(player, ecoType)
            }

            val refreshLogic = shopConfig.refreshLogic
            if (refreshLogic != null) {
                set('$', buildItem(config.getItemStack("refresh")!!).replaceLore(
                    mapOf(
                        "{cost}" to refreshLogic.value.toString(),
                        "{name}" to if (refreshLogic.cost == CostType.CUSTOM) {
                            ecoType
                        } else {
                            refreshLogic.cost.display
                        },
                        "{remaining}" to (refreshLogic.maxLimit - goodInformation.refreshed).toString()
                    )
                )) {
                    isCancelled = true
                    if (refreshLogic.maxLimit - goodInformation.refreshed <= 0) {
                        player.sendLang("refresh_limit")
                        return@set
                    }

                    val has = when (refreshLogic.cost) {
                        CostType.COINS -> {
                            player.getBalance().toInt()
                        }

                        CostType.POINTS -> {
                            PlayerPoints.getInstance().api.look(player.uniqueId)
                        }

                        CostType.CUSTOM -> {
                            MySQLHandler.getValue(player.name, ecoType) ?: 0
                        }
                    }

                    if (has < refreshLogic.value) {
                        player.sendLang("no_money", ecoType)
                        return@set
                    }

                    when (refreshLogic.cost) {
                        CostType.COINS -> {
                            player.withdrawBalance(refreshLogic.value.toDouble())
                        }

                        CostType.POINTS -> {
                            PlayerPoints.getInstance().api.take(player.uniqueId, refreshLogic.value)
                        }

                        CostType.CUSTOM -> {
                            MySQLHandler.setValue(player.name, ecoType, has - refreshLogic.value)
                        }
                    }
                    goodInformation.refreshed++
                    goodInformation.lastRefreshed = -1L
                    submitAsync {
                        data.update()
                    }

                    open(player, ecoType)

                    player.sendLang("refresh_success")
                }
            }

            set('#', config.getItemStack("placeholder")!!) {
                isCancelled = true
            }

            set('@', config.getItemStack("close")!!) {
                isCancelled = true
                player.closeInventory()
                submit(delay = 1L) {
                    for (command in config.getStringList("close.commands")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.name))
                    }
                }
            }

            setPreviousPage(getFirstSlot('<')) {_, _ -> config.getItemStack("previous")!!}

            setNextPage(getFirstSlot('>')) {_, _ -> config.getItemStack("next")!!}

        }
    }

    private fun getNewGoodsInformation(shopConfig: ShopConfig): PlayerShopData.GoodsInformation {
        return PlayerShopData.GoodsInformation().apply {
            repeat(shopConfig.roundAmount) {
                val random = shopConfig.goodsRandomList.random()!!
                this.goods += PlayerShopData.GoodData(random.internalName, random.buyLimit)
            }
            lastRefreshed = System.currentTimeMillis()
        }
    }

}