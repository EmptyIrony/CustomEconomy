package me.cunzai.plugin.customeconomy

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.database.MySQLHandler
import me.cunzai.plugin.customeconomy.misc.getLastAndNextExecutionTime
import me.cunzai.plugin.customeconomy.ui.ShopUI
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
import taboolib.module.database.Order
import taboolib.module.database.Table
import taboolib.platform.util.sendLang
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max

@CommandHeader(name = "customshop", permissionDefault = PermissionDefault.TRUE)
object CustomEconomyCommands {

    @CommandBody(permissionDefault = PermissionDefault.TRUE)
    val shop = subCommand {
        dynamic("货币类型") {
            execute<Player> { sender, _, argument ->
                val config = ConfigLoader.shopConfigs[argument] ?: return@execute
                if (config.permissionRequired != null && !sender.hasPermission(config.permissionRequired)) {
                    sender.sendLang("no_permission")
                    return@execute
                }

                ShopUI.open(sender, argument)
            }
        }
    }

    @CommandBody
    val leader = subCommand {
        dynamic("货币类型") {
            execute<CommandSender> { sender, _, argument ->
                if (!ConfigLoader.knownEconomyType.contains(argument)) {
                    sender.sendMessage("&c未知货币类型".colored())
                    return@execute
                }

                sender.sendMessage("&7查询中...".colored())
                submitAsync {
                    sender.sendMessage("&7查询结果: 第1页\n".colored())
                    leaders(argument, 0).forEachIndexed { index, (name, amount) ->
                        sender.sendMessage("&7${index + 1}. &e$name &7- &a$amount\n".colored())
                    }
                    val sum = sum(argument)
                    sender.sendMessage("&7全服总和: &8$sum".colored())
                }
            }
            int("page") {
                execute<CommandSender> { sender, context, argument ->
                    if (!ConfigLoader.knownEconomyType.contains(context["货币类型"])) {
                        sender.sendMessage("&c未知货币类型".colored())
                        return@execute
                    }

                    sender.sendMessage("&7查询中...".colored())
                    submitAsync {
                        sender.sendMessage("&7查询结果: 第${argument}页\n".colored())
                        leaders(context["货币类型"], (argument.toInt() - 1) * 5).forEachIndexed { index, (name, amount) ->
                            sender.sendMessage("&7${index + 1 + (argument.toInt() - 1) * 5}. &e$name &7- &a$amount\n".colored())
                        }
                        val sum = sum(context["货币类型"])
                        sender.sendMessage("&7全服总和: &8$sum".colored())
                    }
                }
            }
        }
    }

    private fun sum(economyType: String): Int {
        val table = MySQLHandler.tables[economyType] ?: return 0
        val cron = ConfigLoader.economyCleanCron[economyType]

        return table.select(MySQLHandler.datasource) {
            rows("SUM(value) as sum")

            where {
                if (cron != null) {
                    val (last, _) = cron.getLastAndNextExecutionTime()
                    val lastTimestamp = last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    "last_refresh_at" gte lastTimestamp
                }
            }

            orderBy("value", Order.Type.DESC)
        }.firstOrNull {
            getInt("sum")
        } ?: 0
    }

    private fun leaders(economyType: String, skip: Int, ): List<Pair<String, Int>> {
        val table = MySQLHandler.tables[economyType] ?: return emptyList()
        val cron = ConfigLoader.economyCleanCron[economyType]

        return table.select(MySQLHandler.datasource) {
            where {
                if (cron != null) {
                    val (last, _) = cron.getLastAndNextExecutionTime()
                    val lastTimestamp = last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    "last_refresh_at" gte lastTimestamp
                }
            }

            offset(skip)
            limit(5)

            orderBy("value", Order.Type.DESC)
        }.map {
            getString("player_name") to getInt("value")
        }
    }

    @CommandBody(permission = "customshop.admin")
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

    @CommandBody(permission = "customshop.admin")
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

    @CommandBody(permission = "customshop.admin")
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

    @CommandBody(permission = "customshop.admin")
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

    @CommandBody(permission = "customshop.admin")
    val reload = subCommand {
        execute<CommandSender> { sender, _, _ ->
            ConfigLoader.config.reload()
            ConfigLoader.i()
            UI.config.reload()
            sender.sendMessage("ok")
        }
    }

    @CommandBody(permissionDefault = PermissionDefault.TRUE)
    val rewards = subCommand {
        dynamic("货币类型") {
            execute<Player> { sender, context, argument ->
                UI.open(sender, argument)
            }
        }
    }

    @CommandBody(permission = "customshop.admin")
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

    @CommandBody(permission = "customshop.admin")
    val main = mainCommand {
        createHelper(checkPermissions = true)
    }

}