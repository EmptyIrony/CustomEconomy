package me.cunzai.plugin.customeconomy.config

import com.cronutils.model.Cron
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
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

        MySQLHandler.init()
    }



}