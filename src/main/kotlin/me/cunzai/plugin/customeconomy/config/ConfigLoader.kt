package me.cunzai.plugin.customeconomy.config

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common5.RandomList
import taboolib.library.xseries.getItemStack
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getStringColored
import taboolib.module.configuration.util.getStringListColored

object ConfigLoader {

    @Config(value = "config.yml")
    lateinit var config: Configuration

    @Config(value = "rewards.yml")
    lateinit var rewardsConfig: Configuration

    val knownEconomyType = HashSet<String>()
    val economyCleanCron = HashMap<String, Cron>()

    val rewards = HashMap<String, HashMap<Int, List<String>>>()
    val rewardsContents = HashMap<String, HashMap<Int, List<String>>>()
    val rewardsName = HashMap<String, HashMap<Int, String>>()

    val shopConfigs = HashMap<String, ShopConfig>()


    @Awake(LifeCycle.ENABLE)
    fun i() {
        knownEconomyType.clear()
        economyCleanCron.clear()
        rewards.clear()
        rewardsContents.clear()
        rewardsName.clear()

        val section = config.getConfigurationSection("economy")!!
        for (economyType in section.getKeys(false)) {
            knownEconomyType += economyType
            val cron = section.getString("${economyType}.clean") ?: continue
            val parse = CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)).parse(cron)
            economyCleanCron[economyType] = parse
        }

        for (key in economyCleanCron.keys) {
            val rewardsSection = rewardsConfig.getConfigurationSection(key) ?: continue
            rewardsSection.getKeys(false).mapNotNull { it.toIntOrNull() }
                .forEach {
                    rewards.getOrPut(key) {
                        HashMap()
                    }[it] = rewardsSection.getStringListColored("${it}.commands")
                    rewardsContents.getOrPut(key) {
                        HashMap()
                    }[it] = rewardsSection.getStringListColored("${it}.contents")
                    rewardsName.getOrPut(key) {
                        HashMap()
                    }[it] = rewardsSection.getStringColored("${it}.name") ?: "无"
                }
        }

        for (ecoType in knownEconomyType) {
            val shopSection = section.getConfigurationSection(ecoType)?.getConfigurationSection("shop") ?: continue

            val optionsSection = shopSection.getConfigurationSection("options")!!
            val refreshCron = optionsSection.getString("refresh_cron")!!
            val roundAmount = optionsSection.getInt("round_amount")
            val permissionRequired = optionsSection.getString("permission")
            val refreshLogic = optionsSection.getConfigurationSection("refresh")?.let {
                RefreshLogic(
                    CostType.valueOf(it.getString("cost")!!.uppercase()),
                    it.getInt("value"),
                    it.getInt("max_limit")
                )
            }

            val itemSection = shopSection.getConfigurationSection("items")!!
            val goods = itemSection.getKeys(false).map {
                val sec = itemSection.getConfigurationSection(it)!!
                val iconItem = sec.getItemStack("icon")!!
                val commands = sec.getStringList("commands")
                val weight = sec.getInt("weight")
                val price = sec.getInt("price")
                val buyLimit = sec.getInt("buy_limit", -1)
                GoodConfig(it, iconItem, commands, weight, price, buyLimit)
            }

            val shopConfig = ShopConfig(
                CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)).parse(refreshCron),
                roundAmount,
                refreshLogic,
                permissionRequired
            )

            shopConfig.goodsRandomList = RandomList(*goods.map { it to it.weight }.toTypedArray())
            shopConfig.goodsMap += goods.associateBy { it.internalName }

            shopConfigs[ecoType] = shopConfig
        }

        MySQLHandler.init()
    }



}