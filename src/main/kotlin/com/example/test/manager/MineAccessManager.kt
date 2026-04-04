package com.example.test

import org.bukkit.entity.Player
import java.util.UUID

object MineAccessManager {
    fun canAccessMine(ownerId: UUID, player: Player): Boolean {
        if (ownerId == player.uniqueId) return true
        if (MineManager.hasMinerRank(player)) return true
        return canAccessMine(ownerId, player.uniqueId)
    }

    fun canAccessMine(ownerId: UUID, playerId: UUID): Boolean {
        if (ownerId == playerId) return true
        val ownerClan = ClanManager.getClanFor(ownerId) ?: return false
        val playerClan = ClanManager.getClanFor(playerId) ?: return false
        return ownerClan.id == playerClan.id
    }
}
