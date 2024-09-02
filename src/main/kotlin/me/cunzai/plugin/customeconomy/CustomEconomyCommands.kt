package me.cunzai.plugin.customeconomy

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import me.cunzai.plugin.customeconomy.ui.UI
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import taboolib.common.platform.command.*
import taboolib.common.platform.function.submitAsync
import taboolib.expansion.createHelper
import taboolib.module.chat.colored
import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.HostSQLite
import taboolib.module.database.Table
import java.io.File
import java.util.UUID
import kotlin.math.max

@CommandHeader(name = "customshop", permission = "customshop.admin")
object CustomEconomyCommands {

    @CommandBody
    val add = subCommand {
        dynamic("玩家") {
            dynamic("货币") {
                dynamic("数量") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context["玩家"]
                        val economyName = context["货币"]
                        val amount = argument.toIntOrNull() ?: run {
                            sender.sendMessage("&c必须是数字".colored())
                            return@execute
                        }

                        submitAsync {
                            val oldValue = MySQLHandler.getValue(playerName, economyName) ?: run {
                                sender.sendMessage("&c未注册的货币名: &e${economyName}".colored())
                                return@submitAsync
                            }

                            MySQLHandler.setValue(playerName, economyName, oldValue + amount)
                            sender.sendMessage("&a成功, $playerName 现在拥有 ${oldValue + amount} $economyName".colored())
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val set = subCommand {
        dynamic("玩家") {
            dynamic("货币") {
                dynamic("数量") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context["玩家"]
                        val economyName = context["货币"]
                        val amount = argument.toIntOrNull() ?: run {
                            sender.sendMessage("&c必须是数字".colored())
                            return@execute
                        }

                        submitAsync {
                            MySQLHandler.getValue(playerName, economyName) ?: run {
                                sender.sendMessage("&c未注册的货币名: &e${economyName}".colored())
                                return@submitAsync
                            }

                            MySQLHandler.setValue(playerName, economyName, amount)
                            sender.sendMessage("&a成功, $playerName 现在拥有 $amount $economyName".colored())
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val take = subCommand {
        dynamic("玩家") {
            dynamic("货币") {
                dynamic("数量") {
                    execute<CommandSender> { sender, context, argument ->
                        val playerName = context["玩家"]
                        val economyName = context["货币"]
                        val amount = argument.toIntOrNull() ?: run {
                            sender.sendMessage("&c必须是数字".colored())
                            return@execute
                        }

                        submitAsync {
                            val oldValue = MySQLHandler.getValue(playerName, economyName) ?: run {
                                sender.sendMessage("&c未注册的货币名: &e${economyName}".colored())
                                return@submitAsync
                            }

                            MySQLHandler.setValue(playerName, economyName, max(0, oldValue - amount))
                            sender.sendMessage("&a成功, $playerName 现在拥有 ${max(0, oldValue - amount)} $economyName".colored())
                        }
                    }
                }
            }
        }
    }

    @CommandBody
    val look = subCommand {
        dynamic("玩家") {
            dynamic("货币") {
                execute<CommandSender> { sender, context, argument ->
                    val playerName = context["玩家"]
                    val economyName = context["货币"]

                    submitAsync {
                        val oldValue = MySQLHandler.getValue(playerName, economyName) ?: run {
                            sender.sendMessage("&c未注册的货币名: &e${economyName}".colored())
                            return@submitAsync
                        }

                        sender.sendMessage("&a$playerName 现在拥有 $oldValue $economyName".colored())
                    }
                }
            }
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            ConfigLoader.config.reload()
            ConfigLoader.i()
            sender.sendMessage("ok")
        }
    }

    @CommandBody
    val rewards = subCommand {
        dynamic("货币类型") {
            execute<Player> { sender, context, argument ->
                UI.open(sender, argument)
            }
        }
    }

    @CommandBody
    val transfer = subCommand {
        dynamic("文件路径") {
            execute<CommandSender> { sender, _, argument ->
                val file = File(argument)
                if (!file.exists()) {
                    sender.sendMessage("&c文件不存在".colored())
                    return@execute
                }

                val hostSQLite = HostSQLite(file)
                val source = hostSQLite.createDataSource()
                for (economyType in ConfigLoader.knownEconomyType) {
                    sender.sendMessage("&8Transferring ${economyType}...".colored())
                    val table = Table(economyType, hostSQLite) {
                        add("player_id") {
                            type(ColumnTypeSQLite.TEXT)
                        }
                        add("amount") {
                            type(ColumnTypeSQLite.INTEGER)
                        }
                    }

                    table.workspace(source) {
                        select {}
                    }.forEach {
                        val name = Bukkit.getOfflinePlayer(UUID.fromString(getString("player_id"))).name ?: return@forEach
                        val amount = getInt("amount")
                        MySQLHandler.setValue(name, economyType, amount)
                    }
                    sender.sendMessage("&aTransferred $economyType".colored())
                }
            }
        }
    }

    @CommandBody
    val main = mainCommand {
        createHelper(checkPermissions = true)
    }

}