package com.example.test

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

object MineAccessManager {
    fun canAccessMine(ownerId: java.util.UUID, player: Player): Boolean {
        if (ownerId == player.uniqueId) return true
        if (MineManager.hasMinerRank(player)) return true
        return canAccessMine(ownerId, player.uniqueId)
    }

    fun trustPlayer(owner: Player, target: OfflinePlayer): String {
        if (target.uniqueId == owner.uniqueId) return "&cYou cannot trust yourself."
        if (!target.hasPlayedBefore() && !target.isOnline) return "&cThat player has never joined before."
        val data = DataStore.get(owner.uniqueId)
        val key = target.uniqueId.toString()
        if (key in data.trustedMinePlayers) return "&cThat player is already trusted."
        data.trustedMinePlayers += key
        target.player?.sendMessage(TextUtil.colorize("&a${owner.name} trusted you on their mine."))
        return "&aTrusted &f${target.name ?: "Unknown"}&a on your mine."
    }

    fun untrustPlayer(owner: Player, target: OfflinePlayer): String {
        val data = DataStore.get(owner.uniqueId)
        val removed = data.trustedMinePlayers.remove(target.uniqueId.toString())
        if (!removed) return "&cThat player is not trusted on your mine."
        target.player?.sendMessage(TextUtil.colorize("&e${owner.name} removed your trusted access to their mine."))
        return "&eRemoved trusted access for &f${target.name ?: "Unknown"}&e."
    }

    fun isTrusted(ownerId: java.util.UUID, playerId: java.util.UUID): Boolean {
        if (ownerId == playerId) return true
        return playerId.toString() in DataStore.get(ownerId).trustedMinePlayers
    }

    fun canAccessMine(ownerId: java.util.UUID, playerId: java.util.UUID): Boolean {
        if (ownerId == playerId) return true
        return isTrusted(ownerId, playerId)
    }

    fun getTrustedPlayers(ownerId: java.util.UUID): List<OfflinePlayer> =
        DataStore.get(ownerId).trustedMinePlayers.mapNotNull { raw ->
            val uuid = runCatching { java.util.UUID.fromString(raw) }.getOrNull() ?: return@mapNotNull null
            Bukkit.getOfflinePlayer(uuid)
        }.sortedBy { it.name ?: "" }
}
