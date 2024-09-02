package me.cunzai.plugin.customeconomy.database

import me.cunzai.plugin.customeconomy.config.ConfigLoader
import me.cunzai.plugin.customeconomy.data.PlayerClaimData
import me.cunzai.plugin.customeconomy.misc.getLastAndNextExecutionTime
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.submitAsync
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration
import taboolib.module.database.*
import java.time.ZoneId

object MySQLHandler {
    @Config("database.yml")
    lateinit var config: Configuration

    private val host by lazy {
        config.getHost("mysql")
    }

    val datasource by lazy {
        host.createDataSource()
    }

    val tables = HashMap<String, Table<Host<SQL>, SQL>>()
    val claimTables = HashMap<String, Table<Host<SQL>, SQL>>()


    fun init() {
        tables.clear()
        for (economyName in ConfigLoader.knownEconomyType) {
            tables[economyName] = Table("economy_${economyName}", host) {
                add {
                    id()
                }

                add ("player_name"){
                    type(ColumnTypeSQL.VARCHAR, 64) {
                        options(ColumnOptionSQL.KEY)
                    }
                }

                add("value") {
                    type(ColumnTypeSQL.INT)
                }

                add("last_refresh_at") {
                    type(ColumnTypeSQL.BIGINT)
                }
            }.apply {
                workspace(datasource) {
                    createTable(checkExists = true)
                }.run()
            }

            claimTables[economyName] = Table("economy_claim_${economyName}", host) {
                add {
                    id()
                }

                add("player_name") {
                    type(ColumnTypeSQL.VARCHAR, 64) {
                        options(ColumnOptionSQL.KEY)
                    }
                }

                add("last_refresh_at") {
                    type(ColumnTypeSQL.BIGINT)
                }

                add("claim_data") {
                    type(ColumnTypeSQL.TEXT)
                }
            }.apply {
                workspace(datasource) {
                    createTable(checkExists = true)
                }.run()
            }
        }
    }

    fun getValue(name: String, economyName: String): Int? {
        val table = tables[economyName] ?: return null
        return table.workspace(datasource) {
            select {
                where {
                    ("player_name" eq name)
                }
            }
        }.firstOrNull {
            val cron = ConfigLoader.economyCleanCron[name]
            if (cron != null) {
                val refreshedAt = getLong("last_refresh_at")
                val lastCleanTime = cron.getLastAndNextExecutionTime().first
                val lastCleanTimestamp = lastCleanTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (lastCleanTimestamp > refreshedAt) {
                    table.workspace(datasource) {
                        update {
                            set("value", 0)
                            set("last_refresh_at", System.currentTimeMillis())
                            where {
                                "player_name" eq name
                            }
                        }
                    }.run()
                    return@firstOrNull 0
                }
            }

            return@firstOrNull getInt("value")
        } ?: run {
            table.workspace(datasource) {
                insert("player_name", "value", "last_refresh_at") {
                    value(name, 0, System.currentTimeMillis())
                }
            }.run()
            0
        }
    }

    fun setValue(name: String, economyName: String, value: Int) {
        val table = tables[economyName] ?: return
        // refresh first
        getValue(name, economyName)
        table.workspace(datasource) {
            update {
                set("value", value)
                set("last_refresh_at", System.currentTimeMillis())
                where {
                    "player_name" eq name
                }
            }
        }.run()
    }

    @SubscribeEvent
    fun e(e: PlayerJoinEvent) {
        val player = e.player

        submitAsync {
            for (economyType in ConfigLoader.economyCleanCron.keys) {
                val table = claimTables[economyType] ?: continue
                val data = table.workspace(datasource) {
                    select {
                        where {
                            "player_name" eq player.name
                        }
                    }
                }.firstOrNull {
                    val lastRefresh = getLong("last_refresh_at")
                    val claimData = getString("claim_data")

                    val data = PlayerClaimData(player.name, economyType)

                    val split = claimData?.split(";")
                    data.lastRefresh = lastRefresh
                    if (split?.isNotEmpty() == true) {
                        data.claimed += split.toSet()
                    }
                    data
                } ?: run {
                    table.workspace(datasource) {
                        insert("player_name", "last_refresh_at") {
                            value(player.name, System.currentTimeMillis())
                        }
                    }.run()

                    PlayerClaimData(player.name, economyType).apply {
                        lastRefresh = System.currentTimeMillis()
                    }
                }

                if (!player.isOnline) {
                    return@submitAsync
                }

                PlayerClaimData.cache.getOrPut(player.name) {
                    HashMap()
                }[economyType] = data
            }
        }
    }

    fun PlayerClaimData.save() {
        val table = claimTables[economyType] ?: return
        table.workspace(datasource) {
            update {
                set("last_refresh_at", System.currentTimeMillis())
                set("claim_data", claimed.joinToString(";"))
                where {
                    "player_name" eq playerName
                }
            }
        }.run()
    }

    @SubscribeEvent
    fun e(e: PlayerQuitEvent) {
        PlayerClaimData.cache.remove(e.player.name)
    }
}