package me.cunzai.plugin.customeconomy.ui

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.data.PlayerClaimData
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import me.cunzai.plugin.customeconomy.database.MySQLHandler.save
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.submitAsync
import taboolib.common5.util.replace
import taboolib.expansion.submitChain
import taboolib.library.xseries.getItemStack
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getStringColored
import taboolib.module.ui.ItemStacker
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import taboolib.platform.util.replaceLore
import taboolib.platform.util.replaceName
import taboolib.platform.util.sendLang

object UI {

    @Config("ui.yml")
    lateinit var config: Configuration

    fun open(player: Player, economyType: String) {
        val rewards = ConfigLoader.rewards[economyType] ?: return
        val economyNames = ConfigLoader.rewardsName[economyType] ?: return
        val contents = ConfigLoader.rewardsContents[economyType] ?: return
        val data = PlayerClaimData.cache[player.name]?.get(economyType) ?: return

        val format = config.getStringList("format")
        val title = config.getStringColored("title") ?: return

        submitChain {
            val money = async {
                MySQLHandler.getValue(player.name, economyType)
            } ?: 0

            sync {
                data.doRefresh()

                player.openMenu<Chest>(title) {
                    rows(format.size)
                    map(*format.toTypedArray())
                    onClick { it.isCancelled = true }

                    var index = 0
                    rewards.toList().sortedBy { it.first }.forEach { (required, commands) ->
                        val slot = getSlots('#').getOrNull(index)
                        index++
                        if (slot == null) return@sync

                        if (data.claimed.contains(required.toString())) {
                            set(slot, config.getItemStack("claimed")!!.doReplace(
                                required, contents[required] ?: emptyList(), economyNames[required] ?: "无",
                            )) {
                                isCancelled = true
                                player.sendLang("claimed")
                            }
                        } else {
                            if (money >= required) {
                                set(
                                    slot, config.getItemStack("claim")!!.doReplace(
                                        required, contents[required] ?: emptyList(), economyNames[required] ?: "无",
                                    )
                                ) {
                                    isCancelled = true

                                    data.doRefresh()

                                    val success = data.claimed.add(required.toString())
                                    if (!success) {
                                        open(player, economyType)
                                        player.sendLang("claimed")
                                        return@set
                                    }

                                    submitAsync {
                                        data.save()
                                    }

                                    commands.replace(
                                        "%player%" to player.name
                                    ).forEach { command ->
                                        Bukkit.dispatchCommand(
                                            Bukkit.getConsoleSender(),
                                            command
                                        )
                                    }

                                    player.sendLang("claim_success")

                                    open(player, economyType)
                                }
                            } else {
                                set(slot, config.getItemStack("cant_claim")!!.doReplace(required, contents[required] ?: emptyList(), economyNames[required] ?: "无")) {
                                    isCancelled = true
                                }
                            }
                        }
                    }

                    set('!', config.getItemStack("close")!!) {
                        isCancelled = true
                        player.closeInventory()
                    }
                }
            }
        }
    }

    private fun ItemStack.doReplace(required: Int, contents: List<String>, name: String): ItemStack {
        val replaceMap = mapOf(
            "%required%" to required.toString(),
            "%name%" to name
        )

        return buildItem(this) {
            val indexOf = lore.indexOf("%contents%")
            if (indexOf == -1) return@buildItem
            lore.removeAt(indexOf)
            lore.addAll(indexOf, contents)
        }.replaceName(replaceMap)
            .replaceLore(replaceMap)
    }

}