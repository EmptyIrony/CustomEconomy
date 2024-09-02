package me.cunzai.plugin.customeconomy.hook

import me.cunzai.plugin.customeconomy.database.MySQLHandler
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.platform.compat.PlaceholderExpansion

object PAPIHook: PlaceholderExpansion {
    override val identifier: String
        get() = "customshop"
    override val autoReload: Boolean
        get() = true
    override val enabled: Boolean
        get() = true

    override fun onPlaceholderRequest(player: OfflinePlayer?, args: String): String {
        return player?.name?.let { name ->
            MySQLHandler.getValue(name, args)?.toString()
        } ?: "null"
    }

    override fun onPlaceholderRequest(player: Player?, args: String): String {
        return onPlaceholderRequest(player as? OfflinePlayer?, args)
    }
}